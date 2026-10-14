package lesotho.igss;

import lesotho.igss.Models.*;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Everything a citizen sees. A citizen only ever sees their own information. */
final class CitizenPages {
    private CitizenPages() {}

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final String B = "Dashboard";

    private static Resp needLogin() { return Resp.redirect("/login"); }

    static Resp landing(Req r) {
        String body = Html.intro("One ID. One Profile. All Services.")
            + Html.tiles(List.of(new String[]{"/login", "user", "Citizen Login"}, new String[]{"/register", "id", "Create Account"}, new String[]{"/staff/login", "lock", "Department Official"}))
            + Html.label("");
        return Resp.html(Html.page("Integrated Government Services", null, null, body));
    }

    static Resp register(Req r) {
        if (!r.post()) return Resp.html(Html.page("Create Citizen Account", "Back", "/",
            Html.banner(r.get("m")) + Html.intro("Home Affairs checks your details once. After that every department can confirm who you are.")
            + Html.form("/register", Html.field("Full name", "name", "text", "") + Html.field("ID number", "id", "text", "")
                + Html.field("Date of birth (yyyy-mm-dd)", "dob", "text", "") + Html.field("Phone (optional)", "phone", "text", "")
                + Html.field("Password", "password", "password", "") + "<br>" + Html.button("Sign Up"))));
        Citizen c = Store.citizen(r.get("id"));
        if (c == null) return Resp.to("/register", "err:No Home Affairs record found for that ID. Please visit your nearest Home Affairs office.");
        if (c.hasAccount()) return Resp.to("/register", "err:This ID already has an account. Please log in.");
        LocalDate dob;
        try { dob = LocalDate.parse(r.get("dob")); } catch (Exception e) { return Resp.to("/register", "err:Enter your date of birth as yyyy-mm-dd."); }
        if (!dob.equals(c.dob)) return Resp.to("/register", "err:The date of birth does not match the Home Affairs record.");
        if (r.get("password").length() < 6) return Resp.to("/register", "err:Choose a password of at least 6 characters.");
        c.salt = Store.salt();
        c.hash = Store.hash(c.salt, r.get("password"));
        c.registeredName = r.get("name");
        c.accountVerified = r.get("name").equalsIgnoreCase(c.name);
        if (!r.get("phone").isEmpty()) c.phone = r.get("phone");
        Store.saveAccount(c);
        Store.notify(c.id, c.accountVerified ? "Welcome! Your identity was verified once by Home Affairs." : "Your account is waiting for Home Affairs to confirm your name.");
        Resp resp = Resp.redirect("/home");
        resp.newSession = new Sess("C", c.id);
        return resp;
    }

    static Resp login(Req r) {
        if (r.post()) {
            Citizen c = Store.citizen(r.get("id"));
            if (c != null && c.hasAccount() && Store.hash(c.salt, r.p.getOrDefault("password", "")).equals(c.hash)) {
                Resp resp = Resp.redirect("/home");
                resp.newSession = new Sess("C", c.id);
                return resp;
            }
            return Resp.to("/login", "err:Wrong ID number or password.");
        }
        return Resp.html(Html.page("Citizen Login", "Back", "/", Html.banner(r.get("m"))
            + Html.intro("Welcome back. Log in to use all government services with one profile.")
            + Html.form("/login", Html.field("ID number", "id", "text", "") + Html.field("Password", "password", "password", "") + "<br>" + Html.button("Login"))));
    }

    static Resp home(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        List<String[]> t = new ArrayList<>();
        for (Models.Dept d : Models.Dept.values()) t.add(new String[]{"/services?dept=" + d.name(), d.icon, d.label});
        t.add(new String[]{"/apps", "doc", "My Applications"});
        t.add(new String[]{"/docs", "folder", "My Documents"});
        t.add(new String[]{"/pension", "coin", "My Pension"});
        t.add(new String[]{"/book", "cal", "Appointments"});
        t.add(new String[]{"/notifs", "bell", "Notifications"});
        t.add(new String[]{"/access", "lock", "Who Accessed My Data"});
        t.add(new String[]{"/profile", "user", "Profile"});
        StringBuilder body = new StringBuilder(Html.banner(r.get("m")));
        body.append(me.accountVerified ? Html.card("<b>" + Html.esc(me.name) + "</b><span class='mu'>ID " + me.id + "</span>" + Html.pill("Verified"))
            : "<div class='err'>! Your account is waiting for Home Affairs to confirm your name. You cannot apply yet.</div>");
        body.append(Html.tiles(t)).append(Html.label("RECENT UPDATES"));
        int n = 0;
        for (Notification x : Store.notesOf(me.id)) { if (n++ == 3) break; body.append(Html.row(x.text, x.time.format(WHEN), null, "/notifs")); }
        body.append("<a class='sm' style='text-align:center' href='/logout'>Log out</a>");
        return Resp.html(Html.page("Welcome back, " + me.name.split(" ")[0] + "!", null, null, body.toString()));
    }

