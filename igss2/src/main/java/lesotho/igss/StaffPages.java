package lesotho.igss;

import lesotho.igss.Models.*;
import lesotho.igss.Policy.Field;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Everything a government employee sees. Each department only gets the tools and the data it needs. */
final class StaffPages {
    private StaffPages() {}

    private static final String B = "Dashboard";

    private static Resp needLogin() { return Resp.redirect("/staff/login"); }

    private static Resp only(Official o, Dept d) {
        if (o == null) return needLogin();
        return o.dept == d ? null : Resp.to("/staff", "err:" + d.label + " staff only.");
    }

    static Resp login(Req r) {
        if (r.post()) {
            Official o = Store.official(r.get("staff"));
            if (o != null && Store.hash(o.salt, r.p.getOrDefault("password", "")).equals(o.hash)) {
                Resp resp = Resp.redirect("/staff");
                resp.newSession = new Sess("O", o.staffId);
                return resp;
            }
            return Resp.to("/staff/login", "err:Wrong staff ID or password.");
        }
        return Resp.html(Html.page("Department Official Login", "Back", "/", Html.banner(r.get("m")) + Html.intro("Staff only. Every action is logged.")
            + Html.form("/staff/login", Html.field("Staff ID", "staff", "text", "") + Html.field("Password", "password", "password", "") + "<br>" + Html.button("Login"))));
    }

    static Resp dashboard(Req r) {
        Official o = r.staff();
        if (o == null) return needLogin();
        List<String[]> t = new ArrayList<>();
        t.add(new String[]{"/staff/search", "id", "Citizen Verification"});
        t.add(new String[]{"/staff/apps", "doc", "Applications"});
        t.add(new String[]{"/staff/reports", "chart", "Status & Reports"});
        switch (o.dept) {
            case HA:
                t.add(new String[]{"/staff/pending", "user", "Pending Verifications"});
                t.add(new String[]{"/staff/docs", "folder", "Document Verification"});
                t.add(new String[]{"/staff/life", "alert", "Life Status"});
                t.add(new String[]{"/staff/queue", "cal", "Appointments / Queue"});
                t.add(new String[]{"/staff/access", "lock", "Access Records"});
                break;
            case FI:
                t.add(new String[]{"/staff/fees", "card", "Fees & Receipts"});
                t.add(new String[]{"/staff/payments", "coin", "Pension Payments"});
                break;
            case PE: t.add(new String[]{"/staff/pension", "coin", "Pension Register"}); break;
            case PO:
                t.add(new String[]{"/staff/queue", "cal", "Appointments / Queue"});
                t.add(new String[]{"/staff/access", "lock", "Access Records"});
                break;
            default: t.add(new String[]{"/staff/queue", "cal", "Appointments / Queue"});
        }
        int open = 0;
        for (Application a : Store.appsOf(o.dept)) if (a.status != Status.COMPLETED && a.status != Status.REJECTED) open++;
        return Resp.html(Html.page(o.dept.label + " Dashboard", null, null, Html.banner(r.get("m"))
            + Html.card("<b>Welcome, " + Html.esc(o.name) + "</b><span class='mu'>" + o.dept.label + " · " + open + " open applications</span>") + Html.tiles(t)
            + "<a class='sm' style='text-align:center' href='/logout'>Log out</a>"));
    }

