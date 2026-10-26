package lesotho.igss;

import lesotho.igss.Models.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Passport flags. Police (any reason) and Passport Services (immigration reasons) place a flag on a person.
 * While a flag is active, Passport Services cannot approve, ready or complete a passport for that person.
 * Only Police lift a flag. Home Affairs can see the list. Page: /staff/flags
 */
final class TravelFlags {
    private TravelFlags() {}

    static final String[] KINDS = {"Immigration violation", "Visa overstay", "Banned from entering a country", "Wanted for a crime"};
    private static final Set<String> PASSPORT_KINDS = Set.of(KINDS[0], KINDS[1], KINDS[2]);

    static void init() {
        Db.exec("create table if not exists travel_flags(id integer primary key autoincrement,citizen_id text,kind text,details text,officer text,dept text,case_no text,status text,created text,lifted_by text,lifted text)");
    }

    private static final class Flag { int id; String citizenId, kind, details, officer, dept, caseNo, status, created; }

    private static final Db.Mapper<Flag> FL = r -> {
        Flag f = new Flag();
        f.id = r.getInt("id"); f.citizenId = r.getString("citizen_id"); f.kind = r.getString("kind"); f.details = r.getString("details") == null ? "" : r.getString("details");
        f.officer = r.getString("officer"); f.dept = r.getString("dept"); f.caseNo = r.getString("case_no") == null ? "" : r.getString("case_no");
        f.status = r.getString("status"); f.created = r.getString("created");
        return f;
    };

    private static List<Flag> active(String citizenId) { return Db.list("select * from travel_flags where citizen_id=? and status='Active' order by id desc", FL, citizenId); }

    private static String day(String iso) { return iso == null ? "" : iso.length() >= 10 ? iso.substring(0, 10) : iso; }

    private static String deptLabel(String code) {
        try { return Dept.valueOf(code).label; } catch (Exception e) { return code; }
    }

    /** Text for an error message when a passport must not be issued, or null when there is no active flag. */
    static String holdText(String citizenId) {
        List<Flag> l = active(citizenId);
        if (l.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Flag f : l) sb.append(sb.length() > 0 ? "; " : "").append(f.kind).append(" (").append(deptLabel(f.dept)).append(", ").append(day(f.created)).append(")");
        return sb.toString();
    }

    /** Red warning boxes for Passport Services: active flags, and open police cases about this person. */
    static String warnHtml(String citizenId) {
        StringBuilder sb = new StringBuilder();
        for (Flag f : active(citizenId))
            sb.append("<div class='err'>! PASSPORT FLAG: ").append(Html.esc(f.kind)).append(" · ").append(Html.esc(deptLabel(f.dept))).append(" · ").append(day(f.created))
                    .append(f.details.isEmpty() ? "" : "<br>" + Html.esc(f.details)).append("<br>Do not issue a passport. Reject it, or wait until Police lift the flag.</div>");
        for (CaseRec cs : Store.cases())
            if (citizenId.equals(cs.citizenId) && !cs.status.equals("Closed"))
                sb.append("<div class='err'>! This person is linked to the open police case ").append(Html.esc(cs.caseNo)).append(". Check with Police before issuing.</div>");
        return sb.toString();
    }

    /** Flags shown on the Police person record. */
    static String personHtml(String citizenId, String caseNo) {
        StringBuilder sb = new StringBuilder(Html.label("PASSPORT FLAGS"));
        List<Flag> l = active(citizenId);
        for (Flag f : l) sb.append(Html.row(f.kind, deptLabel(f.dept) + " · " + day(f.created) + (f.details.isEmpty() ? "" : " · " + f.details), Html.pill("Active"), null));
        if (l.isEmpty()) sb.append(Html.mu("No passport flags."));
        sb.append("<a class='sm' href='/staff/flags?id=").append(Html.esc(citizenId)).append("&case_no=").append(Html.esc(caseNo)).append("'>Flag this person for passports</a>");
        return sb.toString();
    }

