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
    static final int MAX_BODY = 16 * 1024 * 1024; // a photo plus several documents; each file is limited to 5 MB in PublicApply

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String db = args.length > 1 ? args[1] : "igss.db";
        Store.init(db);
        PublicApply.init();
        ServiceForms.init();
        TravelFlags.init();
        Store.migrateIds();
        Store.migrateBenefits();
        Store.initPassports();
        Store.initTax();
        Store.initIdCards();
        Store.initTraffic();
        Certificates.init();
        Ussd.init();
        Registry.startPensionJob();
        Db.exec("update applications set service='Change of Names' where service='Change of Name'");
        Db.exec("update applications set service='Change of Ownership' where service='Vehicle Ownership Transfer'");
        System.out.println("Database file: " + java.nio.file.Paths.get(db).toAbsolutePath());
        System.out.println("Uploads folder: " + Store.UPLOADS.toAbsolutePath());
        System.out.println("Walk-in applications in this database: " + Db.count("select count(*) from public_apps")
                + " | citizen applications: " + Db.count("select count(*) from applications"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("IGSS is running. Open http://localhost:" + port);
        System.out.println("DATABASE FILE : " + new java.io.File(db).getAbsolutePath());
        System.out.println("UPLOADS FOLDER: " + Store.UPLOADS.toAbsolutePath());
        System.out.println("Applications: " + Db.count("select count(*) from public_apps") + " without an account, " + Db.count("select count(*) from applications") + " from citizen accounts.");
    }

    private static void handle(HttpExchange ex) throws IOException {
        byte[] out;
        int code;
        String type = "text/html; charset=utf-8";
        try {
            String sid = cookie(ex, "sid");
            String lang = "st".equals(cookie(ex, "lang")) ? "st" : "en";
            String uri = ex.getRequestURI().toString();
            Req r = new Req(ex.getRequestMethod(), ex.getRequestURI().getPath(), sid == null ? null : SESSIONS.get(sid));
            r.p.putAll(Req.parse(ex.getRequestURI().getRawQuery()));
            Resp resp;
            if (r.post()) {
                byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) {
                    resp = Resp.error(413, "That upload is too big. Each file can be up to 5 MB.");
                    body = null;
                } else {
                    String ct = ex.getRequestHeaders().getFirst("Content-Type");
                    if (ct != null && ct.startsWith("multipart/form-data") && ct.contains("boundary=")) r.parseMultipart(body, ct.substring(ct.indexOf("boundary=") + 9).replace("\"", ""));
                    else r.p.putAll(Req.parse(new String(body, StandardCharsets.UTF_8)));
                    synchronized (Store.LOCK) { resp = dispatch(ex, r); }
                }
            } else {
                synchronized (Store.LOCK) { resp = dispatch(ex, r); }
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
            String text = resp.bytes != null ? null : resp.body;
            if (text != null && type.startsWith("text/html") && code != 302) text = Sesotho.apply(Display.process(text, lang, uri), lang);
            out = resp.bytes != null ? resp.bytes : (text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
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

    /**
     * /lang?l=st&next=/home switches the language (cookie) and goes back to the page.
     * Every submitted form is checked first: names may only have letters, ID numbers only digits.
     * Everything else goes to route.
     */
    private static Resp dispatch(HttpExchange ex, Req r) {
        if (r.path.equals("/lang")) {
            String l = r.get("l").equals("st") ? "st" : "en";
            String next = r.get("next");
            if (!next.startsWith("/") || next.startsWith("//") || next.contains("\n") || next.contains("\r")) next = "/";
            ex.getResponseHeaders().add("Set-Cookie", "lang=" + l + "; Path=/; Max-Age=31536000; SameSite=Lax");
            return Resp.redirect(next);
        }
        if (r.post()) {
            String bad = Display.badInput(r);
            if (bad != null) return Resp.error(400, bad);
        }
        return route(r);
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
            case "/feature.jpg": return LandingPage.asset("feature.jpg", "image/jpeg");

            // Applications that need no login
            case "/public": return PublicApply.home(r);
            case "/public/id": return Resp.redirect("/public");
            case "/public/birth": return PublicApply.form(r, "birth");
            case "/public/done": return PublicApply.done(r);
            case "/public/track": return PublicApply.track(r);

            case "/register": return CitizenPages.register(r);
            case "/login": return CitizenPages.login(r);
            case "/logout": { Resp x = Resp.redirect("/"); x.logout = true; return x; }
            case "/home": return CitizenPages.home(r);
            case "/profile": return CitizenPages.profile(r);
            case "/photo": return CitizenPages.photo(r);
            case "/services": return CitizenPages.services(r);
            case "/apply": { Resp g = Registry.guardApply(r); return g != null ? g : ServiceForms.apply(r); }
            case "/form/view": return ServiceForms.view(r);
            case "/apps": return CitizenPages.apps(r);
            case "/app": return CitizenPages.appDetail(r);
            case "/pay": return CitizenPages.pay(r);
            case "/docs": return CitizenPages.docs(r);
            case "/fingerprints": return CitizenPages.fingerprints(r);
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
            case "/staff/police": return StaffPages.police(r);
            case "/staff/cases": return StaffPages.cases(r);
            case "/staff/case": return StaffPages.caseView(r);
            case "/staff/person": return StaffPages.person(r);
            case "/staff/lost": return StaffPages.lost(r);
            case "/staff/address": return StaffPages.address(r);
            case "/staff/clearance": return StaffPages.clearance(r);
            case "/lookup": return Registry.lookup(r);
            case "/ussd": return Ussd.ussd(r);
            case "/sms": return Ussd.smsPage(r);
            case "/staff/issue": return StaffPages.issue(r);
            case "/certificate": return Certificates.page(r);
            case "/verify": return Certificates.verify(r);
            case "/staff/fines": return StaffPages.fines(r);
            case "/staff/traffic": return StaffPages.trafficRecords(r);
            case "/staff/govt": return StaffPages.govt(r);
            case "/staff/records": return StaffPages.records(r);
            case "/staff/flags": return TravelFlags.page(r);
            case "/staff/public": return PublicApply.staffList(r);
            case "/staff/public/view": return PublicApply.staffView(r);
            case "/staff/public/update": return Registry.publicUpdate(r);
            case "/staff/public/file": return PublicApply.staffFile(r);
            default: return Resp.notFound();
        }
    }
}