    /** Remote identity check: no ID card and no visit needed. Four separate boxes, fill in any of them. */
    static Resp search(Req r) {
        Official o = r.staff();
        if (o == null) return needLogin();
        StringBuilder result = new StringBuilder();
        String name = r.get("name"), id = r.get("id"), phone = r.get("phone"), fp = r.get("fp");
        if (r.post()) {
            if (name.isEmpty() && id.isEmpty() && phone.isEmpty() && fp.isEmpty()) result.append("<div class='err'>! Fill in at least one box: name, ID number, phone number or fingerprint code.</div>");
            else if (r.get("purpose").length() < 3) result.append("<div class='err'>! Write a short reason for access. It is saved in the access log.</div>");
            else {
                List<Citizen> found = Store.search(name, id, phone, fp);
                if (found.isEmpty()) result.append("<div class='err'>! No match in the Home Affairs register.</div>");
                for (Citizen c : found) {
                    List<Field> ok = Policy.allowed(o.dept);
                    List<String> shown = new ArrayList<>(), hidden = new ArrayList<>(), names = new ArrayList<>();
                    for (Field f : Field.values()) {
                        if (ok.contains(f)) { shown.add(f.label + ": " + Policy.value(c, f, !fp.isEmpty())); names.add(f.label); }
                        else hidden.add(f.label);
                    }
                    Store.logAccess(c.id, o.dept, o.name, String.join(", ", names), r.get("purpose"));
                    Store.notify(c.id, o.dept.label + " checked your identity (" + String.join(", ", names) + ") for: " + r.get("purpose") + ".");
                    Doc ph = ok.contains(Field.PHOTO) ? Store.docOf(c.id, "Passport photo") : null;
                    String img = ph != null && ph.stored != null ? "<img class='ph' src='/file?id=" + ph.id + "' alt='Photo'>" : "";
                    result.append("<div class='ok'>&#10003; Verified by Home Affairs. Access logged and citizen notified.</div>")
                        .append(Html.card("<div class='two'>" + img + "<div><b style='font-size:18px'>" + Html.esc(c.name) + "</b></div></div>"
                            + Html.label("THIS DEPARTMENT CAN SEE") + Html.chips(shown, true) + Html.label("NOT SHARED") + Html.chips(hidden, false)));
                }
            }
        }
        String form = Html.form("/staff/search", Html.field("Name", "name", "text", name) + Html.field("ID number", "id", "text", id)
            + Html.field("Phone number", "phone", "text", phone) + Html.field("Fingerprint code (for example FP-1001)", "fp", "text", fp)
            + Html.field("Reason for access", "purpose", "text", r.get("purpose")) + "<br>" + Html.button("Verify identity"));
        return Resp.html(Html.page("Citizen Verification", B, "/staff", Html.intro("Find a citizen without an ID card or a visit. You only see the fields your department needs.") + form + result));
    }

    static Resp apps(Req r) {
        Official o = r.staff();
        if (o == null) return needLogin();
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Applications for " + o.dept.label + ". Change a status and the citizen is told."));
        for (Application a : Store.appsOf(o.dept)) {
            Citizen c = Store.citizen(a.citizenId);
            Policy.Service sv = Policy.service(a.dept, a.service);
            StringBuilder docs = new StringBuilder();
            if (sv != null) for (String dt : sv.docs) {
                Doc d = Store.docOf(a.citizenId, dt);
                if (d != null && d.stored != null && Policy.canView(o, d)) docs.append("<a class='sm' href='/file?id=").append(d.id).append("' target='_blank'>").append(Html.esc(dt)).append("</a> ");
            }
            String action;
            if (a.status == Status.REJECTED || a.status == Status.COMPLETED) action = "";
            else if (a.status == Status.PAYMENT_DUE) action = Html.mu("Waiting for the citizen to pay M " + a.fee + " to Finance.");
            else action = Html.form("/staff/update", Html.hidden("id", String.valueOf(a.id))
                + Html.select("New status", "status", "PROCESSING|Processing", "NEEDS_INFO|Needs info", "APPROVED|Approved", "COMPLETED|Completed", "REJECTED|Rejected")
                + Html.field("Note to citizen (optional)", "note", "text", "") + "<br>" + Html.button("Update status"));
            sb.append(Html.card("<b>" + Html.esc(a.service) + "</b><span class='mu'>" + Html.esc(c.name) + " · " + a.reference
                + (a.fee > 0 ? " · fee M " + a.fee + (a.paid ? " paid" : " unpaid") : "") + (c.alive ? "" : " · Home Affairs: deceased") + "</span>" + Html.pill(a.status.label)
                + (docs.length() > 0 ? Html.label("DOCUMENTS (opening one is logged)") + "<div>" + docs + "</div>" : "") + action));
        }
        return Resp.html(Html.page("Applications", B, "/staff", sb.toString()));
    }

    static Resp update(Req r) {
        Official o = r.staff();
        if (o == null) return needLogin();
        Application a;
        try { a = Store.app(Integer.parseInt(r.get("id"))); } catch (Exception e) { a = null; }
        if (a == null || a.dept != o.dept || !r.post()) return Resp.to("/staff/apps", "err:That application belongs to another department.");
        if (!a.paid && a.fee > 0) return Resp.to("/staff/apps", "err:The fee has not been paid to Finance yet.");
        Status st;
        try { st = Status.valueOf(r.get("status")); } catch (Exception e) { return Resp.to("/staff/apps", "err:Unknown status."); }
        Citizen c = Store.citizen(a.citizenId);
        if (!c.alive && st != Status.REJECTED) return Resp.to("/staff/apps", "err:Home Affairs records this person as deceased. Reject the application.");
        Policy.Service sv = Policy.service(a.dept, a.service);
        if (sv != null && sv.pension && (st == Status.APPROVED || st == Status.COMPLETED)) {
            int age = Policy.age(c, LocalDate.now());
            if (age < Policy.PENSION_AGE) return Resp.to("/staff/apps", "err:Home Affairs shows this citizen is " + age + ". The minimum age is " + Policy.PENSION_AGE + ".");
            Store.enrol(c.id);
        }
        a.status = st;
        a.note = r.get("note");
        Store.saveApp(a);
        Store.notify(a.citizenId, "Your " + a.service + " is now " + a.status.label + (a.note.isEmpty() ? "." : ": " + a.note)
            + (sv != null && sv.pension && st == Status.APPROVED ? " You are now on the pension register." : ""));
        return Resp.to("/staff/apps", "ok:Status updated and the citizen was notified.");
    }

