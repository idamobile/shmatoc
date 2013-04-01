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

    public void save(ProtoFile protoFile) {
        this.protoFile = protoFile;
        this.protoClassName = extractClassName(protoFile);

        for (Type type : protoFile.getTypes()) {
            if (type instanceof MessageType) {
                saveMessage((MessageType) type);
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

    private void saveMessage(MessageType message) {
        Map<String, String> enumNames = new HashMap<String, String>();
        for (Type type : message.getNestedTypes()) {
            if (type instanceof EnumType) {
                enumNames.put(type.getName(), saveEnum((EnumType) type, message));
            }
        }

        StringBuilder builder = saveType(message);
        builder.append("@Data\n");
        builder.append("@Mapper(protoClass = ")
                .append(protoClassName)
                .append(".")
                .append(message.getName())
                .append(".class)\n");
        String className = nameCallback.getName(message.getName());
        builder.append("public class ").append(className).append(" implements Serializable {\n");
        for (MessageType.Field field : message.getFields()) {
            builder.append("    @Field");
            if (isOptional(field)) {
                builder.append("(optional = true)");
            }
            builder.append(" private ");
            String mappedTypeName = enumNames.containsKey(field.getType()) ? enumNames.get(field.getType()) : getMappedTypeName(field);
            if (isRepeated(field)) {
                builder.append("List<").append(mappedTypeName).append(">");
            } else {
                builder.append(mappedTypeName);
            }
            builder.append(" ").append(field.getName());
            if (isRepeated(field)) {
                builder.append(" = new ArrayList<").append(mappedTypeName).append(">()");
            }
            builder.append(";\n");
        }
        builder.append("}");

        saveJavaFile(builder.toString(), className);
    }

    private String saveEnum(EnumType enumType, MessageType message) {
        StringBuilder builder = saveType(enumType);
        builder.append("@Mapper(protoClass = ")
                .append(protoFile.getJavaPackage())
                .append(".")
                .append(protoClassName)
                .append(".");
        if (message != null) {
            builder.append(message.getName()).append(".");
        }
        builder.append(enumType.getName())
                .append(".class, isEnum = true)\n");

        String enumName = nameCallback.getName("" + (message == null ? "" : message.getName()) + enumType.getName());
        builder.append("public enum ").append(enumName).append(" {\n");
        for (EnumType.Value value : enumType.getValues()) {
            builder.append("    ").append(value.getName()).append("(").append(value.getTag()).append("),\n");
        }
        builder.replace(builder.length() - 2, builder.length(), ";\n");
        builder.append("\n");
        builder.append("    @Field public final int code;\n\n");
        builder.append("    private ").append(enumName).append("(int code) {\n");
        builder.append("        this.code = code;\n");
        builder.append("    }\n");
        builder.append("}");

        saveJavaFile(builder.toString(), enumName);

        return enumName;
    }

    private StringBuilder saveType(Type type) {
        StringBuilder builder = new StringBuilder();
        setPackage(builder);
        setImports(type, builder);
        return builder;
    }

    private void setPackage(StringBuilder builder) {
        builder.append("package " + packageName + ";\n\n");
    }

    private void setImports(Type type, StringBuilder builder) {
        Set<String> classNames = new TreeSet<String>();

        classNames.add("com.shaubert.protomapper.annotations.Field");
        classNames.add("com.shaubert.protomapper.annotations.Mapper");
        classNames.add("lombok.Data");
        classNames.add(protoFile.getJavaPackage() + "." + protoClassName);
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
                result = "bool";
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
        String getName(String name);
        String getType(String type);
    }

    public static class DefaultNameCallback implements NameCallback {
        @Override
        public String getName(String name) {
            return name.replaceAll("(ProtobufDTO)|(DTO)", "");
        }

        @Override
        public String getType(String type) {
            return type.replaceAll("(ProtobufDTO)|(DTO)", "");
        }
    }
}
