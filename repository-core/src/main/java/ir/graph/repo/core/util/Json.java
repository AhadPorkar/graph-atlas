package ir.graph.repo.core.util;

import java.math.BigDecimal;
import java.util.*;

/** Small, dependency-free JSON codec. Rejects duplicate keys and non-JSON numbers. */
public final class Json {
    private Json() {
    }
    public static Object parse(String text) {
        Reader r = new Reader(Objects.requireNonNull(text));
        Object value = r.value(0);
        r.space();
        if (r.at != text.length()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?>)) throw new IllegalArgumentException("Expected a JSON object");
        return (Map<String,Object>)value;
    }
    public static Map<String,Object> obj(String text) {
        return object(parse(text));
    }
    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        if (!(value instanceof List<?>)) throw new IllegalArgumentException("Expected a JSON array");
        return (List<Object>)value;
    }
    public static Map<String,Object> map(Object... pairs) {
        if ((pairs.length & 1) != 0) throw new IllegalArgumentException("Key/value pairs required");
        Map<String,Object> m = new LinkedHashMap<>();
        for (int i=0; i<pairs.length; i+=2) m.put((String)pairs[i], pairs[i+1]);
        return m;
    }
    public static String str(Map<String,Object> m,String key,String fallback) {
        Object v=m.get(key);
        return v == null ? fallback : Objects.toString(v);
    }
    public static long num(Map<String,Object> m,String key,long fallback) {
        Object v=m.get(key);
        return v == null ? fallback : new BigDecimal(v.toString()).longValueExact();
    }
    public static boolean bool(Map<String,Object> m,String key,boolean fallback) {
        Object v=m.get(key);
        if(v==null) return fallback;
        if(!(v instanceof Boolean)) throw new IllegalArgumentException("Expected boolean: "+key);
        return (Boolean)v;
    }
    public static String stringify(Object v) {
        StringBuilder b=new StringBuilder();
        write(b,v,0);
        return b.toString();
    }
    public static Object copy(Object value) {
        return parse(stringify(value));
    }
    private static void write(StringBuilder b,Object v,int depth) {
        if(depth>100) throw new IllegalArgumentException("JSON nesting is too deep");
        if(v==null) {
            b.append("null");
            return;
        }
        if(v instanceof String s) {
            b.append('"');
            for(int i=0; i<s.length(); i++) {
                char c=s.charAt(i);
                switch(c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\b' -> b.append("\\b");
                    case '\f' -> b.append("\\f");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    default -> {
                        if(c<32 || Character.isSurrogate(c)) b.append(String.format("\\u%04x",(int)c));
                        else b.append(c);
                    }
                }
            }
            b.append('"');
        } else if(v instanceof Boolean || v instanceof Number) {
            String s=v.toString();
            if(s.equals("NaN")||s.contains("Infinity")) throw new IllegalArgumentException("Non-finite number");
            b.append(s);
        } else if(v instanceof Map<?,?> m) {
            b.append('{');
            boolean first=true;
            for(var e:m.entrySet()) {
                if(!(e.getKey() instanceof String)) throw new IllegalArgumentException("JSON object key must be a string");
                if(!first) b.append(',');
                first=false;
                write(b,e.getKey(),depth+1);
                b.append(':');
                write(b,e.getValue(),depth+1);
            }
            b.append('}');
        } else if(v instanceof Iterable<?> list) {
            b.append('[');
            boolean first=true;
            for(Object item:list) {
                if(!first)b.append(',');
                first=false;
                write(b,item,depth+1);
            }
            b.append(']');
        } else throw new IllegalArgumentException("Unsupported JSON type: "+v.getClass());
    }
    private static final class Reader {
        final String text;
        int at;
        Reader(String text) {
            this.text=text;
        }
        void space() {
            while(at<text.length() && " \t\r\n".indexOf(text.charAt(at))>=0) at++;
        }
        IllegalArgumentException bad() {
            return new IllegalArgumentException("Invalid JSON at position "+at);
        }
        char next() {
            if(at==text.length())throw bad();
            return text.charAt(at++);
        }
        Object value(int depth) {
            if(depth>100) throw new IllegalArgumentException("JSON nesting is too deep");
            space();
            if(at==text.length())throw bad();
            char c=text.charAt(at);
            if(c=='"') return string();
            if(c=='{') {
                at++;
                Map<String,Object> m=new LinkedHashMap<>();
                space();
                if(at<text.length()&&text.charAt(at)=='}') {
                    at++;
                    return m;
                }
                while(true) {
                    space();
                    if(at==text.length()||text.charAt(at)!='"')throw bad();
                    String key=string();
                    space();
                    if(next()!=':')throw bad();
                    if(m.containsKey(key))throw bad();
                    m.put(key,value(depth+1));
                    space();
                    char sep=next();
                    if(sep=='}')return m;
                    if(sep!=',')throw bad();
                }
            }
            if(c=='[') {
                at++;
                List<Object> a=new ArrayList<>();
                space();
                if(at<text.length()&&text.charAt(at)==']') {
                    at++;
                    return a;
                }
                while(true) {
                    a.add(value(depth+1));
                    space();
                    char sep=next();
                    if(sep==']')return a;
                    if(sep!=',')throw bad();
                }
            }
            for(String s:List.of("true","false","null")) if(text.startsWith(s,at)) {
                at+=s.length();
                return s.equals("null")?null:Boolean.valueOf(s);
            }
            int start=at;
            if(c=='-')at++;
            if(at==text.length())throw bad();
            if(text.charAt(at)=='0')at++;
            else {
                if(text.charAt(at)<'1'||text.charAt(at)>'9')throw bad();
                while(at<text.length()&&digit(text.charAt(at)))at++;
            }
            if(at<text.length()&&text.charAt(at)=='.') {
                at++;
                int p=at;
                while(at<text.length()&&digit(text.charAt(at)))at++;
                if(p==at)throw bad();
            }
            if(at<text.length()&&(text.charAt(at)=='e'||text.charAt(at)=='E')) {
                at++;
                if(at<text.length()&&(text.charAt(at)=='+'||text.charAt(at)=='-'))at++;
                int p=at;
                while(at<text.length()&&digit(text.charAt(at)))at++;
                if(p==at)throw bad();
            }
            try {
                return new BigDecimal(text.substring(start,at));
            } catch(NumberFormatException e) {
                throw bad();
            }
        }
        boolean digit(char c) {
            return c>='0'&&c<='9';
        }
        String string() {
            if(next()!='"')throw bad();
            StringBuilder b=new StringBuilder();
            while(true) {
                char c=next();
                if(c=='"')return b.toString();
                if(c<32)throw bad();
                if(c!='\\') {
                    b.append(c);
                    continue;
                }
                char e=next();
                switch(e) {
                    case '"','\\','/' -> b.append(e);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if(at+4>text.length())throw bad();
                        try {
                            b.append((char)Integer.parseInt(text.substring(at,at+4),16));
                            at+=4;
                        } catch(NumberFormatException ex) {
                            throw bad();
                        }
                    }
                    default -> throw bad();
                }
            }
        }
    }
}