    static Resp profile(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        Doc photo = Store.docOf(me.id, "Passport photo");
        String img = photo != null && photo.stored != null ? "<img class='ph' src='/file?id=" + photo.id + "' alt='Your photo'>" : "";
        String body = Html.intro("Your verified profile. Home Affairs confirmed it once.")
            + Html.card("<div class='two'>" + img + "<div><b style='font-size:18px'>" + Html.esc(me.name) + "</b><br>" + Html.pill(me.accountVerified ? "Verified" : "Pending")
                + "<br><span class='mu'>ID " + me.id + "<br>Born " + me.dob + " · " + Html.esc(me.gender) + "<br>" + Html.esc(me.phone) + "<br>" + Html.esc(me.address) + "</span></div></div>")
            + Html.label("EVERY DEPARTMENT RELIES ON THIS ONE RECORD")
            + Html.chips(List.of("Traffic", "Finance", "Pensions", "Police", "Passport"), true)
            + "<a class='sm' style='text-align:center' href='/docs'>Upload a photo or document</a><a class='sm' style='text-align:center' href='/access'>See who accessed my data</a>";
        return Resp.html(Html.page("Profile", B, "/home", body));
    }

    static Resp services(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        Models.Dept d = dept(r.get("dept"));
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Pick a service. Your details are filled in for you."));
        String cat = "";
        for (Policy.Service s : Policy.of(d)) {
            if (!s.category.equals(cat)) { cat = s.category; sb.append(Html.label(cat.toUpperCase())); }
            sb.append(Html.row(s.name, s.desc, "<span class='pl m'>" + (s.fee == 0 ? "Free" : "M " + s.fee) + "</span>", "/apply?dept=" + d.name() + "&service=" + s.name.replace(" ", "%20").replace("'", "%27").replace("(", "%28").replace(")", "%29")));
        }
        return Resp.html(Html.page(d.label, B, "/home", sb.toString()));
    }

