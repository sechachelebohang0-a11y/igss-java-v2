package lesotho.igss;

import lesotho.igss.Models.*;

import java.time.LocalDate;
import java.util.*;

/** Rules that cross departments: appointments, pensions, deaths, birth certificate ID numbers, collection offices and Traffic records. */
final class Registry {
    private Registry() {}

    static final String OLD_AGE = Policy.GRANT_SERVICE;
    static final String RETIRE = Policy.PENSION_SERVICE;

    /** GRANT for the old-age grant, PENSION for the retirement pension, null for any other service. */
    static String benefitKind(String service) { return OLD_AGE.equals(service) ? "GRANT" : RETIRE.equals(service) ? "PENSION" : null; }

    /** Services that need a booked appointment first (the ID card or the licence is made at the office). Empty this set to switch the rule off. */
    static final Set<String> NEEDS_APPOINTMENT = Set.<String>of();

    /** A status where the document or decision is issued. */
    static boolean issuing(Status st) { return st == Status.APPROVED || st == Status.READY || st == Status.COMPLETED; }

    /** Runs before the application form. Returns a page to show instead, or null when the citizen may go on. */
    // ------------------------------------------------------------------ pensions: automatic work
    /** Every month Pensions sends the grant and pension payments to Finance. Safe to run again: nobody is sent twice for the same month. */
    static synchronized int sendMonthlyBenefits() {
        java.time.YearMonth ym = java.time.YearMonth.now();
        int sent = 0;
        for (String kind : new String[]{"GRANT", "PENSION"}) {
            List<Citizen> reg = kind.equals("GRANT") ? Store.enrolledList() : Store.pensionList();
            for (Citizen c : reg) {
                if (!c.alive || Store.benefitProblem(c, kind) != null || Store.pensionPayment(c.id, ym, kind) != null) continue;
                Store.addPayment(c.id, kind, Policy.benefitLabel(kind) + " " + ym, Policy.benefitAmount(kind), ym.toString(), false, null, 0);
                Store.notify(c.id, "Your " + Policy.benefitLabel(kind).toLowerCase() + " for " + ym + " was sent to Finance for payment.");
                sent++;
            }
        }
        return sent;
    }

    /** Tells each citizen once when they reach pension age and have not applied. */
    static synchronized int inviteNewPensioners() {
        int n = 0;
        LocalDate today = LocalDate.now();
        for (Citizen c : Store.allCitizens()) {
            if (!c.alive || !c.hasAccount() || !Policy.pensionAge(c, today) || Store.enrolled(c.id) || Store.pensionEnrolled(c.id) || Store.invitedToPension(c.id)) continue;
            Store.notify(c.id, "You are " + Policy.age(c, today) + ". You can now get the old-age grant or the retirement pension. "
                    + (Store.govtWorker(c.id) ? "You work for the government, so you must choose one of them. Open My Pension to choose." : "Open My Pension to apply."));
            Store.markInvited(c.id);
            n++;
        }
        return n;
    }

