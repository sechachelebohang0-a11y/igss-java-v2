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
    private static final List<String> CASE_STATUSES = List.of("Open case", "Under investigation", "Suspect identified", "In court", "Closed");

    private static Resp needLogin() { return Resp.redirect("/staff/login"); }

    private static Resp only(Official o, Dept d) {
        if (o == null) return needLogin();
        return o.dept == d ? null : Resp.to("/staff", "err:" + d.label + " staff only.");
    }

    /** Picture of a citizen for this officer: the real photo only when the department is allowed to see photos, otherwise initials. */
    private static String av(Official o, Citizen c, int px) {
        return Html.avatar(c, Policy.allowed(o.dept).contains(Field.PHOTO), px);
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
        if (o.dept != Dept.FI) t.add(new String[]{"/staff/apps", "doc", o.dept == Dept.PO ? "Reports & Clearances" : "Applications"});
        t.add(new String[]{"/staff/queue", "cal", "Appointments / Queue"});
        t.add(new String[]{"/staff/reports", "chart", "Status & Reports"});
        switch (o.dept) {
            case HA:
                t.add(new String[]{"/staff/pending", "user", "Pending Verifications"});
                t.add(new String[]{"/staff/public", "id", "Walk-in Applications"});
                t.add(new String[]{"/staff/docs", "folder", "Document Verification"});
                t.add(new String[]{"/staff/life", "alert", "Life Status"});
                t.add(new String[]{"/staff/address", "search", "Police Address Requests"});
                t.add(new String[]{"/staff/flags", "alert", "Passport Flags"});
                break;
            case PA:
                t.add(new String[]{"/staff/flags", "alert", "Passport Flags"});
                break;
            case FI:
                t.add(new String[]{"/staff/fees", "card", "Fees & Receipts"});
                t.add(new String[]{"/staff/payments", "coin", "Grant & Pension Payments"});
                t.add(new String[]{"/staff/records", "chart", "Financial Records"});
                t.add(new String[]{"/staff/govt", "user", "Government Workers (read only)"});
                break;
            case PE:
                t.add(new String[]{"/staff/pension", "coin", "Pension Register"});
                t.add(new String[]{"/staff/govt", "user", "Government Workers"});
                break;
            case TR:
                t.add(new String[]{"/staff/traffic", "doc", "Licence & Vehicle Register"});
                t.add(new String[]{"/staff/issue", "id", "Tests & Certificates"});
                t.add(new String[]{"/staff/fines", "alert", "Traffic Offences & Fines"});
                break;
            case PO:
                t.add(new String[]{"/staff/police", "fp", "Criminal Search"});
                t.add(new String[]{"/staff/cases", "folder", "Police Cases"});
                t.add(new String[]{"/staff/lost", "alert", "Lost IDs & Passports"});
                t.add(new String[]{"/staff/flags", "alert", "Passport Flags"});
                break;
            default: break;
        }
        StringBuilder dn = new StringBuilder();
        for (String[] n : Store.deptNotices(o.dept)) dn.append("<div class='err' style='background:#eef4ff;border-color:#b6c8f0;color:#1f3a7a'>").append(Html.esc(n[0])).append("</div>");
        int open = 0;
        for (Application a : Store.appsOf(o.dept)) if (a.status != Status.COMPLETED && a.status != Status.REJECTED) open++;
        int walk = o.dept == Dept.HA ? PublicApply.openCount() : 0;
        open += walk;
        String walkNote = walk > 0 ? "<a class='err' style='display:block' href='/staff/public'>! " + walk + " walk-in application" + (walk == 1 ? " is" : "s are") + " waiting (ID cards and births made without an account). Tap to open.</a>" : "";
        return Resp.html(Html.page(o.dept.label + " Dashboard", null, null, Html.banner(r.get("m")) + walkNote + dn
                + Html.card("<b>Welcome, " + Html.esc(o.name) + "</b><span class='mu'>" + o.dept.label + " · " + open + (o.dept == Dept.PO ? " open reports" : " open applications") + "</span>") + Html.tiles(t)
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
                    result.append("<div class='ok'>&#10003; Verified by Home Affairs. Access logged and citizen notified.</div>")
                            .append(Html.card("<div class='two'>" + Html.avatar(c, ok.contains(Field.PHOTO), 72) + "<div><b style='font-size:18px'>" + Html.esc(c.name) + "</b></div></div>"
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
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro(o.dept == Dept.PO ? "Incident reports sent by citizens (robberies, theft, lost documents and more). Change a status and the citizen is told."
                : "Applications for " + o.dept.label + ". Change a status and the citizen is told."));
        if (o.dept == Dept.HA) {
            String walkRows = PublicApply.openRows(30);
            if (!walkRows.isEmpty()) sb.append(Html.label("WALK-IN APPLICATIONS (MADE WITHOUT AN ACCOUNT)")).append(walkRows).append(Html.label("APPLICATIONS FROM CITIZEN ACCOUNTS"));
        }
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
            else {
                String officeBox = Policy.collectable(a.service)
                        ? Html.selectOf("Collection office (used with Ready to collect)", "office", Policy.officeFor(c.address), Policy.OFFICES.toArray(new String[0])) : "";
                boolean clr = a.service.equals(CLEARANCE);
                action = (clr ? clearancePanel(a, c) : "") + Html.form("/staff/update", Html.hidden("id", String.valueOf(a.id))
                        + (clr ? Html.select("New status", "status", "PROCESSING|Processing", "NEEDS_INFO|Needs info", "READY|Ready to collect (no criminal record)", "COMPLETED|Collected", "REJECTED|Not cleared")
                        : o.dept == Dept.PO ? Html.select("New status", "status", "PROCESSING|Under investigation", "NEEDS_INFO|Needs info", "COMPLETED|Resolved", "REJECTED|Closed (no case)")
                        : Html.select("New status", "status", "PROCESSING|Processing", "NEEDS_INFO|Needs info", "APPROVED|Approved", "READY|Ready to collect", "COMPLETED|Completed (collected)", "REJECTED|Rejected"))
                        + officeBox + Html.field("Note to citizen (optional)", "note", "text", "") + "<br>" + Html.button("Update status"));
            }
            String where = a.status == Status.READY && Store.collectionOf(a.id) != null ? " · collect at " + Store.collectionOf(a.id) : "";
            sb.append(Html.card("<div class='two'>" + av(o, c, 48) + "<div><b>" + Html.esc(a.service) + "</b><span class='mu'>" + Html.esc(c.name) + " · " + a.reference
                    + (a.fee > 0 ? " · fee M " + a.fee + (a.paid ? " paid" : " unpaid") : "") + (c.alive ? "" : " · Home Affairs: deceased") + Html.esc(where) + "</span></div></div>" + Html.pill(a.status.label)
                    + (a.dept == Dept.PA && a.status != Status.COMPLETED && a.status != Status.REJECTED ? TravelFlags.warnHtml(c.id) : "")
                    + ServiceForms.answersHtml(a.id)
                    + (docs.length() > 0 ? Html.label("DOCUMENTS (opening one is logged)") + "<div>" + docs + "</div>" : "") + action));
        }
        return Resp.html(Html.page(o.dept == Dept.PO ? "Reports & Clearances" : "Applications", B, "/staff", sb.toString()));
    }

    static final String CLEARANCE = "Police Clearance Certificate";

    /** Fingerprint request status and criminal record check shown on a clearance application. */
    private static String clearancePanel(Application a, Citizen c) {
        AddressRequest q = Store.latestAddressRequest(c.id, a.reference);
        List<CaseRec> cases = Store.casesOfPerson(c.id);
        StringBuilder sb = new StringBuilder(Html.label("FINGERPRINTS FROM HOME AFFAIRS"));
        if (q == null) sb.append(Html.mu("Not asked yet. Police must ask Home Affairs for the fingerprints before the certificate can be given."))
                .append(Html.form("/staff/clearance", Html.hidden("id", String.valueOf(a.id)) + "<button class='sm'>Request fingerprints from Home Affairs</button>"));
        else if (q.status.equals("Pending")) sb.append(Html.mu("Asked on " + CitizenPages.WHEN.format(q.requested) + ". Waiting for Home Affairs."));
        else if (q.status.equals("Approved")) sb.append(Html.mu("Home Affairs sent the fingerprints. Fingerprint code: " + Html.esc(String.valueOf(Store.citizen(c.id).fingerprint)) + "."));
        else sb.append(Html.mu("Home Affairs refused. Ask again if the reason was missing.")).append(Html.form("/staff/clearance", Html.hidden("id", String.valueOf(a.id)) + "<button class='sm'>Ask again</button>"));
        sb.append(Html.label("CRIMINAL RECORD CHECK"));
        if (cases.isEmpty()) sb.append(Html.mu("No case is linked to this person. If the fingerprints are in, you can mark it Ready to collect."));
        else { StringBuilder l = new StringBuilder(); for (CaseRec k : cases) l.append(k.caseNo).append(" (").append(k.status).append(") "); sb.append("<div class='err'>! This person has a record: " + Html.esc(l.toString().trim()) + ". Mark the application Not cleared.</div>"); }
        return sb.toString();
    }

    /** Police ask Home Affairs for the fingerprints of a person who applied for a clearance. */
    static Resp clearance(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.PO);
        if (bad != null) return bad;
        Application a;
        try { a = Store.app(Integer.parseInt(r.get("id"))); } catch (Exception e) { a = null; }
        if (a == null || a.dept != Dept.PO || !a.service.equals(CLEARANCE) || !r.post()) return Resp.to("/staff/apps", "err:Nothing to request.");
        AddressRequest q = Store.latestAddressRequest(a.citizenId, a.reference);
        if (q != null && q.status.equals("Pending")) return Resp.to("/staff/apps", "err:Already asked. Home Affairs has not answered yet.");
        Store.addAddressRequest(a.citizenId, a.reference, "Fingerprints for police clearance " + a.reference, o.name);
        return Resp.to("/staff/apps", "ok:Request sent to Home Affairs. You will see the answer on this application.");
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
        boolean issuing = Registry.issuing(st);
        if (a.dept == Dept.PA && issuing) {
            String hold = TravelFlags.holdText(c.id);
            if (hold != null) return Resp.to("/staff/apps", "err:Passport flag: " + hold + ". A passport cannot be issued to this person. Reject the application, or wait until Police lift the flag.");
        }
        boolean oldAge = sv != null && sv.name.equals(Registry.OLD_AGE);
        if (a.dept == Dept.PO && a.service.equals(CLEARANCE) && issuing) {
            AddressRequest q = Store.latestAddressRequest(c.id, a.reference);
            if (q == null || !q.status.equals("Approved")) return Resp.to("/staff/apps", "err:Ask Home Affairs for the fingerprints first and wait until they send them.");
            if (!Store.casesOfPerson(c.id).isEmpty()) return Resp.to("/staff/apps", "err:This person has a criminal record. A clearance cannot be given. Choose Not cleared.");
        }

        if (sv != null && sv.pension && issuing) {
            int age = Policy.age(c, LocalDate.now());
            if (age < Policy.PENSION_AGE) return Resp.to("/staff/apps", "err:Home Affairs shows this citizen is " + age + ". The minimum age is " + Policy.PENSION_AGE + ".");
            String why = Store.benefitProblem(c, Registry.benefitKind(sv.name));
            if (why != null) return Resp.to("/staff/apps", "err:" + why + " Reject the application.");
        }

        // Ready to collect: the citizen is told which office to go to (the one chosen here, or the one nearest to their address).
        String office = "";
        if (st == Status.READY) {
            if (!Policy.collectable(a.service)) return Resp.to("/staff/apps", "err:Nothing is collected for this service. Use Approved or Completed.");
            office = r.get("office");
            if (!Policy.OFFICES.contains(office)) office = Policy.officeFor(c.address);
        }

        // Death certificate issued: Home Affairs tells every department by marking the person as deceased. Payments stop.
        boolean death = sv != null && sv.name.equals("Death Registration") && issuing;
        if (death) {
            String problem = Registry.recordDeath(o, a);
            if (problem != null) return Resp.to("/staff/apps", "err:" + problem);
        }

        // Birth certificate issued: the child gets an ID number and a record in the register.
        String extra = "";
        if (sv != null && sv.name.equals("Birth Registration") && issuing) {
            String idNo = Registry.issueBirth(a);
            if (idNo != null) extra = " Birth certificate ID number: " + idNo + ".";
        }

        if (a.dept == Dept.PA && issuing && sv != null && (sv.name.equals("Passport Renewal") || sv.name.equals("New Passport") || sv.name.equals("Lost or Stolen Passport"))) {
            String pn = Store.issuePassport(c.id, a.id);
            if (pn != null) extra += " New passport number: " + pn + ".";
        }

        if (sv != null && sv.name.equals("First-time ID Card") && issuing) Store.giveIdCard(c.id);

        // Change of names: the register changes, so the new name shows in every service. Connected departments are told.
        if (sv != null && sv.name.equals("Change of Names") && issuing) {
            String nf = Registry.answer(a.id, "Your new name", "New first names").trim(), ns = Registry.answer(a.id, "Your new name", "New surname").trim();
            String full = (nf + " " + ns).trim();
            if (!nf.isEmpty() && !ns.isEmpty() && !full.equals(c.name)) {
                String oldName = c.name;
                Store.rename(c.id, full);
                c.name = full;
                extra += " Name changed from " + oldName + " to " + full + ".";
                String msg = "Name change for ID " + c.id + ": " + oldName + " is now " + full + ". Home Affairs approved it.";
                for (Dept cd : Store.connectedDepts(c.id)) Store.noticeDept(cd, c.id, msg);
                Store.notify(c.id, "Your name is now " + full + " in every Home Affairs service. We told the departments you are connected to.");
            }
        }

        // Traffic issued a vehicle or a licence: keep the record so Police can see it for a case.
        if (issuing && sv != null && sv.dept == Dept.TR) Registry.recordTraffic(a);

        if (st == Status.APPROVED || st == Status.COMPLETED) {
            if (oldAge) Store.enrol(c.id);
            if (sv != null && sv.name.equals(Registry.RETIRE)) Store.enrolPension(c.id);
        }
        if (st == Status.READY) Store.setCollection(a.id, office);
        a.status = st;
        a.note = (r.get("note") + extra).trim();
        Store.saveApp(a);
        String message;
        if (st == Status.READY)
            message = "Your " + a.service + " is ready. Collect it at the " + a.dept.label + " office in " + office + ", Monday to Friday, 08:00 to 16:00. Bring your ID and the reference "
                    + a.reference + "." + (a.note.isEmpty() ? "" : " " + a.note);
        else
            message = "Your " + a.service + " is now " + a.status.label + (a.note.isEmpty() ? "." : ": " + a.note) + (sv != null && sv.pension && st == Status.APPROVED ? " You are now on the " + (oldAge ? "old-age grant" : "retirement pension") + " register." : "");
        Store.notify(a.citizenId, message);
        return Resp.to("/staff/apps", "ok:Status updated and the citizen was notified." + (st == Status.READY ? " They were told to collect at " + office + "." : "")
                + (death ? " Home Affairs has told every department, and payments are stopped." : ""));
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
            pensions = Store.paidTotal("PENSION", "GRANT");
            sb.append(Html.card("<span class='mu'>Service fees collected (income)</span><b style='font-size:22px'>M " + fees + "</b>"))
                    .append(Html.card("<span class='mu'>Grants and pensions paid out</span><b style='font-size:22px'>M " + pensions + "</b>"))
                    .append(Html.card("<span class='mu'>Allocated to departments</span><b style='font-size:22px'>M " + Store.allocatedTotal() + "</b>"));
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
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Digital queue and booked appointments for " + o.dept.label + "."));
        sb.append(Html.label("WAITING IN THE QUEUE"));
        List<Ticket> w = Store.waiting(o.dept);
        for (Ticket t : w) {
            Citizen c = Store.citizen(t.citizenId);
            sb.append(Html.rowAv(av(o, c, 40), t.number, c.name + " · " + t.service, Html.pill("Pending"), null));
        }
        if (w.isEmpty()) sb.append(Html.mu("Nobody is waiting."));
        sb.append(Html.form("/staff/queue", Html.button("Call next")));
        sb.append(Html.label("BOOKED APPOINTMENTS"));
        List<Appointment> booked = Store.upcoming(o.dept);
        for (Appointment a : booked) {
            Citizen c = Store.citizen(a.citizenId);
            sb.append(Html.rowAv(av(o, c, 40), c.name, a.service + " · " + CitizenPages.WHEN.format(a.slot), Html.pill("Booked"), null));
        }
        if (booked.isEmpty()) sb.append(Html.mu("No upcoming appointments."));
        return Resp.html(Html.page("Appointments / Queue", B, "/staff", sb.toString()));
    }

    /** Pension register. Eligibility comes from the date of birth and life status that Home Affairs holds. Government workers do not receive a pension. */
    static Resp pension(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.PE);
        if (bad != null) return bad;
        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.from(today);
        if (r.post()) {
            Citizen c = Store.citizen(r.get("id"));
            if (c == null) return Resp.to("/staff/pension", "err:Citizen not found.");
            String kind = r.get("benefit").equals("PENSION") ? "PENSION" : "GRANT";
            if (r.get("act").equals("invite")) {
                if (!Policy.pensionAge(c, today) || !c.alive) return Resp.to("/staff/pension", "err:Not eligible.");
                Store.notify(c.id, "You are " + Policy.age(c, today) + ". Open My Pension to apply for the old-age grant or the retirement pension.");
                return Resp.to("/staff/pension", "ok:Invitation sent to " + c.name + ".");
            }
            boolean registered = kind.equals("GRANT") ? Store.enrolled(c.id) : Store.pensionEnrolled(c.id);
            if (!registered || Store.pensionPayment(c.id, ym, kind) != null) return Resp.to("/staff/pension", "err:Cannot send this payment.");
            String why = Store.benefitProblem(c, kind);
            if (why != null) return Resp.to("/staff/pension", "err:" + why);
            Store.addPayment(c.id, kind, Policy.benefitLabel(kind) + " " + ym, Policy.benefitAmount(kind), ym.toString(), false, null, 0);
            Store.notify(c.id, "Your " + Policy.benefitLabel(kind).toLowerCase() + " for " + ym + " was sent to Finance for payment.");
            return Resp.to("/staff/pension", "ok:Sent to Finance.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Payday: " + Policy.payday(ym) + ". Nobody has to queue.")
                + Html.card("<b>How benefits work</b><span class='mu'>" + Html.esc(Policy.PENSION_RULE) + "</span>"));
        for (String kind : new String[]{"GRANT", "PENSION"}) {
            sb.append(Html.label(Policy.benefitLabel(kind).toUpperCase() + " REGISTER (M " + Policy.benefitAmount(kind) + " A MONTH)"));
            List<Citizen> reg = kind.equals("GRANT") ? Store.enrolledList() : Store.pensionList();
            int shown = 0;
            for (Citizen c : reg) {
                if (!c.alive) continue;
                shown++;
                Payment p = Store.pensionPayment(c.id, ym, kind);
                String why = Store.benefitProblem(c, kind);
                String send = p == null && why == null ? Html.form("/staff/pension", Html.hidden("id", c.id) + Html.hidden("act", "send") + Html.hidden("benefit", kind) + "<button class='sm'>Send to Finance</button>") : "";
                String state = why != null ? "Not allowed" : Policy.pensionState(p, today);
                sb.append("<div class='rw'>").append(av(o, c, 40)).append("<div><b>").append(Html.esc(c.name)).append("</b><small>Age ").append(Policy.age(c, today))
                        .append(" · ").append(Store.govtWorker(c.id) ? "Government employee" : "Private sector").append(why != null ? " · " + Html.esc(why) : "")
                        .append("</small></div>").append(Html.pill(state)).append(send).append("</div>");
            }
            if (shown == 0) sb.append(Html.mu("Nobody on this register."));
        }
        sb.append(Html.label("RETIRED GOVERNMENT EMPLOYEES (CHOOSE GRANT OR PENSION)"));
        int gw = 0;
        for (Citizen c : Store.govtWorkers()) {
            if (!c.alive || !Policy.pensionAge(c, today)) continue;
            gw++;
            String ch = Store.benefitChoice(c.id);
            sb.append(Html.rowAv(av(o, c, 40), c.name, "Age " + Policy.age(c, today) + " · " + Store.govtMinistry(c.id), Html.pill(ch == null ? "Pending" : Policy.benefitLabel(ch)), null));
        }
        if (gw == 0) sb.append(Html.mu("None."));
        sb.append(Html.label("AGE " + Policy.PENSION_AGE + "+ BUT NOT YET APPLIED"));
        for (Citizen c : Store.allCitizens())
            if (c.alive && Policy.pensionAge(c, today) && !Store.enrolled(c.id) && !Store.pensionEnrolled(c.id)) {
                String inv = c.hasAccount() ? Html.form("/staff/pension", Html.hidden("id", c.id) + Html.hidden("act", "invite") + "<button class='sm'>Invite to apply</button>") : "";
                sb.append("<div class='rw'>").append(av(o, c, 40)).append("<div><b>").append(Html.esc(c.name)).append("</b><small>Age ").append(Policy.age(c, today)).append("</small></div>").append(inv).append("</div>");
            }
        sb.append(Html.label("TURNING " + Policy.PENSION_AGE + " WITHIN 60 DAYS"));
        for (Citizen c : Store.allCitizens())
            if (c.alive && !Policy.pensionAge(c, today) && !Policy.turnsPensionAge(c).isAfter(today.plusDays(60)))
                sb.append(Html.rowAv(av(o, c, 40), c.name, "Turns " + Policy.PENSION_AGE + " on " + Policy.turnsPensionAge(c), Html.pill("Pending"), null));
        sb.append(Html.label("STOPPED (HOME AFFAIRS REPORTS DEATH)"));
        for (Citizen c : Store.enrolledList()) if (!c.alive) sb.append(Html.rowAv(av(o, c, 40), c.name, "Old-age grant stopped automatically", Html.pill("Deceased"), null));
        for (Citizen c : Store.pensionList()) if (!c.alive) sb.append(Html.rowAv(av(o, c, 40), c.name, "Retirement pension stopped automatically", Html.pill("Deceased"), null));
        return Resp.html(Html.page("Pension Register", B, "/staff", sb.toString()));
    }

    static Resp payments(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.FI);
        if (bad != null) return bad;
        if (r.post()) {
            Payment p;
            try { p = Store.payment(Integer.parseInt(r.get("id"))); } catch (Exception e) { p = null; }
            if (p == null || p.paid || !(p.kind.equals("PENSION") || p.kind.equals("GRANT"))) return Resp.to("/staff/payments", "err:Nothing to pay.");
            Citizen pc = Store.citizen(p.citizenId);
            String why = Store.benefitProblem(pc, p.kind);
            if (why != null) return Resp.to("/staff/payments", "err:" + why);
            String receipt = Store.nextReceipt();
            int owed = Store.taxOwed(p.citizenId), held = Math.min(owed, p.amount);
            if (held > 0) { Store.setTaxOwed(p.citizenId, owed - held); Store.lraTransfer(p.citizenId, held, Policy.benefitLabel(p.kind) + " " + p.month); }
            Store.markPaid(p.id, receipt);
            Store.notify(p.citizenId, "Your " + Policy.benefitLabel(p.kind).toLowerCase() + " payment of M " + p.amount + " for " + p.month + " has been paid. Receipt " + receipt + "."
                    + (held > 0 ? " LRA says you owe tax. M " + held + " was taken and sent to LRA. You receive M " + (p.amount - held) + "." : ""));
            return Resp.to("/staff/payments", "ok:Marked as paid and the citizen was notified." + (held > 0 ? " M " + held + " was held back and sent to LRA for unpaid tax." : ""));
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Grant and pension payments sent by Pensions. Before paying, Finance checks with Home Affairs that the person is alive, and checks their employment: a government employee gets only the benefit they chose."));
        List<Payment> l = Store.benefitPayments();
        for (Payment p : l) {
            Citizen c = Store.citizen(p.citizenId);
            String why = p.paid ? null : Store.benefitProblem(c, p.kind);
            boolean stopped = why != null;
            String act = p.paid || stopped ? "" : Html.form("/staff/payments", Html.hidden("id", String.valueOf(p.id)) + "<button class='sm'>Mark paid</button>");
            sb.append("<div class='rw'>").append(av(o, c, 40)).append("<div><b>").append(Html.esc(c.name)).append("</b><small>ID ").append(c.id).append(" · ").append(Html.esc(Policy.benefitLabel(p.kind))).append(" · M ").append(p.amount).append(" · ").append(p.month)
                    .append("<br>Home Affairs says: ").append(c.alive ? "alive" : "deceased").append(" · Pensions says employment: ").append(Store.govtWorker(c.id) ? "government" + (Store.benefitChoice(c.id) == null ? " (no choice yet)" : ", chose the " + Policy.benefitLabel(Store.benefitChoice(c.id)).toLowerCase()) : "private sector")
                    .append(stopped ? "<br>Stopped: " + Html.esc(why) : "").append("</small></div>").append(Html.pill(p.paid ? "Paid" : stopped ? "Rejected" : "Pending")).append(act).append("</div>");
        }
        if (l.isEmpty()) sb.append(Html.mu("No payments sent yet."));
        return Resp.html(Html.page("Grant & Pension Payments", B, "/staff", sb.toString()));
    }

    /** Financial records: income, expenses and the money Finance allocates to the other departments. */
    static Resp records(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.FI);
        if (bad != null) return bad;
        if (r.post() && r.get("act").equals("tax")) {
            Citizen tc = Store.citizen(r.get("id"));
            String t = r.get("owed").replace(" ", "");
            if (tc == null) return Resp.to("/staff/records", "err:That ID number is not in the register.");
            if (!t.matches("[0-9]{1,8}")) return Resp.to("/staff/records", "err:Wrong amount. Write the tax LRA says is owed in Maloti, digits only. Write 0 if nothing is owed.");
            Store.setTaxOwed(tc.id, Integer.parseInt(t));
            return Resp.to("/staff/records", "ok:Saved. " + tc.name + " owes M " + Integer.parseInt(t) + " to LRA.");
        }
        if (r.post()) {
            Dept d;
            try { d = Dept.valueOf(r.get("dept")); } catch (Exception e) { return Resp.to("/staff/records", "err:Choose a department."); }
            if (d == Dept.FI) return Resp.to("/staff/records", "err:Choose another department. Finance does not allocate money to itself.");
            String purpose = r.get("purpose").trim();
            String amountText = r.get("amount").replace(" ", "");
            if (!amountText.matches("[0-9]{1,8}") || Integer.parseInt(amountText) <= 0) return Resp.to("/staff/records", "err:Wrong amount. Write the amount in Maloti, digits only. Example: 5000.");
            if (purpose.length() < 3) return Resp.to("/staff/records", "err:Write what the money is for.");
            if (purpose.length() > 120) purpose = purpose.substring(0, 120);
            int amount = Integer.parseInt(amountText);
            Store.addAllocation(d, amount, purpose, o.name);
            return Resp.to("/staff/records", "ok:M " + amount + " allocated to " + d.label + ".");
        }
        int income = 0;
        for (Payment p : Store.payments("FEE")) if (p.paid) income += p.amount;
        int grants = Store.paidTotal("GRANT"), pensions = Store.paidTotal("PENSION"), allocated = Store.allocatedTotal();
        int expenses = grants + pensions + allocated, balance = income - expenses;
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("The financial records of the government services: all money that comes in and all money that goes out. Finance does not collect tax. It receives service fees and allocates money to the other departments."));
        sb.append(Html.label("INCOME"));
        sb.append(Html.card("<span class='mu'>Service fees received</span><b style='font-size:22px'>M " + income + "</b>"));
        for (String[] row : Store.incomeByDept()) {
            String label; try { label = Dept.valueOf(row[0]).label; } catch (Exception e) { label = row[0]; }
            sb.append(Html.row(label, "Fees paid for this department's services", "<span class='pl m'>M " + row[1] + "</span>", null));
        }
        sb.append(Html.label("EXPENSES"));
        sb.append(Html.row("Old-age grants paid", "Paid to citizens", "<span class='pl m'>M " + grants + "</span>", null));
        sb.append(Html.row("Retirement pensions paid", "Paid to citizens", "<span class='pl m'>M " + pensions + "</span>", null));
        sb.append(Html.row("Allocated to departments", "Money handed to the other departments", "<span class='pl m'>M " + allocated + "</span>", null));
        sb.append(Html.card("<span class='mu'>Total expenses</span><b style='font-size:22px'>M " + expenses + "</b>"));
        sb.append(Html.card("<span class='mu'>Balance (income minus expenses)</span><b style='font-size:22px'>M " + balance + "</b>"));
        if (balance < 0) sb.append("<div class='err'>! More money has gone out than came in. The balance is M " + balance + ".</div>");

        List<String> depts = new ArrayList<>();
        depts.add("|Choose a department");
        for (Dept d : Dept.values()) if (d != Dept.FI) depts.add(d.name() + "|" + d.label);
        sb.append(Html.label("TAX HELD BACK FOR LRA (FROM GRANTS AND PENSIONS)"));
        sb.append(Html.card("<span class='mu'>Sent to LRA so far</span><b style='font-size:22px'>M " + Store.lraTotal() + "</b>"));
        for (String[] t : Store.lraTransfers()) sb.append(Html.row(String.valueOf(t[1]), "ID " + t[0] + " · " + t[3], "<span class='pl m'>M " + t[2] + "</span>", null));
        for (String[] t : Store.taxOwing()) sb.append(Html.row(String.valueOf(t[1]), "LRA says this person owes tax. It is taken from the next payment.", "<span class='pl m'>M " + t[2] + "</span>", null));
        sb.append(Html.form("/staff/records", Html.hidden("act", "tax") + Html.field("ID number", "id", "text", "") + Html.field("Tax LRA says is owed (Maloti)", "owed", "text", "") + "<br>" + Html.button("Save LRA notice")));
        sb.append(Html.label("ALLOCATE MONEY TO A DEPARTMENT"));
        sb.append(Html.form("/staff/records", Html.selectOf("Department", "dept", "", depts.toArray(new String[0])) + Html.field("Amount (Maloti)", "amount", "text", "")
                + Html.field("What is it for?", "purpose", "text", "") + "<br>" + Html.button("Allocate")));
        sb.append(Html.label("ALLOCATIONS"));
        List<String[]> al = Store.allocations(30);
        for (String[] a : al) {
            String label; try { label = Dept.valueOf(a[0]).label; } catch (Exception e) { label = a[0]; }
            sb.append(Html.row(label + " · M " + a[1], a[2] + " · " + a[3] + " · " + a[4].substring(0, 10), null, null));
        }
        if (al.isEmpty()) sb.append(Html.mu("Nothing allocated yet."));
        return Resp.html(Html.page("Financial Records", B, "/staff", sb.toString()));
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
            sb.append(Html.rowAv(av(o, c, 40), p.ref, c.name + " · ID " + c.id + " · M " + p.amount + " · " + p.receipt, Html.pill("Paid"), null));
        }
        if (l.isEmpty()) sb.append(Html.mu("No fees paid yet."));
        return Resp.html(Html.page("Fees & Receipts", B, "/staff", Html.intro("Fees paid for services in every department. Total collected: M " + total) + sb));
    }

    /** People who work for the government do not receive a pension. Finance and Pensions keep this list. */
    static Resp govt(Req r) {
        Official o = r.staff();
        if (o == null) return needLogin();
        if (o.dept != Dept.FI && o.dept != Dept.PE) return Resp.to("/staff", "err:Finance and Pensions staff only.");
        boolean owner = o.dept == Dept.PE; // Pensions keeps this record. Finance can only read it.
        if (r.post()) {
            if (!owner) return Resp.to("/staff/govt", "err:Only Pensions can change the government workers list. Finance can read it.");
            Citizen c = Store.citizen(r.get("id"));
            if (c == null) return Resp.to("/staff/govt", "err:No citizen with that ID number in the register.");
            if (r.get("act").equals("remove")) {
                Store.clearGovtWorker(c.id);
                Store.logAccess(c.id, o.dept, o.name, "Government worker record", "Removed from the government workers list");
                return Resp.to("/staff/govt", "ok:" + c.name + " is no longer marked as a government worker.");
            }
            String ministry = r.get("ministry").trim();
            if (ministry.isEmpty()) ministry = "Government of Lesotho";
            if (ministry.length() > 80) ministry = ministry.substring(0, 80);
            Store.setGovtWorker(c.id, ministry);
            Store.logAccess(c.id, o.dept, o.name, "Government worker record", "Marked as a government worker: " + ministry);
            Store.notify(c.id, o.dept.label + " recorded that you work for the government (" + ministry + "). When you retire you choose either the old-age grant or the retirement pension. You cannot receive both.");
            return Resp.to("/staff/govt", "ok:" + c.name + " is marked as a government worker. They must choose the grant or the pension, not both.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("People who work or worked for the government get only one of the two benefits: the old-age grant or the retirement pension. Pensions keeps this list, and Finance checks it before paying.")
                + Html.card("<b>How benefits work</b><span class='mu'>" + Html.esc(Policy.PENSION_RULE) + "</span>"));
        if (owner) sb.append(Html.form("/staff/govt", Html.hidden("act", "add") + Html.field("ID number", "id", "text", "") + Html.field("Ministry or department (optional)", "ministry", "text", "")
                + "<br>" + Html.button("Mark as government worker")));
        else sb.append(Html.card("<b>Read only</b><span class='mu'>Pensions keeps this list. Finance uses it to check who may receive the grant, the pension or both before paying.</span>"));
        sb.append(Html.label("GOVERNMENT WORKERS"));
        List<Citizen> l = Store.govtWorkers();
        for (Citizen c : l)
            sb.append("<div class='rw'>").append(av(o, c, 40)).append("<div><b>").append(Html.esc(c.name)).append("</b><small>ID ").append(c.id).append(" · ").append(Html.esc(Store.govtMinistry(c.id))).append(Store.benefitChoice(c.id) == null ? "" : " · chose the " + Policy.benefitLabel(Store.benefitChoice(c.id)).toLowerCase())
                    .append("</small></div>").append(owner ? Html.form("/staff/govt", Html.hidden("id", c.id) + Html.hidden("act", "remove") + "<button class='sm'>Remove</button>") : "").append("</div>");
        if (l.isEmpty()) sb.append(Html.mu("Nobody is marked yet."));
        return Resp.html(Html.page("Government Workers", B, "/staff", sb.toString()));
    }

    // ================================================================== TRAFFIC
    /**
     * Tests and inspections. The citizen books an appointment and comes to the office. Here the officer records the result.
     * A pass issues the learner's licence, the driving licence or the roadworthy certificate straight to the citizen.
     */
    static Resp issue(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.TR);
        if (bad != null) return bad;
        String back = "/staff/issue";
        if (r.post()) {
            Citizen c = Store.citizen(r.get("id"));
            String type = r.get("type"), result = r.get("result"), klass = r.get("klass"), note = r.get("note");
            if (c == null) return Resp.to(back, "err:That ID number is not in the register. Check the 11 digits.");
            if (!c.alive) return Resp.to(back, "err:Home Affairs records this person as deceased.");
            if (!Certificates.TYPES.contains(type)) return Resp.to(back, "err:Choose what was tested.");
            boolean licence = !type.equals(Certificates.ROADWORTHY);
            String detail;
            if (licence) {
                if (!Certificates.CLASSES.contains(klass)) return Resp.to(back, "err:Choose the class of vehicle.");
                detail = "Class: " + klass;
            } else {
                String plate = r.get("plate").toUpperCase(), make = r.get("make"), model = r.get("model");
                if (plate.length() < 3 || plate.length() > 12 || make.isEmpty() || model.isEmpty()) return Resp.to(back, "err:Write the number plate, make and model of the vehicle.");
                detail = "Number plate: " + plate + " | Make: " + make + " | Model: " + model;
            }
            if (result.equals("Failed")) {
                if (note.length() < 3) return Resp.to(back, "err:Write why it failed, so the citizen knows what to fix or practise.");
                Store.notify(c.id, "Your " + type.toLowerCase() + (licence ? " test" : " inspection") + " did not pass. " + note + " Book another appointment at Traffic Services when you are ready.");
                Store.logAccess(c.id, o.dept, o.name, "Name, ID number", type + " not passed");
                return Resp.to(back, "ok:Result saved. " + c.name + " was told and can book again.");
            }
            if (!result.equals("Passed")) return Resp.to(back, "err:Choose Passed or Failed.");
            if (!r.get("paid").equals("yes")) return Resp.to(back, "err:Collect the fee (M " + Certificates.fee(type) + ") at the office first, then choose Yes.");
            int age = Policy.age(c, LocalDate.now());
            if (type.equals(Certificates.LEARNER) && age < 16) return Resp.to(back, "err:A learner's licence is for people aged 16 or older. This person is " + age + ".");
            if (type.equals(Certificates.DRIVING)) {
                if (age < 18) return Resp.to(back, "err:A driving licence is for people aged 18 or older. This person is " + age + ".");
                if (!Certificates.hasValid(c.id, Certificates.LEARNER)) return Resp.to(back, "err:This person has no valid learner's licence. They must pass the learner's test first.");
            }
            String[] cert = Certificates.issue(c.id, type, detail, o.name);
            Store.addTraffic(c.id, licence ? (type.equals(Certificates.LEARNER) ? "Learner's licence" : "Driving licence") : "Roadworthy certificate",
                    cert[3] + " | " + detail.replace(" | ", " · ") + " · valid until " + cert[6], "Valid");
            Store.logAccess(c.id, o.dept, o.name, "Name, ID number", type + " issued " + cert[3]);
            Store.notify(c.id, "You passed. Traffic Services issued your " + type + ", number " + cert[3] + ". Open it under Traffic Services, My certificates. You can print it. Anyone can check it with the code " + Certificates.showCode(cert[8]) + ".");
            return Resp.to(back, "ok:" + type + " " + cert[3] + " issued to " + c.name + ". The citizen can open and print it.");
        }
        String pid = r.get("id");
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("The citizen books a test or an inspection and comes to the office. Record the result here. When they pass, the learner's licence, driving licence or roadworthy certificate is issued to them straight away."));
        String klassOpts[] = Certificates.CLASSES.toArray(new String[0]);
        sb.append(Html.form("/staff/issue", Html.field("Citizen's ID number", "id", "text", pid)
                + Html.selectOf("What was tested?", "type", r.get("type"), Certificates.TYPES.toArray(new String[0]))
                + Html.selectOf("Result", "result", "", "Passed", "Failed")
                + Html.selectOf("Class of vehicle (licences only)", "klass", "", klassOpts)
                + Html.field("Number plate (roadworthy only)", "plate", "text", "") + Html.field("Make (roadworthy only)", "make", "text", "") + Html.field("Model (roadworthy only)", "model", "text", "")
                + Html.select("Fee paid at the office? (Learner's M 50, Driving M 150, Roadworthy M 80)", "paid", "no|No", "yes|Yes")
                + Html.field("Note (required if failed)", "note", "text", "") + "<br>" + Html.button("Save result")));
        sb.append(Html.label("BOOKED TESTS AND INSPECTIONS"));
        int n = 0;
        for (Appointment ap : Store.upcoming(Dept.TR)) {
            String s = ap.service == null ? "" : ap.service.toLowerCase();
            if (!(s.contains("learner") || s.contains("roadworthy") || s.equals("driving test"))) continue;
            n++;
            Citizen c = Store.citizen(ap.citizenId);
            String t = s.contains("learner") ? Certificates.LEARNER : s.contains("roadworthy") ? Certificates.ROADWORTHY : Certificates.DRIVING;
            sb.append(Html.rowAv(av(o, c, 40), c.name, ap.service + " · " + CitizenPages.WHEN.format(ap.slot) + " · ID " + c.id,
                    "<a class='sm' href='/staff/issue?id=" + c.id + "&type=" + t.replace(" ", "%20").replace("'", "%27") + "'>Record result</a>", null));
        }
        if (n == 0) sb.append(Html.mu("No tests or inspections are booked."));
        sb.append(Html.label("RECENTLY ISSUED"));
        List<String[]> recent = Db.list("select * from certificates order by id desc limit 15", rs -> new String[]{rs.getString("citizen_id"), rs.getString("type"), rs.getString("number"), rs.getString("issued")});
        for (String[] c : recent) { Citizen h = Store.citizen(c[0]); sb.append(Html.row(c[1] + " " + c[2], (h == null ? "" : h.name) + " · " + c[3], Html.pill("Issued"), null)); }
        if (recent.isEmpty()) sb.append(Html.mu("Nothing issued yet."));
        return Resp.html(Html.page("Tests & Certificates", B, "/staff", sb.toString()));
    }

    /** Offences and fines: Traffic records the offence, the citizen is told, and the fine is marked paid when the citizen pays at the office. */
    static Resp fines(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.TR);
        if (bad != null) return bad;
        if (r.post()) {
            if (r.get("act").equals("paid")) {
                String[] f;
                try { f = Store.fine(Integer.parseInt(r.get("id"))); } catch (Exception e) { f = null; }
                if (f == null || !f[7].equals("Unpaid")) return Resp.to("/staff/fines", "err:Nothing to mark as paid.");
                String receipt = Store.nextReceipt();
                Store.payFine(Integer.parseInt(f[0]), receipt);
                Store.notify(f[1], "Your fine of M " + f[3] + " for " + f[2] + " is paid. Receipt " + receipt + ".");
                return Resp.to("/staff/fines", "ok:Marked as paid. Receipt " + receipt + ".");
            }
            Citizen c = Store.citizen(r.get("id"));
            String offence = r.get("offence"), place = r.get("place"), amt = r.get("amount").replace(" ", "");
            if (c == null) return Resp.to("/staff/fines", "err:That ID number is not in the register. Check the 11 digits.");
            if (!Store.OFFENCES.contains(offence)) return Resp.to("/staff/fines", "err:Choose the offence.");
            if (!amt.matches("[0-9]{1,6}") || Integer.parseInt(amt) <= 0) return Resp.to("/staff/fines", "err:Wrong amount. Write the fine in Maloti, digits only. Example: 300.");
            if (place.length() < 3) return Resp.to("/staff/fines", "err:Write where it happened.");
            if (place.length() > 100) place = place.substring(0, 100);
            Store.addFine(c.id, offence, Integer.parseInt(amt), place, LocalDate.now().toString(), o.name);
            Store.logAccess(c.id, o.dept, o.name, "Name, ID number", "Traffic fine: " + offence);
            Store.notify(c.id, "Traffic Services recorded a fine of M " + Integer.parseInt(amt) + " for " + offence + " at " + place + ". Pay it at the Traffic office.");
            return Resp.to("/staff/fines", "ok:Fine recorded for " + c.name + " and the citizen was told.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Record a traffic offence and its fine. The citizen is told, sees it under Traffic Services, and pays at the office."));
        sb.append(Html.form("/staff/fines", Html.field("Driver's ID number", "id", "text", "") + Html.select("Offence", "offence", Store.OFFENCES.toArray(new String[0]))
                + Html.field("Fine (Maloti)", "amount", "text", "") + Html.field("Where it happened", "place", "text", "") + "<br>" + Html.button("Record fine")));
        sb.append(Html.label("RECENT FINES"));
        List<String[]> l = Store.recentFines();
        for (String[] f : l) {
            Citizen c = Store.citizen(f[1]);
            String act = f[7].equals("Unpaid") ? Html.form("/staff/fines", Html.hidden("id", f[0]) + Html.hidden("act", "paid") + "<button class='sm'>Mark paid</button>") : "";
            sb.append("<div class='rw'>").append(av(o, c, 40)).append("<div><b>").append(Html.esc(c.name)).append("</b><small>").append(Html.esc(f[2])).append(" · M ").append(f[3]).append(" · ")
                    .append(Html.esc(f[4])).append(" · ").append(f[5]).append(f[8] == null || f[8].isEmpty() ? "" : " · receipt " + f[8]).append("</small></div>").append(Html.pill(f[7])).append(act).append("</div>");
        }
        if (l.isEmpty()) sb.append(Html.mu("No fines recorded yet."));
        return Resp.html(Html.page("Traffic Offences & Fines", B, "/staff", sb.toString()));
    }

    /** The traffic register: driving licences, vehicles, roadworthy certificates and fines of one person. Search by ID number, name or number plate. */
    static Resp trafficRecords(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.TR);
        if (bad != null) return bad;
        String q = r.get("q");
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Look up the driving licence, vehicles, roadworthy certificates and fines of a person. Search by ID number, name or number plate."));
        sb.append("<form method='get' action='/staff/traffic'>" + Html.field("ID number, name or number plate", "q", "text", q) + "<br>" + Html.button("Search") + "</form>");
        if (!q.isEmpty()) {
            List<String> ids = Store.searchTrafficPeople(q);
            if (ids.isEmpty()) sb.append("<div class='err'>! Nobody matches that. Check the ID number, name or number plate.</div>");
            for (String id : ids) {
                Citizen c = Store.citizen(id);
                if (c == null) continue;
                Store.logAccess(c.id, o.dept, o.name, "Name, ID number, traffic records", "Traffic register search");
                StringBuilder rec = new StringBuilder();
                for (TrafficRec t : Store.trafficOf(c.id)) rec.append(Html.row(t.kind, t.detail, Html.pill(t.status), null));
                if (rec.length() == 0) rec.append(Html.mu("No licence or vehicle on the register."));
                StringBuilder fn = new StringBuilder();
                for (String[] f : Store.finesOf(c.id)) fn.append(Html.row(f[2], "M " + f[3] + " · " + f[5], Html.pill(f[7]), null));
                sb.append(Html.card("<div class='two'>" + av(o, c, 56) + "<div><b>" + Html.esc(c.name) + "</b><span class='mu'>ID " + c.id + " · born " + c.dob + (c.alive ? "" : " · Home Affairs: deceased") + "</span></div></div>"
                        + Html.label("LICENCE, VEHICLES AND CERTIFICATES") + rec + (fn.length() > 0 ? Html.label("FINES") + fn : "")));
            }
        }
        return Resp.html(Html.page("Licence & Vehicle Register", B, "/staff", sb.toString()));
    }

    // ================================================================== POLICE
    private static String caseRow(CaseRec cs) {
        return Html.row(cs.caseNo + " · " + cs.title, cs.status + " · updated " + CitizenPages.WHEN.format(cs.updated), Html.pill(cs.status), "/staff/case?no=" + cs.caseNo);
    }

    /**
     * Police search: find a person by ID number, name or fingerprint code for an open case.
     * The person's record opens only for a case, and every look is logged for the citizen.
     */
    static Resp police(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.PO);
        if (bad != null) return bad;

        StringBuilder result = new StringBuilder();
        String name = r.get("name"), id = r.get("id"), fp = r.get("fp"), caseNo = r.get("case_no");
        if (r.post()) {
            CaseRec cs = Store.caseByNo(caseNo);
            if (name.isEmpty() && id.isEmpty() && fp.isEmpty()) result.append("<div class='err'>! Fill in a name, an ID number or a fingerprint code.</div>");
            else if (cs == null || cs.status.equals("Closed")) result.append("<div class='err'>! Pick an open case. Open one under Police Cases if you have none.</div>");
            else {
                List<Citizen> found = Store.search(name, id, "", fp);
                if (found.isEmpty()) result.append("<div class='err'>! No match in the Home Affairs register.</div>");
                for (Citizen c : found) {
                    Store.logAccess(c.id, o.dept, o.name, "Name, ID number, Address, Photo, Fingerprint match", "Case " + cs.caseNo);
                    result.append(Html.card("<div class='two'>" + av(o, c, 72) + "<div><b style='font-size:18px'>" + Html.esc(c.name) + "</b><span class='mu'>ID " + Html.esc(c.id) + "<br>Address: " + Html.esc(c.address == null || c.address.isEmpty() ? "not on record" : c.address) + "<br>"
                            + Html.esc(Policy.value(c, Field.FP, !fp.isEmpty())) + "</span></div></div>"
                            + Html.form("/staff/person", Html.hidden("id", c.id) + Html.hidden("case_no", cs.caseNo) + Html.hidden("act", "open") + Html.button("Open record"))));
                }
            }
        }
        List<String> opts = new ArrayList<>();
        opts.add("|Choose an open case");
        for (CaseRec cs : Store.openCases()) opts.add(cs.caseNo + "|" + cs.caseNo + " · " + cs.title);
        String form = Html.form("/staff/police", Html.selectOf("Case", "case_no", caseNo, opts.toArray(new String[0]))
                + Html.field("ID number", "id", "text", id) + Html.field("Name", "name", "text", name)
                + Html.field("Fingerprint code (for example FP-1001)", "fp", "text", fp) + "<br>" + Html.button("Search"));

        StringBuilder mine = new StringBuilder(Html.label("MY ADDRESS REQUESTS"));
        List<AddressRequest> reqs = Store.addressRequestsBy(o.name);
        for (AddressRequest q : reqs) {
            Citizen c = Store.citizen(q.citizenId);
            String addr = q.status.equals("Approved") ? " · Address: " + (c.address == null || c.address.isEmpty() ? "not on record" : c.address) : "";
            mine.append(Html.rowAv(av(o, c, 40), c.name + " · case " + q.caseNo, q.reason + addr, Html.pill(q.status), null));
        }
        if (reqs.isEmpty()) mine.append(Html.mu("No requests yet."));
        return Resp.html(Html.page("Criminal Search", B, "/staff", Html.banner(r.get("m"))
                + Html.intro("Search by ID number, name or fingerprint code. Pick the open case you are working on. Every look is logged.") + form + result + mine));
    }

    /** List of police cases, and the form to open a new one. */
    static Resp cases(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.PO);
        if (bad != null) return bad;
        if (r.post()) {
            String title = r.get("title").trim(), details = r.get("details").trim(), id = Store.norm(r.get("id"));
            if (title.length() < 3) return Resp.to("/staff/cases", "err:Write a short case title.");
            if (title.length() > 100) title = title.substring(0, 100);
            if (details.length() > 400) details = details.substring(0, 400);
            Citizen c = null;
            if (!id.isEmpty()) {
                c = Store.citizen(id);
                if (c == null) return Resp.to("/staff/cases", "err:No citizen with that ID number in the register.");
            }
            CaseRec cs = Store.addCase(title, details, c == null ? null : c.id, o.name);
            if (c != null) Store.logAccess(c.id, o.dept, o.name, "Name, ID number", "Linked to case " + cs.caseNo);
            return Resp.to("/staff/case?no=" + cs.caseNo, "ok:Case opened. Case number " + cs.caseNo + ".");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Record and follow criminal cases. Open a case, add updates and close it when it is finished."));
        sb.append(Html.label("OPEN A NEW CASE")).append(Html.form("/staff/cases", Html.field("Case title", "title", "text", "") + Html.field("Details", "details", "text", "")
                + Html.field("ID number of the person (optional)", "id", "text", "") + "<br>" + Html.button("Create case")));
        List<CaseRec> all = Store.cases();
        sb.append(Html.label("OPEN CASES"));
        int open = 0;
        for (CaseRec cs : all) if (!cs.status.equals("Closed")) { sb.append(caseRow(cs)); open++; }
        if (open == 0) sb.append(Html.mu("No cases yet."));
        boolean anyClosed = false;
        for (CaseRec cs : all) if (cs.status.equals("Closed")) {
            if (!anyClosed) { sb.append(Html.label("CLOSED CASES")); anyClosed = true; }
            sb.append(caseRow(cs));
        }
        return Resp.html(Html.page("Police Cases", B, "/staff", sb.toString()));
    }

    /** One police case: details, the person in it, and its history. Officers add updates and change the status here. */
    static Resp caseView(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.PO);
        if (bad != null) return bad;
        String no = r.get("no");
        CaseRec cs = Store.caseByNo(no);
        if (cs == null) return Resp.to("/staff/cases", "err:Case not found.");
        String here = "/staff/case?no=" + cs.caseNo;
        if (r.post()) {
            if (r.get("act").equals("link")) {
                Citizen c = Store.citizen(r.get("id"));
                if (c == null) return Resp.to(here, "err:No citizen with that ID number in the register.");
                Store.linkCase(cs.caseNo, c.id);
                Store.addCaseNote(cs.caseNo, o.name, "Linked person: " + c.name + " (ID " + c.id + ")");
                Store.logAccess(c.id, o.dept, o.name, "Name, ID number", "Linked to case " + cs.caseNo);
                return Resp.to(here, "ok:Person linked to the case.");
            }
            String status = r.get("status"), note = r.get("note").trim();
            if (!CASE_STATUSES.contains(status)) return Resp.to(here, "err:Unknown status.");
            if (note.length() > 400) note = note.substring(0, 400);
            if (note.isEmpty() && status.equals(cs.status)) return Resp.to(here, "err:Write a note or change the status.");
            if (!status.equals(cs.status)) {
                Store.updateCase(cs.caseNo, status);
                Store.addCaseNote(cs.caseNo, o.name, "Status changed to " + status + (note.isEmpty() ? "" : ". " + note));
            } else Store.addCaseNote(cs.caseNo, o.name, note);
            return Resp.to(here, "ok:Case updated.");
        }

        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")));
        sb.append(Html.card("<b style='font-size:17px'>" + Html.esc(cs.title) + "</b>" + Html.pill(cs.status)
                + "<span class='mu'>" + Html.esc(cs.caseNo) + " · opened by " + Html.esc(cs.officer) + " · " + CitizenPages.WHEN.format(cs.opened)
                + (cs.details.isEmpty() ? "" : "<br>" + Html.esc(cs.details)) + "</span>"));

        sb.append(Html.label("PERSON IN THIS CASE"));
        Citizen pc = cs.citizenId == null ? null : Store.citizen(cs.citizenId);
        if (pc != null) {
            sb.append(Html.card("<div class='two'>" + av(o, pc, 56) + "<div><b>" + Html.esc(pc.name) + "</b><span class='mu'>ID " + Html.esc(pc.id) + "</span></div></div>"
                    + Html.form("/staff/person", Html.hidden("id", pc.id) + Html.hidden("case_no", cs.caseNo) + Html.hidden("act", "open") + Html.button("Open person record"))));
        } else {
            sb.append(Html.mu("No person is linked to this case yet."))
                    .append(Html.form("/staff/case", Html.hidden("no", cs.caseNo) + Html.hidden("act", "link") + Html.field("ID number", "id", "text", "") + "<br>" + Html.button("Link person")));
        }

        sb.append(Html.label("UPDATE THE CASE")).append(Html.form("/staff/case", Html.hidden("no", cs.caseNo) + Html.hidden("act", "update")
                + Html.selectOf("New status", "status", cs.status, CASE_STATUSES.toArray(new String[0])) + Html.field("Update note", "note", "text", "")
                + "<br>" + Html.button("Save update")));

        sb.append(Html.label("CASE HISTORY"));
        List<CaseNote> notes = Store.notesOfCase(cs.caseNo);
        for (CaseNote n : notes) sb.append(Html.row(n.officer + " · " + CitizenPages.WHEN.format(n.time), n.text, null, null));
        if (notes.isEmpty()) sb.append(Html.mu("No updates yet."));
        return Resp.html(Html.page("Case " + cs.caseNo, "Police Cases", "/staff/cases", sb.toString()));
    }

    private static String sectionLabel(String section) {
        switch (section) {
            case "traffic": return "Traffic vehicle and driver records";
            case "finance": return "Finance payments";
            case "pension": return "Pension record";
            case "passport": return "Passport records";
            default: return "Home Affairs ID details";
        }
    }

    private static String passportNumber(Application a) {
        String n = Registry.answer(a.id, "Your old passport", "Passport number");
        if (n.isEmpty()) n = Registry.answer(a.id, "The lost passport", "Passport number (if you know it)");
        return n;
    }

    /**
     * The record of one person, opened for one case. It starts with the Home Affairs ID details.
     * Traffic, Finance, Pension and Passport records open one at a time, and each one is logged with the case number.
     * The home address needs a Home Affairs approval for this case.
     */
    static Resp person(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.PO);
        if (bad != null) return bad;
        if (!r.post()) return Resp.redirect("/staff/police");
        Citizen c = Store.citizen(r.get("id"));
        CaseRec cs = Store.caseByNo(r.get("case_no"));
        if (c == null) return Resp.to("/staff/police", "err:Citizen not found.");
        if (cs == null || cs.status.equals("Closed")) return Resp.to("/staff/police", "err:Pick an open case first. Police open a person's record only for a case.");
        String act = r.get("act"), section = r.get("section");
        String notice = "";

        if (act.equals("address")) {
            String reason = r.get("reason").trim();
            AddressRequest prev = Store.latestAddressRequest(c.id, cs.caseNo);
            if (reason.length() < 5) notice = "<div class='err'>! Write the reason for needing the address. Home Affairs needs it.</div>";
            else if (prev != null && prev.status.equals("Pending")) notice = "<div class='err'>! Waiting for Home Affairs to decide on the address request.</div>";
            else {
                Store.addAddressRequest(c.id, cs.caseNo, reason, o.name);
                Store.logAccess(c.id, o.dept, o.name, "Address request", "Case " + cs.caseNo + ": " + reason);
                Store.addCaseNote(cs.caseNo, o.name, "Asked Home Affairs for the address of " + c.name + ". Reason: " + reason);
                notice = "<div class='ok'>&#10003; Address request sent to Home Affairs.</div>";
            }
        } else if (act.equals("link")) {
            Store.linkCase(cs.caseNo, c.id);
            Store.addCaseNote(cs.caseNo, o.name, "Linked person: " + c.name + " (ID " + c.id + ")");
            Store.logAccess(c.id, o.dept, o.name, "Name, ID number", "Linked to case " + cs.caseNo);
            cs = Store.caseByNo(cs.caseNo);
        } else if (act.equals("section") && List.of("traffic", "finance", "pension", "passport").contains(section)) {
            Store.logAccess(c.id, o.dept, o.name, sectionLabel(section), "Case " + cs.caseNo);
        } else {
            section = "";
            Store.logAccess(c.id, o.dept, o.name, "Name, ID number, Date of birth, Address, Photo, Fingerprints", "Case " + cs.caseNo);
        }

        StringBuilder sb = new StringBuilder(notice);

        // ---- Home Affairs: ID details
        sb.append(Html.label("HOME AFFAIRS: ID DETAILS"));
        sb.append(Html.card("<div class='two'>" + Html.avatar(c, true, 90) + "<div><b style='font-size:18px'>" + Html.esc(c.name) + "</b><br>" + Html.pill(c.alive ? "Alive" : "Deceased")
                + "<br><span class='mu'>ID " + Html.esc(c.id) + "<br>Born " + c.dob + " (age " + Policy.age(c, LocalDate.now()) + ")<br>" + Html.esc(c.gender == null ? "" : c.gender) + "</span></div></div>"));

        sb.append(Html.label("FINGERPRINTS"));
        Doc fpDoc = Store.docOf(c.id, "Fingerprints");
        if (fpDoc != null && fpDoc.stored != null) {
            boolean img = fpDoc.mime != null && fpDoc.mime.startsWith("image/");
            sb.append(Html.card("<span class='mu'>Code " + Html.esc(c.fingerprint == null || c.fingerprint.isEmpty() ? "none" : c.fingerprint) + " · " + Html.esc(fpDoc.status) + "</span>"
                    + (img ? "<img class='big' src='/file?id=" + fpDoc.id + "' alt='Fingerprints'>" : "")
                    + "<a class='sm' href='/file?id=" + fpDoc.id + "' target='_blank'>Open</a>"));
        } else sb.append(Html.mu("No fingerprints on file."));

        sb.append(Html.label("HOME ADDRESS"));
        sb.append(Html.card("<b>" + Html.esc(c.address == null || c.address.isEmpty() ? "Not on record" : c.address) + "</b><span class='mu'>Home Affairs address on the national register</span>"));

        sb.append(TravelFlags.personHtml(c.id, cs.caseNo));

        sb.append(Html.label("LOST DOCUMENTS"));
        List<LostDoc> lost = Store.lostOf(c.id);
        for (LostDoc l : lost) sb.append(Html.row(l.docType + (l.docNo.isEmpty() ? "" : " " + l.docNo), l.lostDate + " · " + l.place + " · case " + l.caseNo, Html.pill(l.status), null));
        if (lost.isEmpty()) sb.append(Html.mu("No lost documents recorded."));

        // ---- the section that was asked for
        switch (section) {
            case "traffic": {
                sb.append(Html.label("TRAFFIC: VEHICLE AND DRIVER RECORDS"));
                boolean any = false;
                for (TrafficRec t : Store.trafficOf(c.id)) { any = true; sb.append(Html.row(t.kind + ": " + t.detail, t.status + " · " + CitizenPages.WHEN.format(t.created), null, null)); }
                for (Application a : Store.appsOf(c.id)) if (a.dept == Dept.TR) { any = true; sb.append(Html.row(a.service, a.reference + " · " + a.status.label, null, null)); }
                if (!any) sb.append(Html.mu("No vehicle or driver records."));
                break;
            }
            case "finance": {
                sb.append(Html.label("FINANCE: PAYMENTS"));
                boolean any = false;
                for (Payment p : Store.paymentsOf(c.id)) if (p.kind.equals("FEE")) { any = true; sb.append(Html.row(p.ref, "M " + p.amount + (p.receipt == null ? "" : " · receipt " + p.receipt), Html.pill(p.paid ? "Paid" : "Pending"), null)); }
                for (Application a : Store.appsOf(c.id)) if (a.dept == Dept.FI) { any = true; sb.append(Html.row(a.service, a.reference + " · " + a.status.label, null, null)); }
                if (!any) sb.append(Html.mu("No fee payments."));
                break;
            }
            case "pension": {
                sb.append(Html.label("GRANT AND PENSION RECORD"));
                sb.append(Html.row(Store.enrolled(c.id) ? "On the old-age grant register" : "Not on the old-age grant register", "Status", null, null));
                sb.append(Html.row(Store.pensionEnrolled(c.id) ? "On the retirement pension register" : "Not on the retirement pension register", "Status", null, null));
                sb.append(Html.row(Store.govtWorker(c.id) ? "Government employee" : "Private sector", "Employment", null, null));
                for (Payment p : Store.benefitPaymentsOf(c.id)) sb.append(Html.row(Policy.benefitLabel(p.kind) + " " + p.month, "M " + p.amount, Html.pill(p.paid ? "Paid" : "Pending"), null));
                break;
            }
            case "passport": {
                sb.append(Html.label("PASSPORT RECORDS"));
                boolean any = false;
                for (Application a : Store.appsOf(c.id)) if (a.dept == Dept.PA) {
                    any = true;
                    String no = passportNumber(a);
                    sb.append(Html.row(a.service, a.reference + " · " + a.status.label + (no.isEmpty() ? "" : " · passport " + no), null, null));
                }
                if (!any) sb.append(Html.mu("No passport records."));
                break;
            }
            default: break;
        }

        // ---- buttons for the other records
        sb.append(Html.label("OTHER RECORDS FOR THIS CASE")).append("<div class='row'>");
        String[][] secs = {{"traffic", "Traffic records"}, {"finance", "Finance"}, {"pension", "Pension record"}, {"passport", "Passport records"}};
        for (String[] s : secs)
            sb.append(Html.form("/staff/person", Html.hidden("id", c.id) + Html.hidden("case_no", cs.caseNo) + Html.hidden("act", "section") + Html.hidden("section", s[0])
                    + "<button class='sm'>" + Html.esc(s[1]) + "</button>"));
        sb.append("</div>");
        if (cs.citizenId == null || !cs.citizenId.equals(c.id))
            sb.append(Html.form("/staff/person", Html.hidden("id", c.id) + Html.hidden("case_no", cs.caseNo) + Html.hidden("act", "link") + "<button class='sm'>Link this person to the case</button>"));

        return Resp.html(Html.page("Person Record", "Case " + cs.caseNo, "/staff/case?no=" + cs.caseNo, sb.toString()));
    }

    /** Police record a lost or stolen ID card or passport. A case is opened and the citizen is told. */
    static Resp lost(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.PO);
        if (bad != null) return bad;
        LocalDate today = LocalDate.now();
        if (r.post()) {
            if (r.get("act").equals("found")) {
                LostDoc ld = null;
                try { ld = Store.lost(Integer.parseInt(r.get("id"))); } catch (Exception ignored) { }
                if (ld == null || !ld.status.equals("Reported")) return Resp.to("/staff/lost", "err:Nothing to update.");
                Store.setLostStatus(ld.id, "Found");
                Store.updateCase(ld.caseNo, "Closed");
                Store.addCaseNote(ld.caseNo, o.name, "Document found. Case closed.");
                Store.logAccess(ld.citizenId, o.dept, o.name, "Lost document record", "Marked as found, case " + ld.caseNo);
                Store.notify(ld.citizenId, "Police found your " + ld.docType + " (case " + ld.caseNo + "). Contact the police station to collect it.");
                return Resp.to("/staff/lost", "ok:Marked as found. The citizen was told.");
            }
            Citizen c = Store.citizen(r.get("id"));
            String type = r.get("doc_type"), docNo = r.get("doc_no").trim(), place = r.get("place").trim(), details = r.get("details").trim();
            if (c == null) return Resp.to("/staff/lost", "err:No citizen with that ID number in the register.");
            if (!type.equals("National ID card") && !type.equals("Passport")) return Resp.to("/staff/lost", "err:Choose the document.");
            LocalDate when;
            try { when = LocalDate.parse(r.get("lost_date")); } catch (Exception e) { return Resp.to("/staff/lost", "err:Enter the date it was lost."); }
            if (when.isAfter(today)) return Resp.to("/staff/lost", "err:The date cannot be in the future.");
            if (place.isEmpty()) return Resp.to("/staff/lost", "err:Write the place where it was lost.");
            if (docNo.length() > 40) docNo = docNo.substring(0, 40);
            if (place.length() > 120) place = place.substring(0, 120);
            if (details.length() > 300) details = details.substring(0, 300);
            CaseRec cs = Store.addCase("Lost " + type + ": " + c.name, details, c.id, o.name);
            Store.addLost(c.id, type, docNo, when, place, details, o.name, cs.caseNo);
            Store.addCaseNote(cs.caseNo, o.name, "Lost " + type + " recorded. Place: " + place + (docNo.isEmpty() ? "" : ". Document number: " + docNo));
            Store.logAccess(c.id, o.dept, o.name, "Lost document record", "Case " + cs.caseNo);
            Store.notify(c.id, "Police recorded your lost " + type + " (case " + cs.caseNo + "). "
                    + (type.equals("Passport") ? "To replace it, apply for Lost or Stolen Passport under Passport Services." : "To replace it, book an appointment at Home Affairs, then apply for ID Replacement."));
            return Resp.to("/staff/lost", "ok:Recorded. Case number " + cs.caseNo + ". The citizen was told.");
        }

        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Record a lost or stolen ID card or passport. A case is opened and the citizen is told."));
        sb.append(Html.form("/staff/lost", Html.hidden("act", "record") + Html.field("ID number of the owner", "id", "text", "")
                + Html.select("Which document?", "doc_type", "National ID card", "Passport") + Html.field("Document number (if known)", "doc_no", "text", "")
                + "<label class='f'>Date it was lost<input type='date' name='lost_date' max='" + today + "' required></label>"
                + Html.field("Place where it was lost", "place", "text", "") + Html.field("Details", "details", "text", "") + "<br>" + Html.button("Record lost document")));
        sb.append(Html.label("RECENT REPORTS"));
        List<LostDoc> list = Store.lostRecent(30);
        for (LostDoc ld : list) {
            Citizen c = Store.citizen(ld.citizenId);
            String act = ld.status.equals("Reported") ? Html.form("/staff/lost", Html.hidden("act", "found") + Html.hidden("id", String.valueOf(ld.id)) + "<button class='sm'>Mark found</button>") : "";
            sb.append("<div class='rw'>").append(av(o, c, 40)).append("<div><b>").append(Html.esc(ld.docType + " · " + c.name)).append("</b><small>ID ").append(Html.esc(c.id))
                    .append(" · case ").append(Html.esc(ld.caseNo)).append(" · ").append(ld.lostDate).append(" · ").append(Html.esc(ld.place)).append("</small></div>")
                    .append(Html.pill(ld.status)).append(act).append("</div>");
        }
        if (list.isEmpty()) sb.append(Html.mu("No reports yet."));
        return Resp.html(Html.page("Lost IDs & Passports", B, "/staff", sb.toString()));
    }

    /** Home Affairs decides each police request for an address. */
    static Resp address(Req r) {
        Official o = r.staff();
        Resp bad = only(o, Dept.HA);
        if (bad != null) return bad;
        if (r.post()) {
            AddressRequest q = null;
            try { q = Store.addressRequest(Integer.parseInt(r.get("id"))); } catch (Exception ignored) { }
            if (q == null || !q.status.equals("Pending")) return Resp.to("/staff/address", "err:Nothing to decide.");
            boolean yes = r.get("act").equals("approve");
            Store.decideAddress(q.id, yes ? "Approved" : "Rejected", o.name);
            boolean fpReq = q.reason.startsWith("Fingerprints");
            if (fpReq && yes) Store.ensureFingerprintCode(q.citizenId);
            Store.logAccess(q.citizenId, o.dept, o.name, fpReq ? "Fingerprints" : "Address", (yes ? "Released to Police" : "Refused to Police") + ", " + (fpReq ? "clearance " : "case ") + q.caseNo);
            if (fpReq) return Resp.to("/staff/address", yes ? "ok:Approved. Police can now see the fingerprints." : "ok:Refused. Police do not get the fingerprints.");
            return Resp.to("/staff/address", yes ? "ok:Approved. Police can now see the address." : "ok:Refused. Police do not get the address.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Police ask for the address of a person in a criminal case, or for the fingerprints of a person who applied for a police clearance. Check the reference and the reason before you approve."));
        sb.append(Html.label("WAITING FOR YOUR DECISION"));
        List<AddressRequest> pending = Store.addressRequestsPending();
        for (AddressRequest q : pending) {
            Citizen c = Store.citizen(q.citizenId);
            sb.append(Html.card("<div class='two'>" + av(o, c, 48) + "<div><b>" + Html.esc(c.name) + "</b><span class='mu'>ID " + c.id + "<br>Address on the register: " + Html.esc(c.address == null ? "" : c.address)
                    + "<br>" + (q.reason.startsWith("Fingerprints") ? "Police clearance " : "Case ") + Html.esc(q.caseNo) + " · asked by " + Html.esc(q.officer) + " · " + CitizenPages.WHEN.format(q.requested) + "<br>Reason: " + Html.esc(q.reason) + "</span></div></div>"
                    + "<form method='post' action='/staff/address'>" + Html.hidden("id", String.valueOf(q.id))
                    + "<button class='sm' name='act' value='approve'>Approve</button> <button class='sm' name='act' value='reject'>Refuse</button></form>"));
        }
        if (pending.isEmpty()) sb.append(Html.mu("No requests are waiting."));
        sb.append(Html.label("RECENT DECISIONS"));
        for (AddressRequest q : Store.addressRequestsDecided()) {
            Citizen c = Store.citizen(q.citizenId);
            sb.append(Html.rowAv(av(o, c, 40), c.name + (q.reason.startsWith("Fingerprints") ? " · fingerprints " : " · case ") + q.caseNo, "Asked by " + q.officer + " · decided by " + (q.decidedBy == null ? "" : q.decidedBy), Html.pill(q.status), null));
        }
        return Resp.html(Html.page("Police Address Requests", B, "/staff", sb.toString()));
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
                sb.append(Html.card("<div class='two'>" + av(o, c, 48) + "<div><b>Typed: " + Html.esc(c.registeredName) + "</b><span class='mu'>Register: " + Html.esc(c.name) + " · ID " + c.id + "</span></div></div>"
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
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Documents, photos and fingerprints uploaded by citizens. Open one to check it, then verify it."));
        List<Doc> l = Store.uploadedDocs();
        for (Doc d : l) {
            Citizen c = Store.citizen(d.citizenId);
            String view = d.stored == null ? "" : "<a class='sm' href='/file?id=" + d.id + "' target='_blank'>Open</a>";
            sb.append("<div class='rw'>").append(av(o, c, 40)).append("<div><b>").append(Html.esc(d.type)).append("</b><small>").append(Html.esc(c.name)).append(" · ").append(Html.esc(d.filename)).append("</small></div>").append(view)
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
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Life status is kept here and shared with Pensions and every department. It also updates by itself when a death certificate is issued."));
        for (Citizen c : Store.allCitizens()) {
            String act = c.alive ? "<form method='post' action='/staff/life' onsubmit=\"return confirm('Register a death for " + Html.esc(c.name) + "?')\">" + Html.hidden("id", c.id) + "<button class='sm'>Register death</button></form>" : "";
            sb.append("<div class='rw'>").append(av(o, c, 40)).append("<div><b>").append(Html.esc(c.name)).append("</b><small>ID ").append(c.id).append("</small></div>").append(Html.pill(c.alive ? "Alive" : "Deceased")).append(act).append("</div>");
        }
        return Resp.html(Html.page("Life Status", B, "/staff", sb.toString()));
    }
}