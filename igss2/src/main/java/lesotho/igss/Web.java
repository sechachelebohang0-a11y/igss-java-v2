package lesotho.igss;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Minimal web plumbing: request, response, session and file uploads. */
final class Sess {
    final String kind, id; // kind: "C" citizen, "O" official
    Sess(String kind, String id) { this.kind = kind; this.id = id; }
}

final class Part { String filename, mime; byte[] data; }

final class Resp {
    int code = 200;
    String body = "", location, type = "text/html; charset=utf-8";
    byte[] bytes;
    Sess newSession;
    boolean logout;

    static Resp html(String b) { Resp r = new Resp(); r.body = b; return r; }
    static Resp redirect(String loc) { Resp r = new Resp(); r.code = 302; r.location = loc; return r; }

    /** Redirect with a message such as "ok:Saved" or "err:Not allowed". */
    static Resp to(String path, String msg) {
        return redirect(path + (path.contains("?") ? "&" : "?") + "m=" + URLEncoder.encode(msg, StandardCharsets.UTF_8));
    }

    static Resp error(int code, String text) {
        Resp r = html(Html.page(text, "Home", "/", Html.intro(text)));
        r.code = code;
        return r;
    }

    static Resp notFound() { return error(404, "That page does not exist."); }
}

final class Req {
    final String method, path;
    final Map<String, String> p = new HashMap<>();
    final Map<String, Part> files = new HashMap<>();
    final Sess sess;

    Req(String method, String path, Sess sess) { this.method = method; this.path = path; this.sess = sess; }

    boolean post() { return method.equals("POST"); }
    String get(String k) { String v = p.get(k); return v == null ? "" : v.trim(); }
    Models.Citizen citizen() { return sess != null && sess.kind.equals("C") ? Store.citizen(sess.id) : null; }
    Models.Official staff() { return sess != null && sess.kind.equals("O") ? Store.official(sess.id) : null; }

    static Map<String, String> parse(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String part : s.split("&")) {
            int i = part.indexOf('=');
            String k = i < 0 ? part : part.substring(0, i), v = i < 0 ? "" : part.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = from; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    /** Reads a multipart/form-data body (the format a browser uses to upload a file). */
    void parseMultipart(byte[] body, String boundary) {
        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] headEnd = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] next = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int pos = indexOf(body, delim, 0);
        while (pos >= 0) {
            int start = pos + delim.length;
            if (start + 2 > body.length || (body[start] == '-' && body[start + 1] == '-')) break;
            start += 2; // skip CRLF
            int he = indexOf(body, headEnd, start);
            if (he < 0) break;
            String head = new String(body, start, he - start, StandardCharsets.UTF_8);
            int dataStart = he + 4;
            int end = indexOf(body, next, dataStart);
            if (end < 0) break;
            String name = attr(head, "name"), filename = attr(head, "filename");
            byte[] data = java.util.Arrays.copyOfRange(body, dataStart, end);
            if (filename != null) {
                Part pt = new Part();
                pt.filename = filename; pt.data = data;
                for (String line : head.split("\r\n")) if (line.toLowerCase().startsWith("content-type:")) pt.mime = line.substring(13).trim();
                if (name != null && data.length > 0) files.put(name, pt);
            } else if (name != null) p.put(name, new String(data, StandardCharsets.UTF_8));
            pos = end + 2;
        }
    }

    private static String attr(String head, String key) {
        int i = head.indexOf(key + "=\"");
        if (i < 0) return null;
        int s = i + key.length() + 2, e = head.indexOf('"', s);
        return e < 0 ? null : head.substring(s, e);
    }
}