    /** Runs the two jobs above when the app starts and then every 6 hours. */
    static void startPensionJob() {
        java.util.concurrent.ScheduledExecutorService ex = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(rn -> { Thread t = new Thread(rn, "pension-job"); t.setDaemon(true); return t; });
        ex.scheduleWithFixedDelay(() -> {
            try { inviteNewPensioners(); sendMonthlyBenefits(); } catch (RuntimeException e) { e.printStackTrace(); }
        }, 5, 6 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static final java.util.ArrayDeque<Long> LOOKUPS = new java.util.ArrayDeque<>();
    /**
     * Parent autofill on the birth forms: an ID number gives the name and date of birth only (never email, phone or address).
     * Only living adults, and at most 40 looks a minute for the whole system.
     */
    static synchronized Resp lookup(Req r) {
        Resp out = new Resp();
        out.type = "application/json; charset=utf-8";
        out.body = "{}";
        long now = System.currentTimeMillis();
        while (!LOOKUPS.isEmpty() && now - LOOKUPS.peekFirst() > 60000) LOOKUPS.pollFirst();
        if (LOOKUPS.size() >= 40) { out.code = 429; return out; }
        LOOKUPS.addLast(now);
        String id = Store.norm(r.get("id"));
        if (!id.matches("[0-9]{11}")) return out;
        Citizen c = Store.citizen(id);
        if (c == null || !c.alive || Policy.age(c, LocalDate.now()) < 14) return out;
        out.body = "{\"name\":\"" + c.name.replace("\\", "").replace("\"", "") + "\",\"dob\":\"" + c.dob + "\"}";
        return out;
    }

    static Resp guardApply(Req r) {
        Citizen me = r.citizen();
        if (me == null) return null; // the form itself sends them to log in
        Dept d = CitizenPages.dept(r.get("dept"));
        Policy.Service sv = Policy.service(d, r.get("service"));
        if (sv == null) return null;
        String back = "/services?dept=" + d.name();
        if (sv.pension && Store.govtWorker(me.id) && Policy.pensionAge(me, LocalDate.now())) {
            String kind = benefitKind(sv.name), ch = Store.benefitChoice(me.id);
            if (ch == null) return Resp.to("/pension", "err:You are a government employee and you are retired. Choose the old-age grant or the retirement pension first. You cannot receive both.");
            if (!ch.equals(kind)) return Resp.to(back, "err:You chose the " + Policy.benefitLabel(ch) + ". A government employee cannot also receive the " + Policy.benefitLabel(kind) + ".");
        }
        if (sv.name.equals("Learner's Licence") && Policy.age(me, LocalDate.now()) < 16)
            return Resp.to(back, "err:A learner's licence is for people aged 16 or older.");
        if (sv.name.equals("Driving Licence (new)")) {
            if (Policy.age(me, LocalDate.now()) < 18) return Resp.to(back, "err:A driving licence is for people aged 18 or older.");
            if (!Store.hasIssued(me.id, "Learner's Licence")) return Resp.to(back, "err:You need an approved learner's licence first. Apply for the Learner's Licence.");
        }
        if (sv.name.equals("First-time ID Card") && Store.hasIdCard(me.id))
            return Resp.to(back, "err:You already have a national ID card. If it is lost, damaged or expiring, use ID Replacement or ID Renewal.");
        if (sv.name.equals("First-time ID Card") && Db.count("select count(*) from applications where citizen_id=? and service=? and status not in ('REJECTED','COMPLETED')", me.id, sv.name) > 0)
            return Resp.to(back, "err:You already applied for a first-time ID card. Wait for Home Affairs to finish it.");
        if ((sv.name.equals("ID Renewal") || sv.name.startsWith("ID Replacement")) && !Store.hasIdCard(me.id))
            return Resp.to(back, "err:You do not have an ID card yet. Apply for the First-time ID Card.");
        if (sv.name.equals("First-time ID Card") && Policy.age(me, LocalDate.now()) < 16)
            return Resp.to(back, "err:A national ID card is for people aged 16 or older.");
        if (sv.name.startsWith("D Permit") && !Certificates.hasValid(me.id, Certificates.ROADWORTHY))
            return Resp.to("/services?dept=TR", "err:You cannot apply for a D Permit without a Roadworthy Certificate. Book a roadworthy inspection at Traffic Services. When your vehicle passes, you get the certificate and can apply.");
        if (NEEDS_APPOINTMENT.contains(sv.name) && !Store.hasUpcomingBooking(me.id, d))
            return Resp.to(CitizenPages.bookHref(d, sv.name), "err:Book an appointment at " + d.label + " first. The " + sv.name + " is made at the office.");
        return null;
    }

    // ------------------------------------------------------------------ death
    /** Home Affairs issued the death certificate: mark the person as deceased so every payment stops. Returns an error text, or null when fine. */
    static String recordDeath(Official o, Application a) {
        String id = Store.norm(answer(a.id, "The person who died", "ID number"));
        if (id.isEmpty()) return null; // no ID number on the form (for example a young child)
        Citizen c = Store.citizen(id);
        if (c == null) return "The ID number on the form (" + id + ") is not in the register. Check it before issuing the death certificate.";
        if (c.id.equals(a.citizenId)) return "The applicant cannot be the person who died. Check the ID number on the form.";
        if (c.alive) {
            Store.registerDeath(c.id);
            Store.logAccess(c.id, o.dept, o.name, "Life status", "Death certificate issued, " + a.reference);
        }
        return null;
    }

    // ------------------------------------------------------------------ Traffic records
    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (p != null && !p.trim().isEmpty()) sb.append(sb.length() > 0 ? ", " : "").append(p.trim());
        return sb.toString();
    }

    /** When Traffic issues a vehicle registration, a vehicle transfer or a licence renewal, the record is kept so Police can see it for a case. */
    static void recordTraffic(Application a) {
        String kind = null, detail = null, status = "Registered";
        switch (a.service) {
            case "Vehicle Registration": {
                String s = "The vehicle";
                kind = "Vehicle";
                detail = join(answer(a.id, s, "Make (for example Toyota)") + " " + answer(a.id, s, "Model") + " " + answer(a.id, s, "Year of manufacture"),
                        answer(a.id, s, "Colour"), "chassis " + answer(a.id, s, "Chassis number (VIN)"));
                break;
            }
            case "Change of Ownership": {
                String s = "The vehicle";
                kind = "Vehicle";
                detail = join(answer(a.id, s, "Make") + " " + answer(a.id, s, "Model") + " " + answer(a.id, s, "Year of manufacture"),
                        "registration " + answer(a.id, s, "Registration number (number plate)"));
                break;
            }
            case "Roadworthy Certificate": {
                String s = "The vehicle";
                kind = "Roadworthy certificate";
                status = "Valid";
                detail = join(answer(a.id, s, "Registration number (number plate)") + " " + answer(a.id, s, "Make") + " " + answer(a.id, s, "Model"), "valid until " + LocalDate.now().plusYears(1));
                break;
            }
            case "Learner's Licence": {
                kind = "Learner's licence";
                status = "Valid";
                detail = join("Class: " + answer(a.id, "The licence you want", "Class of vehicle"), "valid until " + LocalDate.now().plusMonths(24));
                break;
            }
            case "Driving Licence (new)": {
                kind = "Driving licence";
                status = "Valid";
                detail = join("Licence DL" + (300000 + a.id), "class: " + answer(a.id, "Your learner's licence", "Class of vehicle"), "expires " + LocalDate.now().plusYears(5));
                break;
            }
            case "Driving Licence Renewal": {
                String s = "Your licence";
                kind = "Driving licence";
                status = "Valid";
                detail = join("Licence " + answer(a.id, s, "Licence number"), "class: " + answer(a.id, s, "Class of vehicle"), "expires " + answer(a.id, s, "Date it expires"));
                break;
            }
            default: return;
        }
        if (detail == null || detail.isEmpty() || Store.trafficHas(a.citizenId, kind, detail)) return;
        Store.addTraffic(a.citizenId, kind, detail, status);
    }

    // ------------------------------------------------------------------ birth certificate ID number
    /** For a Birth Registration made by a logged-in citizen. Returns the child's ID number, or null when the form has no usable date of birth. */
    static String issueBirth(Application a) {
        String s = "The child";
        return issue("C", a.id, answer(a.id, s, "Child's first names"), answer(a.id, s, "Child's surname"), answer(a.id, s, "Date of birth"),
                answer(a.id, s, "Sex"), answer(a.id, s, "Village / Town of birth"), answer(a.id, s, "District of birth"));
    }

    private static String issue(String source, int appId, String first, String surname, String dobText, String sex, String place, String district) {
        String existing = Store.birthCert(source, appId);
        if (existing != null) return existing;
        LocalDate dob;
        try { dob = LocalDate.parse(dobText); } catch (Exception e) { return null; }
        String name = (first + " " + surname).trim();
        if (name.isEmpty()) return null;
        String id = Store.newIdNumber(dob, "Male".equals(sex));
        StringBuilder addr = new StringBuilder();
        for (String p : new String[]{place, district, "Lesotho"}) if (p != null && !p.isEmpty()) addr.append(addr.length() > 0 ? ", " : "").append(p);
        Store.addBirthCitizen(id, name, dob, sex, addr.toString());
        Store.saveBirthCert(source, appId, id);
        return id;
    }

    /**
     * Wraps the walk-in update. When Home Affairs approves a walk-in application, the note tells the applicant which district office to collect from.
     * After a birth registration is approved or completed, the child also gets an ID number.
     */
    static Resp publicUpdate(Req r) {
        Resp resp = PublicApply.staffUpdate(r);
        Official o = r.staff();
        if (o == null || o.dept != Dept.HA || !r.post()) return resp;
        int id;
        try { id = Integer.parseInt(r.get("id")); } catch (Exception e) { return resp; }
        List<String[]> row = Db.list("select form,status,note from public_apps where id=?",
                rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3) == null ? "" : rs.getString(3)}, id);
        if (row.isEmpty()) return resp;
        String form = row.get(0)[0], st = row.get(0)[1], note = row.get(0)[2];
        boolean approved = st.equals("APPROVED"), done = approved || st.equals("COMPLETED");
        String before = note;

