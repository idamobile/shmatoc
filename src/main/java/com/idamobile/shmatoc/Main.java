package com.idamobile.shmatoc;

import com.idamobile.shmatoc.util.Strings;
import com.squareup.proto.ProtoFile;
import com.squareup.proto.ProtoSchemaParser;

import java.io.File;
import java.io.IOException;

public class Main {

    private static final String IN_PARAM = "--proto_files";
    private static final String PACKAGE_PARAM = "--package_out";
    private static final String IMPORTS_PARAM = "--add_java_imports";
    private static final String HELP_PARAM = "--help";

    public static void main(String[] args) throws IOException {
        String files = resolveProtoFiles(args);
        String packageOut = resolvePackage(args);
        String imports = resolveImports(args);
        if (resolveHelp(args) || Strings.isEmpty(files) || Strings.isEmpty(packageOut)) {
            printHelp();
            return;
        }

        File dir = createPackage(packageOut);

        String[] fileArr = files.split(";");
        String[] importsArr = imports == null ? new String[0] : imports.split(";");
        for (String fileName : fileArr) {
            if (!Strings.isEmpty(fileName)) {
                parseFile(new File(fileName), packageOut, importsArr, dir);
            }
        }
    }

    private static void parseFile(File file, String packageOut, String[] importsArr, File dir) throws IOException {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        parseFile(f, packageOut, importsArr, dir);
                    }
                }
            } else {
                try {
                    System.out.println("processing: " + file.getName());
                    ProtoFile protoFile = ProtoSchemaParser.parse(file);
                    ProtoToJavaConverter protoToJavaConverter = new ProtoToJavaConverter(
                            dir, packageOut, new ProtoToJavaConverter.DefaultNameCallback());
                    protoToJavaConverter.save(protoFile, importsArr);
                } catch (IllegalStateException ex) {
                    System.out.println(ex);
                }
            }
        }
    }

    private static File createPackage(String packageOut) {
        String resPath = packageOut.replaceAll("[.]", "/");
        File dir = new File(resPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("unable to create dir: " + resPath);
        }
        return dir;
    }

    private static void printHelp() {
        System.out.println("use: shmatoc " + PACKAGE_PARAM + " com.output.package " + IN_PARAM + " Some.proto Files.proto");
        System.out.println("Commands:");
        System.out.println("\t" + IN_PARAM + ": input protobuf files");
        System.out.println("\t" + PACKAGE_PARAM + ": output package");
        System.out.println("\t" + IMPORTS_PARAM + ": append this imports to generated java files");
        System.out.println("\t" + HELP_PARAM + ": prints this message");
    }

    private static String resolvePackage(String[] args) {
        return collectValues(args, PACKAGE_PARAM, null);
    }

    private static String resolveProtoFiles(String[] args) {
        return collectValues(args, IN_PARAM, ";");
    }

    private static String resolveImports(String[] args) {
        return collectValues(args, IMPORTS_PARAM, ";");
    }

    private static boolean resolveHelp(String[] args) {
        return hasParam(args, HELP_PARAM);
    }

    private static boolean hasParam(String[] args, String paramName) {
        for (String val : args) {
            if (paramName.equalsIgnoreCase(val)) {
                return true;
            }
        }
        return false;
    }

    private static String collectValues(String[] args, String paramName, String separator) {
        StringBuilder builder = new StringBuilder();
        boolean paramFound = false;
        for (String val : args) {
            if (val.startsWith("-")) {
                if (paramFound) {
                    break;
                } else if (paramName.equalsIgnoreCase(val)) {
                    paramFound = true;
                }
            } else if (paramFound) {
                if (builder.length() > 0 && !Strings.isEmpty(separator)) {
                    builder.append(separator);
                }
                builder.append(val);
            }
        }
        return builder.toString();
    }
}