    static Resp page(Req r) {
        Official o = r.staff();
        if (o == null) return Resp.redirect("/staff/departments");
        if (o.dept != Dept.PO && o.dept != Dept.PA && o.dept != Dept.HA) return Resp.to("/staff", "err:Police, Passport Services and Home Affairs only.");
        boolean police = o.dept == Dept.PO, passport = o.dept == Dept.PA;
        String notice = "";

        if (r.post()) {
            String act = r.get("act");
            if (act.equals("lift")) {
                if (!police) return Resp.to("/staff/flags", "err:Only Police can lift a flag.");
                try {
                    int id = Integer.parseInt(r.get("flag"));
                    Db.exec("update travel_flags set status='Lifted', lifted_by=?, lifted=? where id=?", o.name, LocalDateTime.now(), id);
                    notice = "<div class='ok'>&#10003; Flag lifted.</div>";
                } catch (NumberFormatException e) { notice = "<div class='err'>! Flag not found.</div>"; }
            } else if (!police && !passport) {
                return Resp.to("/staff/flags", "err:Home Affairs can see flags but cannot add them.");
            } else {
                Citizen c = Store.citizen(r.get("id"));
                String kind = r.get("kind"), details = r.get("details").trim(), caseNo = r.get("case_no").trim().toUpperCase();
                List<String> allowed = new ArrayList<>(police ? Arrays.asList(KINDS) : PASSPORT_KINDS);
                CaseRec cs = police ? Store.caseByNo(caseNo) : null;
                if (c == null) notice = "<div class='err'>! No citizen with that ID number in the register.</div>";
                else if (!allowed.contains(kind)) notice = "<div class='err'>! Choose a reason.</div>";
                else if (details.length() < 5) notice = "<div class='err'>! Write what happened (a few words).</div>";
                else if (police && (cs == null || cs.status.equals("Closed"))) notice = "<div class='err'>! Police flag a person only under an open case. Pick one.</div>";
                else if (!active(c.id).stream().filter(f -> f.kind.equals(kind)).toList().isEmpty()) notice = "<div class='err'>! That flag is already active for this person.</div>";
                else {
                    if (details.length() > 300) details = details.substring(0, 300);
                    Db.exec("insert into travel_flags(citizen_id,kind,details,officer,dept,case_no,status,created) values(?,?,?,?,?,?,?,?)",
                            c.id, kind, details, o.name, o.dept.name(), police ? cs.caseNo : null, "Active", LocalDateTime.now());
                    Store.logAccess(c.id, o.dept, o.name, "Passport flag", kind + (police ? ", case " + cs.caseNo : ""));
                    if (police) Store.addCaseNote(cs.caseNo, o.name, "Passport flag placed on " + c.name + ": " + kind + ". " + details);
                    notice = "<div class='ok'>&#10003; Flag placed on " + Html.esc(c.name) + ". Passport Services cannot issue a passport to this person until Police lift it.</div>";
                }
            }
        }

        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + notice + Html.intro(
                "A flag stops Passport Services from issuing a passport. Use it for an immigration violation, a visa overstay, a ban from entering a country, or a person wanted for a crime. "
                        + (police ? "Only Police can lift a flag." : passport ? "You can add immigration flags. Only Police can lift a flag." : "Home Affairs can see the flags here.")));

        if (police || passport) {
            List<String> kinds = new ArrayList<>();
            kinds.add("|Choose a reason");
            for (String k : KINDS) if (police || PASSPORT_KINDS.contains(k)) kinds.add(k);
            StringBuilder f = new StringBuilder(Html.field("ID number of the person", "id", "text", r.get("id")) + Html.selectOf("Reason", "kind", r.get("kind"), kinds.toArray(new String[0])));
            if (police) {
                List<String> cases = new ArrayList<>();
                cases.add("|Choose an open case");
                for (CaseRec cs : Store.openCases()) cases.add(cs.caseNo + "|" + cs.caseNo + " · " + cs.title);
                f.append(Html.selectOf("Case", "case_no", r.get("case_no"), cases.toArray(new String[0])));
            }
            f.append(Html.field("What happened (country, date, case)", "details", "text", "")).append("<br>").append(Html.button("Place flag"));
            sb.append(Html.label("FLAG A PERSON")).append(Html.form("/staff/flags", f.toString() + Html.hidden("act", "add")));
        }

        sb.append(Html.label("ACTIVE FLAGS"));
        List<Flag> all = Db.list("select * from travel_flags where status='Active' order by id desc limit 100", FL);
        for (Flag fl : all) {
            Citizen c = Store.citizen(fl.citizenId);
            String body = Html.esc(fl.kind) + "<span class='mu'>" + Html.esc(c == null ? fl.citizenId : c.name) + " · ID " + Html.esc(fl.citizenId) + " · " + Html.esc(deptLabel(fl.dept)) + " · " + day(fl.created)
                    + (fl.caseNo.isEmpty() ? "" : " · case " + Html.esc(fl.caseNo)) + (fl.details.isEmpty() ? "" : "<br>" + Html.esc(fl.details)) + "</span>";
            if (police) body += Html.form("/staff/flags", Html.hidden("act", "lift") + Html.hidden("flag", String.valueOf(fl.id)) + "<button class='sm' data-confirm='Lift this flag? The person can then get a passport again.'>Lift flag</button>");
            sb.append(Html.card("<b>" + body.replaceFirst("<span", "</b><span")));
        }
        if (all.isEmpty()) sb.append(Html.mu("No active flags."));
        return Resp.html(Html.page("Passport Flags", "Dashboard", "/staff", sb.toString()));
    }
}