package com.movie_booking.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader/writer.
 *
 * <p>The brief for this project was "plain Java only", so Jackson and Gson are
 * off the table. This class covers exactly what the HTTP layer needs: turning
 * maps and lists into a response body, and turning a request body back into
 * maps and lists.
 *
 * <p>Writing supports {@code Map}, {@code Collection}, arrays, {@code Number},
 * {@code Boolean}, {@code null}, and the {@code java.time} / {@code BigDecimal}
 * types used by the domain model (rendered as ISO-8601 / plain decimal strings).
 *
 * <p>Reading produces {@code LinkedHashMap}, {@code ArrayList}, {@code String},
 * {@code Long}, {@code Double}, {@code Boolean} and {@code null}.
 */
public final class Json {

    private Json() {
        // Utility class.
    }

    // -----------------------------------------------------------------------
    // Writing
    // -----------------------------------------------------------------------

    public static String write(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(value, out);
        return out.toString();
    }

    /** Convenience factory so handlers can build a body inline. */
    public static Map<String, Object> object() {
        return new LinkedHashMap<String, Object>();
    }

    /** Builds a one-entry object, e.g. {@code Json.of("message", "Deleted")}. */
    public static Map<String, Object> of(String key, Object value) {
        Map<String, Object> map = object();
        map.put(key, value);
        return map;
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof BigDecimal) {
            out.append(((BigDecimal) value).toPlainString());
        } else if (value instanceof Number) {
            writeNumber((Number) value, out);
        } else if (value instanceof Map) {
            writeObject((Map<?, ?>) value, out);
        } else if (value instanceof Collection) {
            writeArray((Collection<?>) value, out);
        } else if (value instanceof Object[]) {
            writeArray(java.util.Arrays.asList((Object[]) value), out);
        } else if (value instanceof Enum) {
            writeString(((Enum<?>) value).name(), out);
        } else if (value instanceof LocalDate
                || value instanceof LocalTime
                || value instanceof LocalDateTime) {
            writeString(value.toString(), out);
        } else {
            writeString(value.toString(), out);
        }
    }

    private static void writeNumber(Number number, StringBuilder out) {
        double asDouble = number.doubleValue();
        if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
            // JSON has no literal for these; null is the conventional stand-in.
            out.append("null");
        } else {
            out.append(number.toString());
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(String.valueOf(entry.getKey()), out);
            out.append(':');
            writeValue(entry.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(Collection<?> collection, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (Object element : collection) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(element, out);
        }
        out.append(']');
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':  out.append("\\\"");  break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b");  break;
                case '\f': out.append("\\f");  break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    // -----------------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------------

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Unexpected trailing content at position " + parser.position);
        }
        return value;
    }

    /** Parses a request body that must be a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        if (text == null || text.trim().isEmpty()) {
            return object();
        }
        Object parsed = parse(text);
        if (!(parsed instanceof Map)) {
            throw new JsonException("Expected a JSON object but got "
                    + (parsed == null ? "null" : parsed.getClass().getSimpleName()));
        }
        return (Map<String, Object>) parsed;
    }

    // -----------------------------------------------------------------------
    // Typed accessors for parsed request bodies
    // -----------------------------------------------------------------------

    public static String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static String requireString(Map<String, Object> body, String key) {
        String value = string(body, key);
        if (value == null || value.trim().isEmpty()) {
            throw new JsonException("Field '" + key + "' is required.");
        }
        return value.trim();
    }

    public static int requireInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new JsonException("Field '" + key + "' is required.");
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new JsonException("Field '" + key + "' must be a whole number.");
        }
    }

    public static int optionalInt(Map<String, Object> body, String key, int defaultValue) {
        return body.get(key) == null ? defaultValue : requireInt(body, key);
    }

    public static BigDecimal requireDecimal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new JsonException("Field '" + key + "' is required.");
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new JsonException("Field '" + key + "' must be a decimal number.");
        }
    }

    /** Reads a JSON array of numbers, e.g. the {@code showSeatIds} of a booking. */
    public static List<Integer> requireIntList(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof Collection)) {
            throw new JsonException("Field '" + key + "' must be an array of numbers.");
        }

        List<Integer> numbers = new ArrayList<Integer>();
        for (Object element : (Collection<?>) value) {
            if (element instanceof Number) {
                numbers.add(Integer.valueOf(((Number) element).intValue()));
            } else {
                try {
                    numbers.add(Integer.valueOf(String.valueOf(element).trim()));
                } catch (NumberFormatException ex) {
                    throw new JsonException("Field '" + key + "' contains a non-numeric entry.");
                }
            }
        }
        return numbers;
    }

    /** Thrown for both malformed JSON and missing/ill-typed fields. */
    public static class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    // -----------------------------------------------------------------------
    // Recursive-descent parser
    // -----------------------------------------------------------------------

    private static final class Parser {

        private final String text;
        private int position;

        private Parser(String text) {
            this.text = text;
        }

        private boolean atEnd() {
            return position >= text.length();
        }

        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private char peek() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of JSON input.");
            }
            return text.charAt(position);
        }

        private void expect(char expected) {
            if (atEnd() || text.charAt(position) != expected) {
                throw new JsonException("Expected '" + expected + "' at position " + position);
            }
            position++;
        }

        private Object readValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{': return readObject();
                case '[': return readArray();
                case '"': return readString();
                case 't': return readLiteral("true", Boolean.TRUE);
                case 'f': return readLiteral("false", Boolean.FALSE);
                case 'n': return readLiteral("null", null);
                default:  return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            skipWhitespace();

            if (peek() == '}') {
                position++;
                return map;
            }

            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                map.put(key, readValue());
                skipWhitespace();

                char next = peek();
                if (next == ',') {
                    position++;
                } else if (next == '}') {
                    position++;
                    return map;
                } else {
                    throw new JsonException("Expected ',' or '}' at position " + position);
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<Object>();
            skipWhitespace();

            if (peek() == ']') {
                position++;
                return list;
            }

            while (true) {
                list.add(readValue());
                skipWhitespace();

                char next = peek();
                if (next == ',') {
                    position++;
                } else if (next == ']') {
                    position++;
                    return list;
                } else {
                    throw new JsonException("Expected ',' or ']' at position " + position);
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();

            while (true) {
                if (atEnd()) {
                    throw new JsonException("Unterminated string literal.");
                }
                char c = text.charAt(position++);

                if (c == '"') {
                    return builder.toString();
                }
                if (c != '\\') {
                    builder.append(c);
                    continue;
                }

                if (atEnd()) {
                    throw new JsonException("Unterminated escape sequence.");
                }
                char escape = text.charAt(position++);
                switch (escape) {
                    case '"':  builder.append('"');  break;
                    case '\\': builder.append('\\'); break;
                    case '/':  builder.append('/');  break;
                    case 'b':  builder.append('\b'); break;
                    case 'f':  builder.append('\f'); break;
                    case 'n':  builder.append('\n'); break;
                    case 'r':  builder.append('\r'); break;
                    case 't':  builder.append('\t'); break;
                    case 'u':
                        if (position + 4 > text.length()) {
                            throw new JsonException("Truncated \\u escape.");
                        }
                        String hex = text.substring(position, position + 4);
                        position += 4;
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException ex) {
                            throw new JsonException("Invalid \\u escape: " + hex);
                        }
                        break;
                    default:
                        throw new JsonException("Invalid escape character: \\" + escape);
                }
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, position)) {
                throw new JsonException("Invalid literal at position " + position);
            }
            position += literal.length();
            return value;
        }

        private Object readNumber() {
            int start = position;

            if (!atEnd() && (peek() == '-' || peek() == '+')) {
                position++;
            }

            boolean floating = false;
            while (!atEnd()) {
                char c = text.charAt(position);
                if (c >= '0' && c <= '9') {
                    position++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    floating = true;
                    position++;
                } else {
                    break;
                }
            }

            String literal = text.substring(start, position);
            if (literal.isEmpty()) {
                throw new JsonException("Expected a value at position " + start);
            }

            try {
                if (floating) {
                    return Double.valueOf(literal);
                }
                return Long.valueOf(literal);
            } catch (NumberFormatException ex) {
                throw new JsonException("Invalid number literal: " + literal);
            }
        }
    }
}
