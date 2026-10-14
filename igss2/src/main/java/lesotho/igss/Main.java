package lesotho.igss;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** Integrated Government Services System - Lesotho. Run this class, then open http://localhost:8080 */
public final class Main {
    private static final Map<String, Sess> SESSIONS = new ConcurrentHashMap<>();
    static final int MAX_BODY = 6 * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String db = args.length > 1 ? args[1] : "igss.db";
        Store.init(db);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("IGSS is running with database " + db + ". Open http://localhost:" + port);
    }

    private static void handle(HttpExchange ex) throws IOException {
        byte[] out;
        int code;
        String type = "text/html; charset=utf-8";
        try {
            String sid = cookie(ex, "sid");
            Req r = new Req(ex.getRequestMethod(), ex.getRequestURI().getPath(), sid == null ? null : SESSIONS.get(sid));
            r.p.putAll(Req.parse(ex.getRequestURI().getRawQuery()));
            Resp resp;
            if (r.post()) {
                byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) {
                    resp = Resp.error(413, "That file is too big. The limit is 5 MB.");
                    body = null;
                } else {
                    String ct = ex.getRequestHeaders().getFirst("Content-Type");
                    if (ct != null && ct.startsWith("multipart/form-data") && ct.contains("boundary=")) r.parseMultipart(body, ct.substring(ct.indexOf("boundary=") + 9).replace("\"", ""));
                    else r.p.putAll(Req.parse(new String(body, StandardCharsets.UTF_8)));
                    synchronized (Store.LOCK) { resp = route(r); }
                }
            } else {
                synchronized (Store.LOCK) { resp = route(r); }
            }
            if (resp.logout && sid != null) {
                SESSIONS.remove(sid);
                ex.getResponseHeaders().add("Set-Cookie", "sid=; Path=/; Max-Age=0");
            }
            if (resp.newSession != null) {
                if (sid != null) SESSIONS.remove(sid); // a new login always replaces the old session
                String id = UUID.randomUUID().toString();
                SESSIONS.put(id, resp.newSession);
                ex.getResponseHeaders().add("Set-Cookie", "sid=" + id + "; Path=/; HttpOnly; SameSite=Lax");
            }
            code = resp.code;
            type = resp.type;
            if (resp.location != null) ex.getResponseHeaders().add("Location", resp.location);
            out = resp.bytes != null ? resp.bytes : resp.body.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            code = 500;
            out = Html.page("Something went wrong", "Home", "/", Html.intro("Please go back and try again.")).getBytes(StandardCharsets.UTF_8);
        }
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (type.startsWith("text/html")) { // pages are never kept by the browser, so Back cannot show them after logout
            ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            ex.getResponseHeaders().set("Pragma", "no-cache");
        }
        ex.sendResponseHeaders(code, code == 302 ? -1 : out.length);
        if (code != 302) ex.getResponseBody().write(out);
        ex.close();
    }

    private static String cookie(HttpExchange ex, String name) {
        String h = ex.getRequestHeaders().getFirst("Cookie");
        if (h == null) return null;
        for (String part : h.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    /** One line per page. Landing screens are in LandingPage, citizen pages in CitizenPages, department-staff pages in StaffPages. */
    private static Resp route(Req r) {
        switch (r.path) {
            // Entry screens: Welcome, Login / Sign Up (role), Already have an account?
            case "/": return LandingPage.welcome(r);
            case "/start": return LandingPage.role(r);
            case "/start/account": return LandingPage.account(r);
            case "/logo.png": return LandingPage.asset("logo.png", "image/png");
            case "/background.jpg": return LandingPage.asset("background.jpg", "image/jpeg");

            case "/register": return CitizenPages.register(r);
            case "/login": return CitizenPages.login(r);
            case "/logout": { Resp x = Resp.redirect("/"); x.logout = true; return x; }
            case "/home": return CitizenPages.home(r);
            case "/profile": return CitizenPages.profile(r);
            case "/services": return CitizenPages.services(r);
            case "/apply": return CitizenPages.apply(r);
            case "/apps": return CitizenPages.apps(r);
            case "/app": return CitizenPages.appDetail(r);
            case "/pay": return CitizenPages.pay(r);
            case "/docs": return CitizenPages.docs(r);
            case "/pension": return CitizenPages.pension(r);
            case "/book": return CitizenPages.book(r);
            case "/notifs": return CitizenPages.notifs(r);
            case "/access": return CitizenPages.access(r);
            case "/file": return FilePage.serve(r);

            case "/staff/departments": return DepartmentPages.select(r);
            case "/staff/login": return DepartmentPages.login(r);
            case "/staff": return StaffPages.dashboard(r);
            case "/staff/search": return StaffPages.search(r);
            case "/staff/apps": return StaffPages.apps(r);
            case "/staff/update": return StaffPages.update(r);
            case "/staff/reports": return StaffPages.reports(r);
            case "/staff/queue": return StaffPages.queue(r);
            case "/staff/pension": return StaffPages.pension(r);
            case "/staff/payments": return StaffPages.payments(r);
            case "/staff/fees": return StaffPages.fees(r);
            case "/staff/pending": return StaffPages.pending(r);
            case "/staff/docs": return StaffPages.docs(r);
            case "/staff/life": return StaffPages.life(r);
            case "/staff/access": return StaffPages.access(r);
            default: return Resp.notFound();
        }
    }
}