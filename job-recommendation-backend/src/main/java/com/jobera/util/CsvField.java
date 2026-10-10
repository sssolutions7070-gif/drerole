package com.jobera.util;

/** Minimal RFC 4180-ish CSV field escaping - quotes a field and doubles any embedded quotes
 *  whenever it contains a comma, quote, or newline; leaves everything else as-is. */
public final class CsvField {

    private CsvField() {
    }

    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