        if (done && form.equals("birth")) {
            String idNo = issue("P", id, pub(id, "First names"), pub(id, "Surname"), pub(id, "Date of birth"), pub(id, "Sex"),
                    pub(id, "Name of the hospital, clinic or village"), pub(id, "District of birth"));
            String tag = idNo == null ? "" : "Birth certificate ID number: " + idNo;
            if (!tag.isEmpty() && !note.contains(tag)) note = (note.isEmpty() ? "" : note + " ") + tag;
        }
        if (done && form.equals("id") && service(id).equals("First-time ID Card")) {
            String village = pub(id, "Village or town"), dist = pub(id, "District");
            String idNo = issue("I", id, pub(id, "First names"), pub(id, "Surname"), pub(id, "Date of birth"), pub(id, "Sex"), village, dist);
            if (idNo != null) {
                attachPhoto(id, idNo);
                Store.giveIdCard(idNo);
                String ph = pub(id, "Phone number");
                if (!ph.isEmpty()) Db.exec("update citizens set phone=? where id=?", ph, idNo);
                String tag = "Your new ID number is " + idNo + ". Use it to create your account.";
                if (!note.contains("Your new ID number is")) note = (note.isEmpty() ? "" : note + " ") + tag;
            }
        }
        if (done && form.equals("id") && !service(id).equals("First-time ID Card")) {
            String existing = pub(id, "Existing ID number").replace(" ", "").replace("-", "");
            if (!existing.isEmpty() && Store.citizen(existing) != null) attachPhoto(id, existing);
        }
        if (approved) {
            String district = form.equals("id") ? pub(id, "District") : form.equals("birth") ? pub(id, "Your district") : pub(id, "District of birth");
            String office = r.get("office");
            if (!Policy.OFFICES.contains(office)) office = Policy.officeFor(district);
            String tag = "Collect it at the Home Affairs office in " + office + ". Bring this reference number and your ID if you have one.";
            note = note.replaceAll("Collect it at the Home Affairs office in [^.]*\\. Bring this reference number and your ID if you have one\\.", "").trim();
            note = (note.isEmpty() ? "" : note + " ") + tag;
        }
        if (!note.equals(before)) Db.exec("update public_apps set note=? where id=?", note, id);
        return resp;
    }

    // ------------------------------------------------------------------ small readers
    static String answer(int appId, String section, String label) {
        List<String> l = Db.list("select value from form_answers where app_id=? and section=? and label=?", rs -> rs.getString(1), appId, section, label);
        return l.isEmpty() || l.get(0) == null ? "" : l.get(0);
    }

    /** The photo sent with a walk-in ID application becomes the person's photo (profile, forms, staff screens). Never replaces a photo already on file. */
    private static void attachPhoto(int appId, String citizenId) {
        Models.Doc have = Store.docOf(citizenId, "Passport photo");
        if (have != null && have.stored != null) return;
        List<String[]> f = Db.list("select filename,stored,mime from public_files where app_id=? and kind='Photo' order by id desc limit 1",
                rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, appId);
        if (f.isEmpty()) return;
        try {
            String ext = f.get(0)[2].equals("image/png") ? ".png" : ".jpg";
            String stored = java.util.UUID.randomUUID() + ext;
            java.nio.file.Files.copy(Store.UPLOADS.resolve(f.get(0)[1]), Store.UPLOADS.resolve(stored));
            Store.putDoc(citizenId, "Passport photo", f.get(0)[0], stored, f.get(0)[2], "Verified");
        } catch (java.io.IOException e) { e.printStackTrace(); }
    }

    private static String service(int appId) {
        List<String> l = Db.list("select service from public_apps where id=?", rs -> rs.getString(1), appId);
        return l.isEmpty() || l.get(0) == null ? "" : l.get(0);
    }

    private static String pub(int appId, String label) {
        List<String> l = Db.list("select value from public_answers where app_id=? and label=? order by ord", rs -> rs.getString(1), appId, label);
        return l.isEmpty() || l.get(0) == null ? "" : l.get(0);
    }
}