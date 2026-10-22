package lesotho.igss;

import lesotho.igss.Models.Citizen;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Certificates and licences that Traffic Services issues after a test or an inspection:
 * learner's licence, driving licence and roadworthy certificate.
 * Each one has a number and a verification code. Anyone can check a certificate at /verify.
 */
final class Certificates {
    private Certificates() {}

    static final String LEARNER = "Learner's Licence", DRIVING = "Driving Licence", ROADWORTHY = "Roadworthy Certificate";
    static final List<String> TYPES = List.of(LEARNER, DRIVING, ROADWORTHY);
    static final List<String> CLASSES = List.of("Motorcycle", "Light motor vehicle (car)", "Heavy motor vehicle (truck)", "Bus / public transport");
    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    static void init() {
        Db.exec("create table if not exists certificates(id integer primary key autoincrement,citizen_id text,type text,number text,detail text,issued text,expires text,officer text,code text)");
    }

    static int fee(String type) { return type.equals(LEARNER) ? 50 : type.equals(DRIVING) ? 150 : 80; }

    static LocalDate expiryFor(String type) {
        LocalDate t = LocalDate.now();
        return type.equals(LEARNER) ? t.plusMonths(24) : type.equals(DRIVING) ? t.plusYears(5) : t.plusYears(1);
    }

    /** One certificate: {id, citizen_id, type, number, detail, issued, expires, officer, code}. */
    private static final Db.Mapper<String[]> CERT = rs -> new String[]{rs.getString("id"), rs.getString("citizen_id"), rs.getString("type"), rs.getString("number"),
            rs.getString("detail"), rs.getString("issued"), rs.getString("expires"), rs.getString("officer"), rs.getString("code")};

    static String[] issue(String citizenId, String type, String detail, String officer) {
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (Db.count("select count(*) from certificates where code=?", code) > 0);
        int id = Db.insert("insert into certificates(citizen_id,type,number,detail,issued,expires,officer,code) values(?,?,?,?,?,?,?,?)",
                citizenId, type, "", detail, LocalDate.now().toString(), expiryFor(type).toString(), officer, code);
        String prefix = type.equals(LEARNER) ? "LL" : type.equals(DRIVING) ? "DL" : "RW";
        String number = prefix + "-" + LocalDate.now().getYear() + "-" + String.format("%05d", id);
        Db.exec("update certificates set number=? where id=?", number, id);
        return Db.one("select * from certificates where id=?", CERT, id);
    }

    static List<String[]> of(String citizenId) { return Db.list("select * from certificates where citizen_id=? order by id desc", CERT, citizenId); }
    static String[] byId(int id) { return Db.one("select * from certificates where id=?", CERT, id); }
    static String[] byCode(String code) { return Db.one("select * from certificates where code=?", CERT, code.replace("-", "").replace(" ", "").toUpperCase()); }
    static boolean hasValid(String citizenId, String type) {
        return Db.count("select count(*) from certificates where citizen_id=? and type=? and expires>=?", citizenId, type, LocalDate.now().toString()) > 0;
    }
    static boolean valid(String[] c) { return LocalDate.parse(c[6]).compareTo(LocalDate.now()) >= 0; }
    static String showCode(String c) { return c.length() == 10 ? c.substring(0, 5) + "-" + c.substring(5) : c; }

