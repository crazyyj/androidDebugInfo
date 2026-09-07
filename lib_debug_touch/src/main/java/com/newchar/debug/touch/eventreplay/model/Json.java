package com.newchar.debug.touch.eventreplay.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简、零依赖的 JSON 编解码工具。
 *
 * <p>仅支持 {@code Map / List / String / Number / Boolean / null} 这几种结构，
 * 足以描述事件序列与动作流。放在与 Android 无关的 model 包中，
 * 因此既可以在 Android 上运行，也能在普通 JVM 上被单元测试覆盖。</p>
 *
 * <p>之所以不依赖 {@code org.json}，是为了让“事件 JSON 化、新代码可处理”
 * 这一核心能力脱离 Android 环境也能被验证。</p>
 */
public final class Json {

    private Json() {
    }

    /** 将对象（Map/List/基本类型）编码为 JSON 字符串。 */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            writeString(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v) ? "true" : "false");
        } else if (v instanceof Number) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, e.getKey());
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object o : (List<Object>) v) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else if (v instanceof Object[]) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Object[]) v) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(v));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    /** 将 JSON 字符串解析为 Map/List/基本类型 嵌套结构。 */
    public static Object fromJson(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object v) {
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object v) {
        return (List<Object>) v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
            this.i = 0;
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw new IllegalStateException("Unexpected end of JSON");
            char c = s.charAt(i);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return parseNumber();
            }
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new HashMap<>();
            i++;
            skipWs();
            if (peek() == '}') { i++; return map; }
            while (true) {
                skipWs();
                if (peek() != '"') throw new IllegalStateException("Expected key string at " + i);
                String key = parseString();
                skipWs();
                if (peek() != ':') throw new IllegalStateException("Expected ':' at " + i);
                i++;
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
                throw new IllegalStateException("Expected ',' or '}' at " + i);
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++;
            skipWs();
            if (peek() == ']') { i++; return list; }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWs();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
                throw new IllegalStateException("Expected ',' or ']' at " + i);
            }
            return list;
        }

        String parseString() {
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalStateException("Unterminated string");
        }

        Object parseNumber() {
            int start = i;
            boolean isDouble = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '-' || c == '+' || (c >= '0' && c <= '9')) {
                    i++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    isDouble = true;
                    i++;
                } else {
                    break;
                }
            }
            String num = s.substring(start, i);
            if (isDouble) return Double.parseDouble(num);
            try {
                long l = Long.parseLong(num);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                return l;
            } catch (NumberFormatException e) {
                return Double.parseDouble(num);
            }
        }

        void expect(String lit) {
            if (!s.startsWith(lit, i)) throw new IllegalStateException("Expected " + lit);
            i += lit.length();
        }

        char peek() {
            if (i >= s.length()) throw new IllegalStateException("Unexpected end");
            return s.charAt(i);
        }
    }
}