    static Resp apply(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        Models.Dept d = dept(r.get("dept"));
        Policy.Service s = Policy.service(d, r.get("service"));
        if (s == null) return Resp.to("/home", "err:Unknown service.");
        String back = "/services?dept=" + d.name();
        if (r.post()) {
            if (!me.accountVerified) return Resp.to(back, "err:Home Affairs must confirm your account first.");
            LocalDate today = LocalDate.now();
            if (s.pension) {
                String typed = Store.norm(r.get("idnumber"));
                if (!typed.equals(me.id)) return Resp.to("/apply?dept=PE&service=" + s.name.replace(" ", "%20"), "err:Enter your own ID number. It must match your account.");
                int age = Policy.age(me, today);
                if (age < Policy.PENSION_AGE) {
                    Application a = Store.addApp(me.id, d, s.name, Status.REJECTED, 0, "Rejected automatically: age " + age + ". Pension applications open at " + Policy.PENSION_AGE + ".");
                    Store.notify(me.id, "Your " + s.name + " was rejected: you are " + age + " and pensions open at " + Policy.PENSION_AGE + ". Reference " + a.reference + ".");
                    return Resp.to("/app?id=" + a.id, "err:Rejected. Your ID shows you are " + age + " years old. You can apply from " + Policy.turnsPensionAge(me) + ".");
                }
                if (!me.alive) return Resp.to(back, "err:Home Affairs records this person as deceased.");
                if (Store.enrolled(me.id)) return Resp.to("/pension", "err:You are already receiving a pension.");
            }
            List<String> missing = new ArrayList<>();
            for (String doc : s.docs) { Doc x = Store.docOf(me.id, doc); if (x == null) missing.add(doc); }
            if (!missing.isEmpty()) return Resp.to("/docs", "err:Upload these first: " + String.join(", ", missing) + ". Then apply for " + s.name + ".");
            if (Store.hasOpen(me.id, s.name)) return Resp.to("/apps", "err:You already have an open " + s.name + " application.");
            Application a = Store.addApp(me.id, d, s.name, s.fee > 0 ? Status.PAYMENT_DUE : Status.VERIFIED, s.fee, r.get("note"));
            Store.notify(me.id, "Your " + s.name + " application was received. Reference " + a.reference + (s.fee > 0 ? ". Pay M " + s.fee + " to continue." : "."));
            return Resp.to("/app?id=" + a.id, s.fee > 0 ? "ok:Application received. Pay the fee to continue." : "ok:Application sent. Your identity is already verified, nothing to re-submit.");
        }
        StringBuilder docs = new StringBuilder();
        for (String doc : s.docs) {
            Doc x = Store.docOf(me.id, doc);
            docs.append(Html.row(doc, x == null ? "Not uploaded yet" : "Already on file, reused", Html.pill(x == null ? "Missing" : x.status), null));
        }
        String body = Html.banner(r.get("m")) + Html.intro(s.name) + Html.mu(s.desc)
            + Html.card("<b>Filled in from Home Affairs</b><span class='mu'>Name: " + Html.esc(me.name) + "<br>ID: " + me.id + "<br>Date of birth: " + me.dob + "</span>")
            + Html.card("<b>Fee</b><span class='mu'>" + (s.fee == 0 ? "Free" : "M " + s.fee + " (paid online, Finance issues the receipt)") + "</span>")
            + (s.docs.isEmpty() ? "" : Html.label("DOCUMENTS NEEDED") + docs)
            + Html.form("/apply", Html.hidden("dept", d.name()) + Html.hidden("service", s.name)
                + (s.pension ? Html.card("<b>Age check</b><span class='mu'>Pensions are for citizens aged " + Policy.PENSION_AGE + " and above. Type your ID number so Home Affairs can confirm your age.</span>") + Html.field("Your ID number", "idnumber", "text", "") : "")
                + Html.field("Note for the officer (optional)", "note", "text", "") + "<br>" + Html.button("Send application"));
        return Resp.html(Html.page("Apply", d.label, back, body));
    }

    static Resp apps(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Tap an application to see where it is."));
        List<Application> l = Store.appsOf(me.id);
        for (Application a : l) sb.append(Html.row(a.service, a.dept.label + " · " + a.reference, Html.pill(a.status.label), "/app?id=" + a.id));
        if (l.isEmpty()) sb.append(Html.mu("No applications yet."));
        return Resp.html(Html.page("My Applications", B, "/home", sb.toString()));
    }

    private static Application mine(Req r, Citizen me) {
        try { Application a = Store.app(Integer.parseInt(r.get("id"))); return a != null && a.citizenId.equals(me.id) ? a : null; } catch (Exception e) { return null; }
    }

    static Resp appDetail(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        Application a = mine(r, me);
        if (a == null) return Resp.to("/apps", "err:Application not found.");
        String body = Html.banner(r.get("m")) + Html.intro(a.service)
            + Html.card("<span class='mu'>You are here</span><b style='font-size:20px'>" + Html.esc(a.status.label) + "</b><span class='mu'>Reference " + a.reference + " · " + a.dept.label
                + (a.fee > 0 ? "<br>Fee M " + a.fee + (a.paid ? " paid · receipt " + a.receipt : " not paid yet") : "") + "</span>")
            + (a.status == Status.PAYMENT_DUE ? "<a class='bt' href='/pay?id=" + a.id + "'>Pay M " + a.fee + " now</a>" : "")
            + (a.status == Status.NEEDS_INFO || a.status == Status.REJECTED ? "<div class='err'>! " + (a.status == Status.REJECTED ? "Rejected" : "The officer needs more information") + (a.note.isEmpty() ? "." : ": " + Html.esc(a.note)) + "</div>" : "")
            + Html.steps(Policy.STEPS, Policy.stepIndex(a.status));
        return Resp.html(Html.page("Application Tracking", "My Applications", "/apps", body));
    }