    static Resp reports(Req r) {
        Official o = r.staff();
        if (o == null) return needLogin();
        Map<Status, Integer> n = new LinkedHashMap<>();
        for (Status s : Status.values()) n.put(s, 0);
        List<Application> l = Store.appsOf(o.dept);
        for (Application a : l) n.merge(a.status, 1, Integer::sum);
        StringBuilder sb = new StringBuilder(Html.intro(l.size() + " applications in total."));
        for (Map.Entry<Status, Integer> e : n.entrySet()) {
            int pct = l.isEmpty() ? 0 : e.getValue() * 100 / l.size();
            sb.append(Html.card("<span class='mu'>" + e.getKey().label + "</span><b style='font-size:22px'>" + e.getValue() + "</b><div class='bar'><i style='width:" + pct + "%'></i></div>"));
        }
        if (o.dept == Dept.FI) {
            int fees = 0, pensions = 0;
            for (Payment p : Store.payments("FEE")) fees += p.amount;
            for (Payment p : Store.payments("PENSION")) if (p.paid) pensions += p.amount;
            sb.append(Html.card("<span class='mu'>Service fees collected</span><b style='font-size:22px'>M " + fees + "</b>"))
              .append(Html.card("<span class='mu'>Pensions paid out</span><b style='font-size:22px'>M " + pensions + "</b>"));
        }
        return Resp.html(Html.page("Status & Reports", B, "/staff", sb.toString()));
    }

