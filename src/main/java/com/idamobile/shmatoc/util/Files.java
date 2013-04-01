package com.idamobile.shmatoc.util;

import java.io.File;

public class Files {

    public static String extractFileName(String path) {
        if (path == null) return null;
        return new File(path).getName();
    }

    public static String stripExtension(String str) {
        if (str == null) return null;
        int pos = str.lastIndexOf(".");
        if (pos == -1) return str;
        return str.substring(0, pos);
    }
}