    /** Fee payment. Finance sees the payment and the receipt straight away. */
    static Resp pay(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        Application a = mine(r, me);
        if (a == null || a.status != Status.PAYMENT_DUE) return Resp.to("/apps", "err:Nothing to pay for this application.");
        if (r.post()) {
            if (r.get("account").length() < 5) return Resp.to("/pay?id=" + a.id, "err:Enter the phone number or card number you are paying with.");
            String receipt = Store.nextReceipt();
            a.paid = true; a.receipt = receipt; a.status = Status.VERIFIED;
            Store.saveApp(a);
            Store.addPayment(me.id, "FEE", a.reference + " " + a.service, a.fee, null, true, receipt, a.id);
            Store.notify(me.id, "Payment of M " + a.fee + " received by Finance for " + a.service + ". Receipt " + receipt + ".");
            return Resp.to("/app?id=" + a.id, "ok:Payment received. Receipt " + receipt + ". " + a.dept.label + " can now process your application.");
        }
        String body = Html.banner(r.get("m")) + Html.intro("Pay M " + a.fee + " for " + a.service)
            + Html.mu("This is a prototype. No real money moves. Finance Services records the payment and issues the receipt.")
            + Html.form("/pay", Html.hidden("id", String.valueOf(a.id)) + Html.select("Pay with", "method", "Mobile money", "Bank card", "Bank transfer")
                + Html.field("Phone or card number", "account", "text", "") + "<br>" + Html.button("Pay M " + a.fee));
        return Resp.html(Html.page("Pay Fee", "Application", "/app?id=" + a.id, body));
    }

    static Resp docs(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        if (r.post()) {
            String type = r.get("type");
            Part f = r.files.get("file");
            if (!Policy.DOC_TYPES.contains(type)) return Resp.to("/docs", "err:Choose what kind of document this is.");
            if (f == null) return Resp.to("/docs", "err:Choose a file from your computer first.");
            String ext = detect(f.data);
            if (ext == null) return Resp.to("/docs", "err:Only JPG, PNG or PDF files are allowed.");
            String stored = UUID.randomUUID() + "." + ext;
            try { Files.write(Store.UPLOADS.resolve(stored), f.data); } catch (IOException e) { return Resp.to("/docs", "err:Could not save the file. Try again."); }
            String mime = ext.equals("pdf") ? "application/pdf" : ext.equals("png") ? "image/png" : "image/jpeg";
            Store.putDoc(me.id, type, safeName(f.filename), stored, mime, "Uploaded");
            return Resp.to("/docs", "ok:" + type + " uploaded. Home Affairs will verify it, and every department can reuse it.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Upload each document once. Every department can reuse it."));
        for (Doc d : Store.docsOf(me.id)) {
            String view = d.stored == null ? "<span class='mu'>paper record</span>" : "<a class='sm' href='/file?id=" + d.id + "' target='_blank'>View</a>";
            String thumb = d.stored != null && d.mime.startsWith("image/") ? "<img class='ph' style='width:56px;height:56px' src='/file?id=" + d.id + "' alt=''>" : "";
            sb.append("<div class='rw'>").append(thumb).append("<div><b>").append(Html.esc(d.type)).append("</b><small>").append(Html.esc(d.filename)).append("</small></div>")
              .append(Html.pill(d.status)).append(view).append("</div>");
        }
        List<String> opts = new ArrayList<>();
        for (String t : Policy.DOC_TYPES) opts.add(t);
        sb.append(Html.label("UPLOAD OR REPLACE A DOCUMENT")).append(Html.uploadForm("/docs", Html.select("What is it?", "type", opts.toArray(new String[0])), "Upload"));
        return Resp.html(Html.page("My Documents", B, "/home", sb.toString()));
    }