    static Resp queue(Req r) {
        Official o = r.staff();
        if (o == null) return needLogin();
        if (r.post()) {
            List<Ticket> w = Store.waiting(o.dept);
            if (w.isEmpty()) return Resp.to("/staff/queue", "err:Nobody is waiting.");
            Ticket t = w.get(0);
            Store.serve(t.id);
            Store.notify(t.citizenId, "It is your turn at " + o.dept.label + ". Ticket " + t.number + ".");
            return Resp.to("/staff/queue", "ok:Called " + t.number + ".");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Digital queue for " + o.dept.label + "."));
        List<Ticket> w = Store.waiting(o.dept);
        for (Ticket t : w) sb.append(Html.row(t.number, Store.citizen(t.citizenId).name + " · " + t.service, Html.pill("Pending"), null));
        if (w.isEmpty()) sb.append(Html.mu("Nobody is waiting."));
        sb.append(Html.form("/staff/queue", Html.button("Call next")));
        return Resp.html(Html.page("Appointments / Queue", B, "/staff", sb.toString()));
    }

    /** Pension register. Eligibility comes from the date of birth and life status that Home Affairs holds. */
    static Resp pension(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.PE);
        if (bad != null) return bad;
        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.from(today);
        if (r.post()) {
            Citizen c = Store.citizen(r.get("id"));
            if (c == null) return Resp.to("/staff/pension", "err:Citizen not found.");
            if (r.get("act").equals("invite")) {
                if (!Policy.pensionAge(c, today) || !c.alive) return Resp.to("/staff/pension", "err:Not eligible.");
                Store.notify(c.id, "You are " + Policy.age(c, today) + " and can apply for the old-age pension. Open My Pension to apply.");
                return Resp.to("/staff/pension", "ok:Invitation sent to " + c.name + ".");
            }
            if (!c.alive || !Store.enrolled(c.id) || Store.pensionPayment(c.id, ym) != null) return Resp.to("/staff/pension", "err:Cannot send this payment.");
            Store.addPayment(c.id, "PENSION", "Pension " + ym, Policy.PENSION_AMOUNT, ym.toString(), false, null, 0);
            Store.notify(c.id, "Your pension for " + ym + " was sent to Finance for payment.");
            return Resp.to("/staff/pension", "ok:Sent to Finance.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Payday: " + Policy.payday(ym) + ". Nobody has to queue."));
        sb.append(Html.label("ON THE PENSION REGISTER"));
        for (Citizen c : Store.enrolledList()) {
            if (!c.alive) continue;
            Payment p = Store.pensionPayment(c.id, ym);
            String send = p == null ? Html.form("/staff/pension", Html.hidden("id", c.id) + Html.hidden("act", "send") + "<button class='sm'>Send to Finance</button>") : "";
            sb.append("<div class='rw'><div><b>").append(Html.esc(c.name)).append("</b><small>Age ").append(Policy.age(c, today)).append(" · M ").append(Policy.PENSION_AMOUNT)
              .append("</small></div>").append(Html.pill(Policy.pensionState(p, today))).append(send).append("</div>");
        }
        sb.append(Html.label("AGE " + Policy.PENSION_AGE + "+ BUT NOT YET APPLIED"));
        for (Citizen c : Store.allCitizens())
            if (c.alive && Policy.pensionAge(c, today) && !Store.enrolled(c.id)) {
                String inv = c.hasAccount() ? Html.form("/staff/pension", Html.hidden("id", c.id) + Html.hidden("act", "invite") + "<button class='sm'>Invite to apply</button>") : "";
                sb.append("<div class='rw'><div><b>").append(Html.esc(c.name)).append("</b><small>Age ").append(Policy.age(c, today)).append("</small></div>").append(inv).append("</div>");
            }
        sb.append(Html.label("TURNING " + Policy.PENSION_AGE + " WITHIN 60 DAYS"));
        for (Citizen c : Store.allCitizens())
            if (c.alive && !Policy.pensionAge(c, today) && !Policy.turnsPensionAge(c).isAfter(today.plusDays(60))) sb.append(Html.row(c.name, "Turns " + Policy.PENSION_AGE + " on " + Policy.turnsPensionAge(c), Html.pill("Pending"), null));
        sb.append(Html.label("STOPPED (HOME AFFAIRS REPORTS DEATH)"));
        for (Citizen c : Store.enrolledList()) if (!c.alive) sb.append(Html.row(c.name, "Payments stopped automatically", Html.pill("Deceased"), null));
        return Resp.html(Html.page("Pension Register", B, "/staff", sb.toString()));
    }