    // ---------------------------------------------------------------- the certificate page (the citizen's own)
    static Resp page(Req r) {
        Citizen me = r.citizen();
        if (me == null) return Resp.redirect("/login");
        String[] c;
        try { c = byId(Integer.parseInt(r.get("id"))); } catch (Exception e) { c = null; }
        if (c == null || !c[1].equals(me.id)) return Resp.to("/services?dept=TR", "err:Certificate not found.");
        boolean licence = !c[2].equals(ROADWORTHY);
        Models.Doc ph = licence ? Store.docOf(me.id, "Passport photo") : null;
        String photo = ph != null && ph.stored != null && ph.mime != null && ph.mime.startsWith("image/")
                ? "<img src='/file?id=" + ph.id + "' alt='Photo' style='width:96px;height:120px;object-fit:cover;border:1.5px solid #1f2937;float:right;margin-left:14px'>" : "";
        String state = valid(c) ? "<b style='color:#14532d'>VALID</b>" : "<b style='color:#9b1c1c'>EXPIRED</b>";
        StringBuilder rows = new StringBuilder();
        rows.append(row(licence ? "Holder" : "Owner", Html.esc(me.name))).append(row("ID number", me.id));
        if (licence) rows.append(row("Date of birth", me.dob.format(LONG)));
        rows.append(row(licence ? "Licence number" : "Certificate number", c[3]));
        for (String part : c[4].split(" \\| ")) {
            int k = part.indexOf(": ");
            if (k > 0) rows.append(row(Html.esc(part.substring(0, k)), Html.esc(part.substring(k + 2))));
        }
        rows.append(row("Date issued", LocalDate.parse(c[5]).format(LONG))).append(row("Valid until", LocalDate.parse(c[6]).format(LONG))).append(row("Status", state))
                .append(row("Issued by", "Traffic Services (" + Html.esc(c[7]) + ")"));
        String body = "<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><title>" + Html.esc(c[2]) + " - IGSS Lesotho</title><style>"
                + "body{margin:0;background:#dfe6ee;font-family:'Segoe UI',Arial,sans-serif;color:#1f2937}.wrap{max-width:640px;margin:0 auto;padding:14px 12px 28px}"
                + ".top a{color:#00209f;font-weight:800;font-size:14px;display:inline-block;padding:4px 0 10px;text-decoration:none}"
                + ".sheet{background:#fff;border:3px double #1f2937;padding:22px 22px 18px}.head{text-align:center;border-bottom:3px double #1f2937;padding-bottom:12px;margin-bottom:14px}"
                + ".crest{font:700 13px Georgia,serif;letter-spacing:.2em;color:#00209f}.motto{font:italic 12px Georgia,serif;color:#475569;margin:2px 0 6px}"
                + "h1{font:700 21px Georgia,serif;margin:8px 0 4px;text-transform:uppercase}table{width:100%;border-collapse:collapse;font-size:14px}"
                + "td{border-bottom:1px dotted #9ca3af;padding:7px 4px;vertical-align:top}td:first-child{width:38%;color:#475569;font-size:12.5px}"
                + ".code{margin-top:16px;border:1.5px solid #1f2937;padding:10px 12px;font-size:13px}.code b{font-size:20px;letter-spacing:.12em;color:#00209f;display:block;margin:4px 0}"
                + "button{margin-top:14px;width:100%;border:0;border-radius:10px;background:#1a4fb4;color:#fff;font:700 15px 'Segoe UI',Arial,sans-serif;padding:14px;cursor:pointer}"
                + "@media print{.noprint{display:none!important}body{background:#fff}}</style></head><body><div class='wrap'>"
                + "<div class='top noprint'><a href='/services?dept=TR'>&lsaquo; Back to Traffic Services</a></div><div class='sheet'><div class='head'><div class='crest'>KINGDOM OF LESOTHO</div>"
                + "<div class='motto'>Khotso &middot; Pula &middot; Nala</div><div style='font-weight:800;font-size:13px;text-transform:uppercase'>Traffic Services</div><h1>" + Html.esc(c[2]) + "</h1></div>"
                + photo + "<table>" + rows + "</table><div style='clear:both'></div>"
                + "<div class='code'>Verification code. Anyone can check this certificate at <b style='display:inline;font-size:13px;letter-spacing:0'>/verify</b> with this code.<b>" + showCode(c[8]) + "</b></div></div>"
                + "<button class='noprint' onclick='window.print()'>Print this certificate</button></div></body></html>";
        return Resp.html(body);
    }

    private static String row(String k, String v) { return "<tr><td>" + k + "</td><td>" + v + "</td></tr>"; }

    // ---------------------------------------------------------------- public check
    static Resp verify(Req r) {
        String code = r.get("code");
        StringBuilder sb = new StringBuilder(Html.intro("Check that a licence or certificate is real. Type the verification code printed on it."));
        sb.append("<form method='get' action='/verify'>" + Html.field("Verification code", "code", "text", code) + "<br>" + Html.button("Check") + "</form>");
        if (!code.isEmpty()) {
            String[] c = byCode(code);
            if (c == null) sb.append("<div class='err'>! No certificate has that code. It may be false. Check the code and try again.</div>");
            else {
                Citizen h = Store.citizen(c[1]);
                boolean ok = valid(c);
                sb.append(ok ? "<div class='ok'>&#10003; This is a real " + Html.esc(c[2]) + " issued by Traffic Services. It is valid.</div>"
                        : "<div class='err'>! This " + Html.esc(c[2]) + " was issued by Traffic Services but it has expired.</div>");
                sb.append(Html.card("<b>" + Html.esc(c[2]) + "</b><span class='mu'>" + Html.esc(c[3]) + " · holder " + Html.esc(h == null ? "" : h.name) + "<br>Issued " + LocalDate.parse(c[5]).format(LONG)
                        + " · valid until " + LocalDate.parse(c[6]).format(LONG) + "</span>"));
            }
        }
        return Resp.html(Html.page("Check a Certificate", "Back", "/", sb.toString()));
    }
}