    /** Checks the first bytes of the file, not just its name. */
    static String detect(byte[] d) {
        if (d.length > 4 && d[0] == (byte) 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "png";
        if (d.length > 3 && d[0] == (byte) 0xFF && d[1] == (byte) 0xD8) return "jpg";
        if (d.length > 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return "pdf";
        return null;
    }

    static String safeName(String n) { return n == null ? "file" : n.replaceAll("[^A-Za-z0-9._ -]", "_"); }

    static Resp pension(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        LocalDate today = LocalDate.now();
        int age = Policy.age(me, today);
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Your pension. Pensions are for citizens aged " + Policy.PENSION_AGE + " and above."));
        sb.append(Html.card("<b>Age " + age + "</b><span class='mu'>Born " + me.dob + " (from Home Affairs)</span>"));
        String applyHref = "/apply?dept=PE&service=Old-Age%20Pension%20Application";
        if (Store.enrolled(me.id)) {
            sb.append(Html.card("<b>You receive a pension</b><span class='mu'>M " + Policy.PENSION_AMOUNT + " per month, paid around the " + Policy.payday(YearMonth.from(today)).getDayOfMonth() + "th</span>" + Html.pill("Enrolled")));
            sb.append(Html.label("MY PENSION PAYMENTS"));
            for (Payment p : Store.paymentsOf(me.id))
                if (p.kind.equals("PENSION")) sb.append(Html.row(p.month, "M " + p.amount + (p.receipt == null ? "" : " · receipt " + p.receipt), Html.pill(p.paid ? "Paid" : "Sent to Finance"), null));
        } else if (age >= Policy.PENSION_AGE) {
            sb.append(Html.card("<b>You can apply</b><span class='mu'>You are old enough. Apply and Pensions will check your ID with Home Affairs.</span>")).append("<a class='bt' href='" + applyHref + "'>Apply for a pension</a>");
        } else {
            Period p = Period.between(today, Policy.turnsPensionAge(me));
            sb.append(Html.card("<b>Not yet</b><span class='mu'>You can apply from " + Policy.turnsPensionAge(me) + " (in " + p.getYears() + " years, " + p.getMonths() + " months).</span>" + Html.pill("Rejected")))
              .append("<a class='sm' style='text-align:center' href='" + applyHref + "'>Try to apply anyway (the system will check)</a>");
        }
        return Resp.html(Html.page("My Pension", B, "/home", sb.toString()));
    }

    static Resp book(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        if (r.post()) {
            Models.Dept d = dept(r.get("dept"));
            Ticket t = Store.addTicket(me.id, d, r.get("service").isEmpty() ? "General enquiry" : r.get("service"));
            return Resp.to("/book", "ok:Your queue number is " + t.number + " at " + d.label + ".");
        }
        List<String> opts = new ArrayList<>();
        for (Models.Dept d : Models.Dept.values()) opts.add(d.name() + "|" + d.label);
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Book a time and skip the long queue.")
            + Html.form("/book", Html.select("Department", "dept", opts.toArray(new String[0])) + Html.field("Service", "service", "text", "") + "<br>" + Html.button("Get my queue number"))
            + Html.label("MY QUEUE NUMBERS"));
        for (Ticket t : Store.ticketsOf(me.id)) sb.append(Html.row(t.number, t.dept.label + " · " + t.service, Html.pill(t.served ? "Completed" : "Pending"), null));
        return Resp.html(Html.page("Appointments", B, "/home", sb.toString()));
    }

    static Resp notifs(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        StringBuilder sb = new StringBuilder(Html.intro("Updates from every department in one place."));
        for (Notification n : Store.notesOf(me.id)) sb.append(Html.row(n.text, n.time.format(WHEN), null, null));
        return Resp.html(Html.page("Notifications", B, "/home", sb.toString()));
    }

    static Resp access(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        StringBuilder sb = new StringBuilder(Html.intro("Every look at your profile is written down."));
        List<AccessEntry> l = Store.logOf(me.id);
        for (AccessEntry e : l) sb.append(Html.row(e.dept.label + " · " + e.officer, e.fields + " · " + e.purpose + " · " + e.time.format(WHEN), Html.pill("Logged"), null));
        if (l.isEmpty()) sb.append(Html.mu("Nobody has looked at your profile yet."));
        return Resp.html(Html.page("Who Accessed My Data", B, "/home", sb.toString()));
    }

    static Models.Dept dept(String s) { try { return Models.Dept.valueOf(s); } catch (Exception e) { return Models.Dept.HA; } }
}