    static Resp payments(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.FI);
        if (bad != null) return bad;
        if (r.post()) {
            Payment p;
            try { p = Store.payment(Integer.parseInt(r.get("id"))); } catch (Exception e) { p = null; }
            if (p == null || p.paid || !p.kind.equals("PENSION")) return Resp.to("/staff/payments", "err:Nothing to pay.");
            String receipt = Store.nextReceipt();
            Store.markPaid(p.id, receipt);
            Store.notify(p.citizenId, "Your pension payment of M " + p.amount + " for " + p.month + " has been paid. Receipt " + receipt + ".");
            return Resp.to("/staff/payments", "ok:Marked as paid and the citizen was notified.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Pension payments sent by Pensions. Finance only sees name and ID number."));
        for (Payment p : Store.payments("PENSION")) {
            Citizen c = Store.citizen(p.citizenId);
            String act = p.paid ? "" : Html.form("/staff/payments", Html.hidden("id", String.valueOf(p.id)) + "<button class='sm'>Mark paid</button>");
            sb.append("<div class='rw'><div><b>").append(Html.esc(c.name)).append("</b><small>ID ").append(c.id).append(" · M ").append(p.amount).append(" · ").append(p.month)
              .append("</small></div>").append(Html.pill(p.paid ? "Paid" : "Pending")).append(act).append("</div>");
        }
        return Resp.html(Html.page("Pension Payments", B, "/staff", sb.toString()));
    }

    /** Service fees paid by citizens to any department. Finance sees who paid what, and the receipt. */
    static Resp fees(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.FI);
        if (bad != null) return bad;
        int total = 0;
        StringBuilder sb = new StringBuilder();
        List<Payment> l = Store.payments("FEE");
        for (Payment p : l) {
            Citizen c = Store.citizen(p.citizenId);
            total += p.amount;
            sb.append(Html.row(p.ref, c.name + " · ID " + c.id + " · M " + p.amount + " · " + p.receipt, Html.pill("Paid"), null));
        }
        if (l.isEmpty()) sb.append(Html.mu("No fees paid yet."));
        return Resp.html(Html.page("Fees & Receipts", B, "/staff", Html.intro("Fees paid for services in every department. Total collected: M " + total) + sb));
    }

    static Resp pending(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.HA);
        if (bad != null) return bad;
        if (r.post()) {
            Citizen c = Store.citizen(r.get("id"));
            if (c != null && c.hasAccount() && !c.accountVerified) {
                Store.setAccountVerified(c.id);
                Store.notify(c.id, "Home Affairs confirmed your account. You can now apply for services.");
                return Resp.to("/staff/pending", "ok:Account verified.");
            }
            return Resp.to("/staff/pending", "err:Nothing to verify.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("New accounts where the name does not exactly match the register."));
        boolean any = false;
        for (Citizen c : Store.allCitizens())
            if (c.hasAccount() && !c.accountVerified) {
                any = true;
                sb.append(Html.card("<b>Typed: " + Html.esc(c.registeredName) + "</b><span class='mu'>Register: " + Html.esc(c.name) + " · ID " + c.id + "</span>"
                    + Html.form("/staff/pending", Html.hidden("id", c.id) + Html.button("Confirm identity"))));
            }
        if (!any) sb.append(Html.mu("No accounts are waiting."));
        return Resp.html(Html.page("Pending Verifications", B, "/staff", sb.toString()));
    }

    static Resp docs(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.HA);
        if (bad != null) return bad;
        if (r.post()) {
            Doc d;
            try { d = Store.doc(Integer.parseInt(r.get("id"))); } catch (Exception e) { d = null; }
            if (d != null && d.status.equals("Uploaded")) {
                Store.setDocStatus(d.id, "Verified");
                Store.notify(d.citizenId, "Home Affairs verified your " + d.type + ".");
                return Resp.to("/staff/docs", "ok:Document verified.");
            }
            return Resp.to("/staff/docs", "err:Nothing to verify.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Documents uploaded by citizens. Open one to check it, then verify it."));
        List<Doc> l = Store.uploadedDocs();
        for (Doc d : l) {
            Citizen c = Store.citizen(d.citizenId);
            String view = d.stored == null ? "" : "<a class='sm' href='/file?id=" + d.id + "' target='_blank'>Open</a>";
            sb.append("<div class='rw'><div><b>").append(Html.esc(d.type)).append("</b><small>").append(Html.esc(c.name)).append(" · ").append(Html.esc(d.filename)).append("</small></div>").append(view)
              .append(Html.form("/staff/docs", Html.hidden("id", String.valueOf(d.id)) + "<button class='sm'>Verify</button>")).append("</div>");
        }
        if (l.isEmpty()) sb.append(Html.mu("No documents are waiting."));
        return Resp.html(Html.page("Document Verification", B, "/staff", sb.toString()));
    }

    /** Home Affairs registers a death. Pensions stops payments and open applications are rejected, automatically. */
    static Resp life(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.HA);
        if (bad != null) return bad;
        if (r.post()) {
            Citizen c = Store.citizen(r.get("id"));
            if (c != null && c.alive) {
                Store.registerDeath(c.id);
                return Resp.to("/staff/life", "ok:Death registered. Pensions stops payments and open applications are rejected.");
            }
            return Resp.to("/staff/life", "err:Nothing to update.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Life status is kept here and shared with Pensions and every department."));
        for (Citizen c : Store.allCitizens()) {
            String act = c.alive ? "<form method='post' action='/staff/life' onsubmit=\"return confirm('Register a death for " + Html.esc(c.name) + "?')\">" + Html.hidden("id", c.id) + "<button class='sm'>Register death</button></form>" : "";
            sb.append("<div class='rw'><div><b>").append(Html.esc(c.name)).append("</b><small>ID ").append(c.id).append("</small></div>").append(Html.pill(c.alive ? "Alive" : "Deceased")).append(act).append("</div>");
        }
        return Resp.html(Html.page("Life Status", B, "/staff", sb.toString()));
    }

    static Resp access(Req r) {
        Official o = r.staff();
        if (o == null) return needLogin();
        boolean all = o.dept == Dept.HA || o.dept == Dept.PO;
        StringBuilder sb = new StringBuilder(Html.intro(all ? "Every access to citizen data, newest first." : "Your department's accesses, newest first."));
        for (AccessEntry e : Store.logRecent(all ? null : o.dept, 40))
            sb.append(Html.row(e.dept.label + " · " + e.officer, Store.citizen(e.citizenId).name + " · " + e.fields + " · " + e.purpose + " · " + CitizenPages.WHEN.format(e.time), Html.pill("Logged"), null));
        return Resp.html(Html.page("Access Records", B, "/staff", sb.toString()));
    }
}
