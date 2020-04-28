package com.algorand.sdkutils.generators;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;

public class QueryMapperGenerator extends Generator {

	public QueryMapperGenerator(JsonNode root) {
		super(root);
	}
	
	public void writeQueryMapper(String sdkutilsPath) throws IOException {
		BufferedWriter bw = getFileWriter("QueryMapper", sdkutilsPath);
		bw.append("package com.algorand.sdkutils.generated;\n" + 
				"\n" + 
				"import java.text.SimpleDateFormat;\n\n" +
				"import java.text.ParseException;\n" +
				"import com.algorand.algosdk.v2.client.model.Enums;\n" + 
				"import com.algorand.algosdk.v2.client.indexer.*;\n" + 
				"import com.algorand.algosdk.crypto.Address;\n" +
				"import com.algorand.algosdk.util.Encoder;\n" +
				"import java.security.NoSuchAlgorithmException;\n" + 
				"import com.algorand.algosdk.v2.client.common.Client;\n" + 
				"import com.algorand.algosdk.v2.client.common.Settings;\n" + 
				"import com.algorand.algosdk.v2.client.common.Query;\n" +
				"import com.algorand.sdkutils.generators.Generator;\n\n"
				+ "public class QueryMapper {\n" + 
				"\n");
		
		StringBuffer getClass = new StringBuffer();
		StringBuffer setValue = new StringBuffer();
		StringBuffer lookUp = new StringBuffer();
		StringBuffer enumMappers = new StringBuffer();
		
		getClass.append("	public static Query getClass(String name, Client client, String args[]) {\n" + 
				"		switch (name) {\n");
		setValue.append("	public static void setValue(Query q, String className, String property, String value) throws ParseException, NoSuchAlgorithmException {\n" + 
				"		switch (className) {\n");
		lookUp.append("	public static String lookup(Query q, String className) throws Exception {\n" + 
				"		switch (className) {\n");
		
		JsonNode paths = this.root.get("paths");
		Iterator<Entry<String, JsonNode>> pathIter = paths.fields();
		while (pathIter.hasNext()) {
			Entry<String, JsonNode> path = pathIter.next();
			String className = path.getValue().findValue("operationId").asText();
			String javaClassName = Generator.getCamelCase(className, true);
			String methodName = Generator.getCamelCase(className, false);
			
			// getClass
			getClass.append("		case \""+className+"\":\n" + 
					"			return client."+methodName+"(");
			
			//setValue
			setValue.append("		case \""+className+"\":\n" + 
					"			switch (property) {\n");
			
			//lookUp
			lookUp.append("		case \""+className+"\":\n" + 
					"			return (("+javaClassName+")q).execute().body().toString();\n");
			
			JsonNode paramNode = path.getValue().findValue("parameters");
			Iterator<Entry<String, JsonNode>> properties = getSortedParameters(paramNode);
			
			// The parameters in the path are directly passed to the constructor.
			// The method with have in order arguments each assigned to the parameter in order. 
			int argCounter = 0;
			
			while (properties.hasNext()) {
				Entry<String, JsonNode> parameter = properties.next();
				String javaSetParamName = Generator.getCamelCase(parameter.getKey(), false);
				String typeName = parameter.getValue().get("type").asText();
				Iterator<JsonNode> enumVals = parameter.getValue().get("enum") == null ? null : 
															parameter.getValue().get("enum").elements();
				String javaEnumName = Generator.getCamelCase(parameter.getKey(), true);
				String format = Generator.getTypeFormat(parameter.getValue());
				
				if (inPath(parameter.getValue())) {
					if (argCounter > 0) {
						getClass.append(", ");
					}
					switch (typeName) {
					case "integer":
						getClass.append("Long.valueOf("+"args[" + argCounter + "])");
						break;
					case "string":
						getClass.append("args[" + argCounter + "]");
						break;
					case "boolean":
						getClass.append("Boolean.valueOf("+"args[" + argCounter + "])");
						break;
					default:
						throw new RuntimeException("Unknow type: " + typeName);
					}

					argCounter++;
					continue;
				}
				
				setValue.append("			case \""+parameter.getKey()+"\":\n" + 
						"				(("+javaClassName+")q)."+javaSetParamName+"(");
				switch (typeName) {
				case "integer":
					setValue.append("Long.valueOf(value));\n");
					break;
				case "string":
					switch (format) {
					case "RFC3339 String":
						setValue.append("new SimpleDateFormat(Settings.DateFormat).parse(value));\n");
						break;
					case "Address":
						setValue.append("new Address(value));\n");
						break;
					case "byte":
						setValue.append("value);\n");
						break;
					default:
						if (enumVals != null) {
							setValue.append("get" + javaEnumName + "(value));\n");
						} else {
							setValue.append("value);\n");
						}						
					}
					break;
				case "boolean":
					setValue.append("Boolean.valueOf(value));\n");
					break;
				}
				setValue.append("				break;\n");
			}
			getClass.append(");\n");			
			setValue.append("			}\n			break;\n");
		}

		getClass.append("		}\n" + 
				"		return null;\n" + 
				"	}\n\n");
		setValue.append("\n" + 
				"		}\n" + 
				"	}\n\n");
		lookUp.append("		}\n" + 
				"		return null;\n" + 
				"	}\n");
		
		generateEnumMapper(root, enumMappers);
		bw.append(getClass);
		bw.append(setValue);
		bw.append(lookUp);
		bw.append(enumMappers);
		bw.append("}");
		bw.close();
	}

	private void generateEnumMapper (JsonNode root, StringBuffer enumMappers) throws IOException {

		JsonNode parameters = root.get("parameters");
		Iterator<Entry<String, JsonNode>> classes = parameters.fields();
		while (classes.hasNext()) {
			Entry<String, JsonNode> cls = classes.next();
			if (cls.getValue().get("enum") != null) {
				String enumName = Generator.getCamelCase(cls.getKey(), true);
				TypeDef enumType = getEnum(cls.getValue(), cls.getKey(), true);
				enumMappers.append("\tprivate static " + enumType.typeName + " get" + enumName + "(String val) {\n");
				enumMappers.append("\t\tswitch(val.toUpperCase()) {\n");			
				JsonNode enumNode = cls.getValue().get("enum");
				Iterator<JsonNode> elmts = enumNode.elements();
				while(elmts.hasNext()) {
					String val = elmts.next().asText();
					String javaEnum = getCamelCase(val, true).toUpperCase();
					enumMappers.append("\t\tcase \"" + javaEnum + "\":\n");
					enumMappers.append("\t\t\treturn " + enumType.typeName + "." + javaEnum + ";\n");
				}
				enumMappers.append("\t\tdefault:\n\t\t\tthrow new RuntimeException(\"Enum value not recognized: \" + val +\"!\");\n");
				enumMappers.append("\t\t}\n\t}\n");
			}
		}
	}

}
