package com.idamobile.shmatoc;

import com.idamobile.shmatoc.util.Files;
import com.idamobile.shmatoc.util.Strings;
import com.squareup.proto.EnumType;
import com.squareup.proto.MessageType;
import com.squareup.proto.ProtoFile;
import com.squareup.proto.Type;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ProtoToJavaConverter {

    private final File outputDir;
    private final String packageName;
    private final NameCallback nameCallback;

    private ProtoFile protoFile;
    private String protoClassName;

    public ProtoToJavaConverter(File outputDir, String packageName, NameCallback nameCallback) {
        this.outputDir = outputDir;
        this.packageName = packageName;
        this.nameCallback = nameCallback;
    }

    public void save(ProtoFile protoFile, String[] importsArr) {
        this.protoFile = protoFile;
        this.protoClassName = extractClassName(protoFile);

        for (Type type : protoFile.getTypes()) {
            if (type instanceof MessageType) {
                saveMessage((MessageType) type, importsArr);
            } else if (type instanceof EnumType) {
                saveEnum((EnumType) type, null);
            }
        }
    }

    private String extractClassName(ProtoFile protoFile) {
        Object outerClassname = protoFile.getOptions().get("java_outer_classname");
        if (outerClassname != null) {
            return (String) outerClassname;
        } else {
            String fName = Files.stripExtension(Files.extractFileName(protoFile.getFileName()));
            return Strings.formatCamelCase(fName);
        }
    }

    private void saveMessage(MessageType message, String[] importsArr) {
        Map<String, String> enumNames = new HashMap<String, String>();
        for (Type type : message.getNestedTypes()) {
            if (type instanceof EnumType) {
                enumNames.put(type.getName(), saveEnum((EnumType) type, message));
            }
        }

        StringBuilder builder = saveType(message, importsArr);
        builder.append("@Data\n");
        builder.append("@Mapper(protoClass = ")
                .append(protoClassName)
                .append(".")
                .append(message.getName())
                .append(".class)\n");
        String className = nameCallback.getClassName(message.getName());
        builder.append("public class ").append(className).append(" implements Serializable {\n");
        for (MessageType.Field field : message.getFields()) {
            String fieldName = nameCallback.getFieldName(field.getName(), field.getType());
            String fieldAnnotations = buildFieldAnnotation(isOptional(field), fieldName, field.getName());
            builder.append("    private ").append(fieldAnnotations).append(' ');
            String mappedTypeName = enumNames.containsKey(field.getType()) ? enumNames.get(field.getType()) : getMappedTypeName(field);
            if (isRepeated(field)) {
                builder.append("List<").append(mappedTypeName).append(">");
            } else {
                builder.append(mappedTypeName);
            }
            builder.append(" ").append(fieldName);
            if (isRepeated(field)) {
                builder.append(" = new ArrayList<").append(mappedTypeName).append(">()");
            }
            builder.append(";\n");
        }
        builder.append("}");

        saveJavaFile(builder.toString(), className);
    }

    private String buildFieldAnnotation(boolean optional, String fieldName, String protoFieldName) {
        StringBuilder builder = new StringBuilder("@Field");
        boolean diffNames = !fieldName.equals(protoFieldName);
        if (optional || diffNames) {
            builder.append("(");
            if (diffNames) {
                builder.append("name = \"" + protoFieldName + "\", ");
            }
            if (optional) {
                builder.append("optional = true, ");
            }
            builder.replace(builder.length() - 2, builder.length(), ")");
        }
        return builder.toString();
    }

    private String saveEnum(EnumType enumType, MessageType message) {
        StringBuilder builder = saveType(enumType, null);
        builder.append("@Mapper(protoClass = ")
                .append(protoClassName)
                .append(".");
        if (message != null) {
            builder.append(message.getName()).append(".");
        }
        builder.append(enumType.getName())
                .append(".class, isEnum = true)\n");

        String enumName = nameCallback.getClassName("" + (message == null ? "" : message.getName()) + enumType.getName());
        builder.append("public enum ").append(enumName).append(" {\n");
        for (EnumType.Value value : enumType.getValues()) {
            builder.append("    ").append(value.getName()).append("(").append(value.getTag()).append("),\n");
        }
        builder.replace(builder.length() - 2, builder.length(), ";\n");
        builder.append("\n");
        builder.append("    public final @Field int code;\n\n");
        builder.append("    private ").append(enumName).append("(int code) {\n");
        builder.append("        this.code = code;\n");
        builder.append("    }\n");
        builder.append("}");

        saveJavaFile(builder.toString(), enumName);

        return enumName;
    }

    private StringBuilder saveType(Type type, String[] importsArr) {
        StringBuilder builder = new StringBuilder();
        setPackage(builder);
        setImports(type, builder, importsArr);
        return builder;
    }

    private void setPackage(StringBuilder builder) {
        builder.append("package " + packageName + ";\n\n");
    }

    private void setImports(Type type, StringBuilder builder, String[] importsArr) {
        Set<String> classNames = new TreeSet<String>();

        classNames.add("com.shaubert.protomapper.annotations.Field");
        classNames.add("com.shaubert.protomapper.annotations.Mapper");
        if (!(type instanceof EnumType)) {
            classNames.add("lombok.Data");
        }
        classNames.add(protoFile.getJavaPackage() + "." + protoClassName);
        if (importsArr != null) {
            for (String importEntry : importsArr) {
                if (!Strings.isEmpty(importEntry)) {
                    classNames.add(importEntry);
                }
            }
        }
        appendImports(builder, classNames);

        if (type instanceof MessageType) {
            classNames.clear();
            MessageType messageType = (MessageType) type;
            for (MessageType.Field field : messageType.getFields()) {
                if (isRepeated(field)) {
                    classNames.add("java.util.ArrayList");
                    classNames.add("java.util.List");
                    break;
                }
            }

            classNames.add("java.io.Serializable");
            appendImports(builder, classNames);
        }

        builder.append("\n");
    }

    private void appendImports(StringBuilder builder, Set<String> classNames) {
        for (String className : classNames) {
            builder.append("import ").append(className).append(";\n");
        }
        builder.append("\n");
    }

    private String getMappedTypeName(MessageType.Field field) {
        String t = field.getType();
        boolean optional = isOptional(field);
        boolean repeated = isRepeated(field);
        String result;
        if (t.equals("int32")) {
            if (optional || repeated) {
                result = "Integer";
            } else {
                result = "int";
            }
        } else if (t.equals("int64")) {
            if (optional || repeated) {
                result = "Long";
            } else {
                result = "long";
            }
        } else if (t.equals("float")) {
            if (optional || repeated) {
                result = "Float";
            } else {
                result = "float";
            }
        } else if (t.equals("double")) {
            if (optional || repeated) {
                result = "Double";
            } else {
                result = "double";
            }
        } else if (t.equals("bool")) {
            if (optional || repeated) {
                result = "Boolean";
            } else {
                result = "boolean";
            }
        } else if (t.equals("string")) {
            result = "String";
        } else if (t.equals("bytes")) {
            result = "byte[]";
        } else {
            result = nameCallback.getType(t);
        }
        return result;
    }

    private boolean isOptional(MessageType.Field field) {
        return field.getLabel() == MessageType.Label.OPTIONAL;
    }

    private boolean isRequired(MessageType.Field field) {
        return field.getLabel() == MessageType.Label.REQUIRED;
    }

    private boolean isRepeated(MessageType.Field field) {
        return field.getLabel() == MessageType.Label.REPEATED;
    }

    private void saveJavaFile(String text, String className) {
        File outFile = new File(outputDir, className + ".java");
        if (outFile.exists()) {
            outFile.delete();
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile)));
            writer.write(text);
        } catch (IOException  e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public interface NameCallback {
        String getClassName(String name);
        String getFieldName(String name, String protoType);
        String getType(String type);
    }

    public static class DefaultNameCallback implements NameCallback {
        @Override
        public String getClassName(String name) {
            return name.replaceAll("(ProtobufDTO)|(DTO)", "");
        }

        @Override
        public String getFieldName(String name, String protoType) {
            if ("bool".equals(protoType)) {
                int isIndex = name.indexOf("is");
                if (isIndex == 0 && name.length() > 2) {
                    if (Character.isUpperCase(name.charAt(2))) {
                        return Strings.lowerCaseLetter(name.substring(2), 0);
                    }
                }
            }
            return name;
        }

        @Override
        public String getType(String type) {
            return type.replaceAll("(ProtobufDTO)|(DTO)", "");
        }
    }
}
