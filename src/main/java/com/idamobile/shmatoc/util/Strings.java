package com.idamobile.shmatoc.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Strings {

    private static final Set<Character> SEPARATORS = new HashSet<Character>(Arrays.asList(new Character[]{
            '-', '_', '+', ' '
    }));

    public static String formatCamelCase(String srt) {
        if (srt == null) {
            return null;
        } else {
            StringBuilder builder = new StringBuilder(srt);
            for (int i = 0; i < builder.length(); i++) {
                Character ch = builder.charAt(i);
                if (SEPARATORS.contains(ch)) {
                    Character nextChar = i < builder.length() - 1 ? builder.charAt(i + 1) : null;
                    if (nextChar != null && !SEPARATORS.contains(ch)) {
                        upperCaseLetter(builder, i + 1);
                    }
                    builder.delete(i, i + 1);
                }
            }
            return builder.toString();
        }
    }

    public static String upperCaseLetter(String string, int charIndex) {
        StringBuilder builder = new StringBuilder(string);
        upperCaseLetter(builder, charIndex);
        return builder.toString();
    }

    public static void upperCaseLetter(StringBuilder builder, int charIndex) {
        builder.replace(charIndex, charIndex + 1, String.valueOf(builder.charAt(charIndex)).toUpperCase());
    }

    public static String lowerCaseLetter(String string, int charIndex) {
        StringBuilder builder = new StringBuilder(string);
        lowerCaseLetter(builder, charIndex);
        return builder.toString();
    }

    public static void lowerCaseLetter(StringBuilder builder, int charIndex) {
        builder.replace(charIndex, charIndex + 1, String.valueOf(builder.charAt(charIndex)).toLowerCase());
    }
}
