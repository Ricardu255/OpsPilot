package org.example.common;

public final class StringUtils {

    private StringUtils() {
    }

    public static String truncate(String text, int maxLength) {
        return truncate(text, maxLength, "");
    }

    public static String truncate(String text, int maxLength, String suffix) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + suffix;
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
