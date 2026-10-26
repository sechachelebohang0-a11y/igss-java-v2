package lesotho.igss;

import lesotho.igss.Models.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Everything a citizen sees. A citizen only ever sees their own information. */
final class CitizenPages {
    private CitizenPages() {}

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final String B = "Dashboard";

    /** Appointment times: every 30 minutes from 08:00 to 15:30. */
    private static final List<LocalTime> SLOTS = new ArrayList<>();
    static { for (LocalTime t = LocalTime.of(8, 0); !t.isAfter(LocalTime.of(15, 30)); t = t.plusMinutes(30)) SLOTS.add(t); }

    private static final String[] SERVICE_IDEAS = {"Make a national ID card", "Renew my ID card", "Make a driving licence", "Renew my driving licence",
            "Collect a document", "General enquiry"};

    private static Resp needLogin() { return Resp.redirect("/login"); }

    /** Link to the booking page with the department (and service) already chosen. */
    static String bookHref(Models.Dept d, String service) {
        return "/book?dept=" + d.name() + (service == null || service.isEmpty() ? "" : "&service=" + URLEncoder.encode(service, StandardCharsets.UTF_8));
    }

    private static String bookLink(Models.Dept d, String service, String text) {
        return "<a class='sm' style='text-align:center' href='" + bookHref(d, service) + "'>" + Html.esc(text) + "</a>";
    }

    private static String or(String s, String other) { return s == null || s.trim().isEmpty() ? other : s; }

    static Resp landing(Req r) {
        String body = Html.intro("One ID. One Profile. All Services.")
                + Html.tiles(List.of(new String[]{"/login", "user", "Citizen Login"}, new String[]{"/register", "id", "Sign Up"}, new String[]{"/staff/login", "lock", "Department Official"}))
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
        String idv = "", idErr = "", pwErr = "";
        if (r.post()) {
            idv = r.get("id");
            String pw = r.p.getOrDefault("password", "");
            Citizen c = Store.citizen(idv);
            if (idv.isEmpty()) idErr = "Enter your ID number.";
            else if (c == null) idErr = "Couldn't find that ID number. Check it and try again.";
            else if (!c.hasAccount()) idErr = "This ID number has no account yet. Go back and tap Sign Up.";
            else if (pw.isEmpty()) pwErr = "Enter your password.";
            else if (!Store.hash(c.salt, pw).equals(c.hash)) pwErr = "Wrong password. Try again.";
            else {
                Resp resp = Resp.redirect("/home");
                resp.newSession = new Sess("C", c.id);
                return resp;
            }
        }
        return Resp.html(Html.page("Citizen Login", "Back", "/", Html.banner(r.get("m"))
                + Html.intro("Welcome back. Log in to use all government services with one profile.")
                + Html.form("/login", Html.fieldErr("ID number", "id", "text", idv, idErr) + Html.fieldErr("Password", "password", "password", "", pwErr) + "<br>" + Html.button("Login"))));
    }

    static Resp home(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        List<String[]> t = new ArrayList<>();
        for (Models.Dept d : Models.Dept.values()) t.add(new String[]{"/services?dept=" + d.name(), d.icon, d.label});
        t.add(new String[]{"/apps", "doc", "My Applications"});
        t.add(new String[]{"/docs", "folder", "My Documents"});
        t.add(new String[]{"/photo", "user", "My Photo"});
        t.add(new String[]{"/fingerprints", "fp", "My Fingerprints"});
        t.add(new String[]{"/pension", "coin", "My Pension"});
        t.add(new String[]{"/book", "cal", "Appointments & Queue"});
        t.add(new String[]{"/notifs", "bell", "Notifications"});
        t.add(new String[]{"/access", "lock", "Who Accessed My Data"});
        t.add(new String[]{"/profile", "user", "Profile"});
        StringBuilder body = new StringBuilder(Html.banner(r.get("m")));
        body.append(Html.card("<div class='two'>" + Html.avatar(me, true, 64) + "<div><b>" + Html.esc(me.name) + "</b><span class='mu'>ID " + me.id + "</span><br>"
                + Html.pill(me.accountVerified ? "Verified" : "Pending") + "</div></div>"));
        if (!me.accountVerified) body.append("<div class='err'>! Your account is waiting for Home Affairs to confirm your name. You cannot apply yet.</div>");
        body.append(Html.tiles(t)).append(Html.label("RECENT UPDATES"));
        int n = 0;
        for (Notification x : Store.notesOf(me.id)) { if (n++ == 3) break; body.append(Html.row(x.text, x.time.format(WHEN), null, "/notifs")); }
        body.append("<a class='sm' style='text-align:center' href='/logout'>Log out</a>");
        return Resp.html(Html.page("Welcome back, " + me.name.split(" ")[0] + "!", null, null, body.toString()));
    }

    static Resp profile(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        LocalDate today = LocalDate.now();
        Doc photo = Store.docOf(me.id, "Passport photo");
        boolean hasPhoto = photo != null && photo.stored != null;
        Doc fpDoc = Store.docOf(me.id, "Fingerprints");
        StringBuilder sb = new StringBuilder(Html.intro("Your verified profile. Home Affairs confirmed it once."));
        sb.append(Html.card("<div class='two'>" + Html.avatar(me, true, 110) + "<div><b style='font-size:18px'>" + Html.esc(me.name) + "</b><br>"
                + Html.pill(me.accountVerified ? "Verified" : "Pending") + "<br><span class='mu'>ID " + Html.esc(me.id) + "</span></div></div>"
                + (hasPhoto ? "" : "<span class='mu'>No photo yet. Tap My Photo below. It will then show here and everywhere your details appear.</span>")));
        sb.append(Html.label("PERSONAL DETAILS"));
        sb.append(Html.row(me.name, "Full name", null, null));
        sb.append(Html.row(me.id, "ID number", null, null));
        sb.append(Html.row(me.dob + " (age " + Policy.age(me, today) + ")", "Date of birth", null, null));
        sb.append(Html.row(or(me.gender, "Not on record"), "Gender", null, null));
        sb.append(Html.row(or(me.phone, "Not provided"), "Phone", null, null));
        sb.append(Html.row(or(me.email, "Not provided"), "Email", null, null));
        sb.append(Html.row(or(me.address, "Not provided"), "Address", null, null));
        sb.append(Html.label("ACCOUNT"));
        sb.append(Html.row(me.alive ? "Alive" : "Deceased", "Life status (Home Affairs)", Html.pill(me.alive ? "Alive" : "Deceased"), null));
        sb.append(Html.row(me.accountVerified ? "Verified by Home Affairs" : "Waiting for Home Affairs", "Account status", Html.pill(me.accountVerified ? "Verified" : "Pending"), null));
        sb.append(Html.row(hasPhoto ? "On file (" + photo.status + ")" : "No photo yet", "Photo", null, "/photo"));
        sb.append(Html.row(fpDoc != null && fpDoc.stored != null ? "Saved (" + fpDoc.status + ")" + (me.fingerprint == null || me.fingerprint.isEmpty() ? "" : " · code " + me.fingerprint) : "Not scanned yet", "Fingerprints", null, "/fingerprints"));
        sb.append(Html.row(Store.enrolled(me.id) || Store.pensionEnrolled(me.id) ? "Receiving " + (Store.enrolled(me.id) && Store.pensionEnrolled(me.id) ? "the grant and the pension" : Store.enrolled(me.id) ? "the old-age grant" : "the retirement pension")
                : Store.govtWorker(me.id) ? "Government employee" + (Store.benefitChoice(me.id) == null ? "" : " (chose the " + Policy.benefitLabel(Store.benefitChoice(me.id)).toLowerCase() + ")") : "Private sector, not on a benefit register", "Grant and pension", null, "/pension"));
        sb.append(Html.label("EVERY DEPARTMENT RELIES ON THIS ONE RECORD"))
                .append(Html.chips(List.of("Traffic", "Finance", "Pensions", "Police", "Passport"), true))
                .append("<a class='sm' style='text-align:center' href='/photo'>Add or change my photo</a><a class='sm' style='text-align:center' href='/fingerprints'>Scan my fingerprints</a>"
                        + "<a class='sm' style='text-align:center' href='/docs'>Upload a document</a><a class='sm' style='text-align:center' href='/access'>See who accessed my data</a>");
        return Resp.html(Html.page("Profile", B, "/home", sb.toString()));
    }

    /** The citizen's own picture. It shows on the profile, on every form and for staff who may see photos. */
    static Resp photo(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        if (r.post()) {
            Part p = r.files.get("photo");
            if (p == null) return Resp.to("/photo", "err:Choose a photo, or take one with the camera, first.");
            String why = FormKit.fileError(p, false);
            if (why != null) return Resp.to("/photo", "err:Photo " + why);
            String mime = FormKit.sniff(p.data);
            String stored = UUID.randomUUID() + FormKit.extension(mime);
            try { Files.write(Store.UPLOADS.resolve(stored), p.data); } catch (IOException e) { return Resp.to("/photo", "err:Could not save the photo. Try again."); }
            Store.putDoc(me.id, "Passport photo", FormKit.safeName(p.filename), stored, mime, "Uploaded");
            Store.notify(me.id, "Your photo was saved. Home Affairs will verify it.");
            return Resp.to("/photo", "ok:Your photo was saved. It now shows on your profile and on your forms.");
        }
        Doc photo = Store.docOf(me.id, "Passport photo");
        boolean has = photo != null && photo.stored != null;
        String body = Html.banner(r.get("m")) + Html.intro("Your photo is used on your profile and on every form you send.")
                + Html.card("<div class='two'>" + Html.avatar(me, true, 110) + "<div><b>" + Html.esc(me.name) + "</b><span class='mu'>"
                + (has ? "Photo on file (" + Html.esc(photo.status) + ")" : "No photo yet") + "</span></div></div>")
                + Html.label(has ? "REPLACE MY PHOTO" : "ADD MY PHOTO")
                + "<form id='appform' method='post' action='/photo' enctype='multipart/form-data' autocomplete='off'>"
                + FormKit.photoInner().replace("id='photo'", "id='photo' data-need='1'") + "<br>" + Html.button("Save photo") + "</form>"
                + "<script>" + FormKit.CAMERA_JS + "</script>";
        return Resp.html(Html.page("My Photo", B, "/home", body));
    }

    /** What Finance holds for this citizen: fees paid, grant and pension payments, and tax held back for LRA. */
    private static String financeMine(Citizen me) {
        StringBuilder sb = new StringBuilder();
        int owed = Store.taxOwed(me.id);
        sb.append(Html.label("MY TAX STANDING (FROM LRA)")).append(Html.card(owed <= 0 ? "<b>No tax owed</b><span class='mu'>Nothing is taken from your grant or pension.</span>"
                : "<b>M " + owed + " owed to LRA</b><span class='mu'>This is taken from your next grant or pension payment and sent to LRA. Finance does not collect tax itself.</span>"));
        sb.append(Html.label("MY GRANT AND PENSION PAYMENTS"));
        int n = 0;
        for (Models.Payment p : Store.benefitPaymentsOf(me.id)) {
            n++;
            sb.append(Html.row(Policy.benefitLabel(p.kind) + " · " + p.month, "M " + p.amount + (p.paid && p.receipt != null ? " · receipt " + p.receipt : ""), Html.pill(p.paid ? "Paid" : "Pending"), null));
        }
        if (n == 0) sb.append(Html.mu("No payments yet."));
        List<String[]> lra = Store.lraOf(me.id);
        if (!lra.isEmpty()) {
            sb.append(Html.label("TAX TAKEN AND SENT TO LRA"));
            for (String[] t : lra) sb.append(Html.row("M " + t[0], t[1], "", null));
        }
        sb.append(Html.label("MY FEE PAYMENTS (RECEIPTS)"));
        int f = 0;
        for (Models.Payment p : Store.paymentsOf(me.id)) {
            if (!p.kind.equals("FEE") || !p.paid) continue;
            f++;
            sb.append(Html.row(p.ref == null ? "Service fee" : p.ref, "M " + p.amount + (p.receipt != null ? " · receipt " + p.receipt : ""), Html.pill("Paid"), null));
        }
        if (f == 0) sb.append(Html.mu("No fees paid yet."));
        return sb.toString();
    }

    /** My driving licence, vehicles, roadworthy certificates and traffic fines. */
    private static String trafficMine(Citizen me) {
        StringBuilder sb = new StringBuilder();
        List<Models.TrafficRec> recs = Store.trafficOf(me.id);
        List<String[]> certs = Certificates.of(me.id);
        sb.append(Html.label("MY CERTIFICATES AND LICENCES"));
        for (String[] c : certs) sb.append(Html.row(c[2] + " · " + c[3], (Certificates.valid(c) ? "Valid until " : "Expired on ") + c[6], Html.pill(Certificates.valid(c) ? "Valid" : "Expired"), "/certificate?id=" + c[0]));
        if (certs.isEmpty()) sb.append(Html.mu("You have none yet. Pass a test or an inspection and it appears here."));
        sb.append(Html.label("MY LICENCE, VEHICLES AND CERTIFICATES"));
        for (Models.TrafficRec t : recs) sb.append(Html.row(t.kind, t.detail, Html.pill(t.status), null));
        if (recs.isEmpty()) sb.append(Html.mu("Nothing on the traffic register yet."));
        List<String[]> fines = Store.finesOf(me.id);
        if (!fines.isEmpty()) {
            sb.append(Html.label("MY TRAFFIC FINES (PAY AT THE TRAFFIC OFFICE)"));
            for (String[] f : fines) sb.append(Html.row(f[2], "M " + f[3] + " · " + f[4] + " · " + f[5] + (f[8] == null || f[8].isEmpty() ? "" : " · receipt " + f[8]), Html.pill(f[7]), null));
        }
        return sb.toString();
    }

    static Resp services(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        Models.Dept d = dept(r.get("dept"));
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Pick a service. Your details are filled in for you."));
        if (d == Models.Dept.FI) sb.append(Html.card("<b>Finance Services</b><span class='mu'>" + Html.esc(Policy.FINANCE_NOTE) + "</span>"));
        if (d == Models.Dept.FI) sb.append(financeMine(me));
        if (d == Models.Dept.TR) sb.append(trafficMine(me));
        if (d == Models.Dept.PE) sb.append(Html.card("<b>How pensions work</b><span class='mu'>" + Html.esc(Policy.PENSION_RULE) + "</span>"));
        if (d == Models.Dept.HA) sb.append(bookLink(d, "Fingerprints for my ID card", "Book an appointment to give fingerprints for my ID card"));
        if (d == Models.Dept.TR) {
            sb.append(Html.card("<b>Tests and certificates</b><span class='mu'>Book a test or an inspection and come to the Traffic office. When you pass, Traffic Services issues your learner's licence, driving licence or roadworthy certificate. You get it here and can print it.</span>"));
            sb.append(bookLink(d, "Learner's licence test", "Book a learner's licence test"));
            sb.append(bookLink(d, "Driving test", "Book a driving test"));
            sb.append(bookLink(d, "Roadworthy inspection", "Book a roadworthy inspection for my vehicle"));
        }
        sb.append(bookLink(d, "", "Book an appointment or join the queue at " + d.label));
        String cat = "";
        for (Policy.Service s : Policy.of(d)) {
            if (s.name.equals("First-time ID Card") && Store.hasIdCard(me.id)) continue;
            if ((s.name.equals("ID Renewal") || s.name.startsWith("ID Replacement")) && !Store.hasIdCard(me.id)) continue;
            if (!s.category.equals(cat)) { cat = s.category; sb.append(Html.label(cat.toUpperCase())); }
            String sub = s.desc + (Registry.NEEDS_APPOINTMENT.contains(s.name) ? " Needs a booked appointment." : "");
            sb.append(Html.row(s.name, sub, "<span class='pl m'>" + (s.fee == 0 ? "Free" : "M " + s.fee) + "</span>", "/apply?dept=" + d.name() + "&service=" + s.name.replace(" ", "%20").replace("'", "%27").replace("(", "%28").replace(")", "%29")));
        }
        return Resp.html(Html.page(d.label, B, "/home", sb.toString()));
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
        String office = Store.collectionOf(a.id);
        if (office == null) office = Policy.officeFor(me.address);
        String body = Html.banner(r.get("m")) + Html.intro(a.service)
                + Html.card("<span class='mu'>You are here</span><b style='font-size:20px'>" + Html.esc(a.status.label) + "</b><span class='mu'>Reference " + a.reference + " · " + a.dept.label
                + (a.fee > 0 ? "<br>Fee M " + a.fee + (a.paid ? " paid · receipt " + a.receipt : " not paid yet") : "") + "</span>")
                + (a.status == Status.READY ? Html.card("<b>Collect it here</b><span class='mu'>" + Html.esc(a.dept.label) + " office in <b>" + Html.esc(office)
                + "</b>, Monday to Friday, 08:00 to 16:00. Bring your ID card (or your ID number) and the reference " + Html.esc(a.reference) + ".</span>") : "")
                + (a.status == Status.PAYMENT_DUE ? "<a class='bt' href='/pay?id=" + a.id + "'>Pay M " + a.fee + " now</a>" : "")
                + ServiceForms.viewLink(a.id)
                + bookLink(a.dept, a.service, "Book an appointment for this application")
                + (a.note.isEmpty() || a.status == Status.NEEDS_INFO || a.status == Status.REJECTED ? "" : Html.card("<b>Note from the officer</b><span class='mu'>" + Html.esc(a.note) + "</span>"))
                + (a.status == Status.NEEDS_INFO || a.status == Status.REJECTED ? "<div class='err'>! " + (a.status == Status.REJECTED ? "Rejected" : "The officer needs more information") + (a.note.isEmpty() ? "." : ": " + Html.esc(a.note)) + "</div>" : "")
                + Html.steps(Policy.steps(a), Policy.stepIndex(a));
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
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Upload each document once. Every department can reuse it.")
                + "<a class='sm' style='text-align:center' href='/photo'>Your profile picture goes in My Photo</a>");
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

    /** Sample fingerprint pattern, drawn from the ID number. Not a real fingerprint. */
    private static String fingerprintSvg(String seedText) {
        Random rnd = new Random(seedText.hashCode());
        StringBuilder sb = new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 240' width='400' height='480'><rect width='200' height='240' fill='white'/>"
                + "<g fill='none' stroke='#0a2a8f' stroke-width='2.2' stroke-linecap='round'>");
        double cx = 100 + rnd.nextInt(11) - 5, cy = 122 + rnd.nextInt(11) - 5;
        for (int i = 1; i <= 15; i++) {
            double rx = 5 + i * 6.0 + rnd.nextDouble() * 2, ry = 7 + i * 7.2 + rnd.nextDouble() * 2;
            double gap = 0.35 + rnd.nextDouble() * 0.9, start = rnd.nextDouble() * Math.PI * 2;
            double a1 = start, a2 = start + Math.PI * 2 - gap;
            double x1 = cx + rx * Math.cos(a1), y1 = cy + ry * Math.sin(a1), x2 = cx + rx * Math.cos(a2), y2 = cy + ry * Math.sin(a2);
            sb.append(String.format(Locale.ROOT, "<path d='M%.1f %.1f A%.1f %.1f 0 1 1 %.1f %.1f'/>", x1, y1, rx, ry, x2, y2));
        }
        return sb.append("</g></svg>").toString();
    }

    private static final String SCAN_CSS = """
            .pad{position:relative;width:160px;height:200px;border:3px solid #1a4fb4;border-radius:22px;background:#f1f5fd;overflow:hidden;display:flex;align-items:center;justify-content:center;margin:0 auto}
            .pad svg{width:110px;height:140px;stroke:#1a4fb4;stroke-width:1.4;opacity:.35;transition:opacity .4s}
            .pad.on svg,.pad.done svg{opacity:1}
            .pad .line{position:absolute;left:0;right:0;top:0;height:4px;background:#2f6fdc;box-shadow:0 0 12px 4px #2f6fdc88;display:none}
            .pad.on .line{display:block;animation:sweep 1.1s ease-in-out infinite alternate}
            @keyframes sweep{from{top:0}to{top:calc(100% - 4px)}}
            .pad.done{border-color:#0a8a3a;background:#e6f4ea}.pad.done svg{stroke:#0a8a3a}
            .pbar{width:100%;max-width:260px;height:10px;border-radius:6px;background:#c9d6f0;overflow:hidden;margin:0 auto}.pbar i{display:block;height:100%;width:0;background:#1a4fb4}
            #scanmsg{text-align:center;font-size:14px;font-weight:700;min-height:20px}
            """;

    private static final String SCAN_JS = """
            (function(){
            var st=document.documentElement.lang==='st';
            var M=st?['Beha monoana oa hau skeneng','Boloka monoana o sa sisinyehe. Ho a skena...','Ho bala mela ea monoana...','Ho hlahloba paterone...','Monoana o nkiloe.']
                    :['Place your finger on the scanner','Keep your finger still. Scanning...','Reading the ridges...','Checking the pattern...','Fingerprint captured.'];
            var b=document.getElementById('scanbtn'),pad=document.getElementById('pad'),bar=document.querySelector('.pbar i'),msg=document.getElementById('scanmsg'),f=document.getElementById('scanform');
            b.addEventListener('click',function(){
              b.disabled=true; pad.className='pad on'; msg.textContent=M[0];
              [[800,1],[2200,2],[3400,3]].forEach(function(s){ setTimeout(function(){ msg.textContent=M[s[1]]; },s[0]); });
              var p=0,t=setInterval(function(){ p+=2; bar.style.width=p+'%';
                if(p>=100){ clearInterval(t); pad.className='pad done'; msg.textContent=M[4]; setTimeout(function(){ f.submit(); },600); } },80);
            });
            })();
            """;

    /** The citizen's own fingerprints. They are scanned here (demo scanner) or uploaded as an image. Police can use them in a criminal investigation. */
    static Resp fingerprints(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        if (r.post()) {
            if (r.get("act").equals("scan")) {
                String code = Store.ensureFingerprintCode(me.id);
                String stored = UUID.randomUUID() + ".svg";
                try { Files.write(Store.UPLOADS.resolve(stored), fingerprintSvg(me.id).getBytes(StandardCharsets.UTF_8)); }
                catch (IOException e) { return Resp.to("/fingerprints", "err:Could not save the scan. Try again."); }
                Store.putDoc(me.id, "Fingerprints", "fingerprint-scan.svg", stored, "image/svg+xml", "Verified");
                Store.notify(me.id, "Your fingerprint was scanned and saved. Your fingerprint code is " + code + ".");
                return Resp.to("/fingerprints", "ok:Fingerprint scanned and saved. Your fingerprint code is " + code + ".");
            }
            Part f = r.files.get("file");
            if (f == null) return Resp.to("/fingerprints", "err:Choose a file from your computer first.");
            if (f.data.length > 5 * 1024 * 1024) return Resp.to("/fingerprints", "err:The file must be 5 MB or smaller.");
            String ext = detect(f.data);
            if (ext == null) return Resp.to("/fingerprints", "err:Only JPG, PNG or PDF files are allowed.");
            String stored = UUID.randomUUID() + "." + ext;
            try { Files.write(Store.UPLOADS.resolve(stored), f.data); } catch (IOException e) { return Resp.to("/fingerprints", "err:Could not save the file. Try again."); }
            String mime = ext.equals("pdf") ? "application/pdf" : ext.equals("png") ? "image/png" : "image/jpeg";
            Store.putDoc(me.id, "Fingerprints", safeName(f.filename), stored, mime, "Uploaded");
            Store.ensureFingerprintCode(me.id);
            Store.notify(me.id, "Your fingerprints were uploaded. Home Affairs will verify them.");
            return Resp.to("/fingerprints", "ok:Fingerprints uploaded. Home Affairs will verify them.");
        }
        Doc fp = Store.docOf(me.id, "Fingerprints");
        Citizen fresh = Store.citizen(me.id);
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Your fingerprints are kept by Home Affairs and tied to your ID number."));
        sb.append(Html.card("<b>Who can use them</b><span class='mu'>Police can use them to identify a person in a criminal investigation, and must give a case number each time. "
                + "Police only get your home address when Home Affairs approves a request. Every look is written in Who Accessed My Data.</span>"));
        if (fp != null && fp.stored != null) {
            String thumb = fp.mime != null && fp.mime.startsWith("image/") ? "<img class='ph' style='width:56px;height:56px' src='/file?id=" + fp.id + "' alt=''>" : "";
            sb.append("<div class='rw'>").append(thumb).append("<div><b>Fingerprints</b><small>").append(Html.esc(fp.filename)).append(" · ").append(fp.uploaded.format(WHEN))
                    .append(fresh.fingerprint == null || fresh.fingerprint.isEmpty() ? "" : " · code " + Html.esc(fresh.fingerprint)).append("</small></div>")
                    .append(Html.pill(fp.status)).append("<a class='sm' href='/file?id=").append(fp.id).append("' target='_blank'>View</a></div>");
        } else sb.append(Html.mu("You have not scanned your fingerprints yet."));

        sb.append(Html.label(fp == null ? "SCAN MY FINGERPRINT" : "SCAN AGAIN"))
                .append("<style>").append(SCAN_CSS).append("</style>")
                .append("<div class='pad' id='pad'><div class='line'></div>").append(Html.icon("fp")).append("</div>")
                .append("<div class='pbar'><i></i></div><div id='scanmsg' aria-live='polite'></div>")
                .append("<button type='button' class='bt' id='scanbtn'>Scan my fingerprint</button>")
                .append("<form method='post' action='/fingerprints' id='scanform'><input type='hidden' name='act' value='scan'></form>")
                .append(Html.mu("Demo scanner: no real device is connected. A sample fingerprint pattern is saved so the rest of the system can be used and tested."))
                .append("<script>").append(SCAN_JS).append("</script>");

        sb.append("<details><summary class='sm' style='cursor:pointer'>Upload a fingerprint image instead</summary><div style='margin-top:10px'>")
                .append(Html.uploadForm("/fingerprints", "", "Upload fingerprints")).append("</div></details>");
        return Resp.html(Html.page("My Fingerprints", B, "/home", sb.toString()));
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
        boolean govt = Store.govtWorker(me.id), retired = age >= Policy.PENSION_AGE;
        if (r.post() && r.get("act").equals("choose")) {
            String b = r.get("benefit");
            if (!govt || !retired) return Resp.to("/pension", "err:Only retired government employees choose between the grant and the pension.");
            if (Store.benefitChoice(me.id) != null) return Resp.to("/pension", "err:You already made your choice. To change it, visit Pensions.");
            if (!b.equals("GRANT") && !b.equals("PENSION")) return Resp.to("/pension", "err:Choose the old-age grant or the retirement pension.");
            Store.setBenefitChoice(me.id, b);
            Store.notify(me.id, "You chose the " + Policy.benefitLabel(b).toLowerCase() + ". As a government employee you cannot also receive the " + Policy.benefitLabel(b.equals("GRANT") ? "PENSION" : "GRANT").toLowerCase() + ".");
            return Resp.to("/pension", "ok:You chose the " + Policy.benefitLabel(b).toLowerCase() + ". Now apply for it below.");
        }
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro("Your old-age grant and retirement pension. They are for citizens aged " + Policy.PENSION_AGE + " and above."));
        sb.append(Html.card("<b>How benefits work</b><span class='mu'>" + Html.esc(Policy.PENSION_RULE) + "</span>"));
        sb.append(Html.card("<div class='two'>" + Html.avatar(me, true, 56) + "<div><b>Age " + age + "</b><span class='mu'>Born " + me.dob + " (from Home Affairs)</span><span class='mu'>Employment: "
                + (govt ? "Government employee, " + Html.esc(Store.govtMinistry(me.id)) : "Private sector") + "</span></div></div>"));
        String ch = Store.benefitChoice(me.id);
        if (govt && retired && ch == null) {
            sb.append(Html.card("<b>You are retired</b><span class='mu'>Home Affairs shows you are " + age + " and the government records show you work for the government. You can receive the old-age grant OR the retirement pension, not both, because both come from the same money. Choose one. You cannot change it later without visiting Pensions.</span>"));
            for (String kind : new String[]{"GRANT", "PENSION"})
                sb.append(Html.card("<b>" + Policy.benefitLabel(kind) + "</b><span class='mu'>M " + Policy.benefitAmount(kind) + " per month</span>"
                        + Html.form("/pension", Html.hidden("act", "choose") + Html.hidden("benefit", kind) + Html.button("Choose the " + Policy.benefitLabel(kind).toLowerCase()))));
        } else {
            if (govt && retired) sb.append(Html.card("<b>You chose the " + Html.esc(Policy.benefitLabel(ch).toLowerCase()) + "</b><span class='mu'>As a government employee you cannot also receive the " + Html.esc(Policy.benefitLabel(ch.equals("GRANT") ? "PENSION" : "GRANT").toLowerCase()) + ".</span>" + Html.pill("Chosen")));
            if (govt && !retired) sb.append(Html.card("<b>Not retired yet</b><span class='mu'>When you are " + Policy.PENSION_AGE + " (" + Policy.turnsPensionAge(me) + ") you will choose either the old-age grant or the retirement pension.</span>"));
            for (String kind : new String[]{"GRANT", "PENSION"}) {
                if (govt && retired && !kind.equals(ch)) continue;
                boolean on = kind.equals("GRANT") ? Store.enrolled(me.id) : Store.pensionEnrolled(me.id);
                String svc = kind.equals("GRANT") ? Policy.GRANT_SERVICE : Policy.PENSION_SERVICE;
                String href = "/apply?dept=PE&service=" + svc.replace(" ", "%20");
                if (on) {
                    sb.append(Html.card("<b>You receive the " + Html.esc(Policy.benefitLabel(kind).toLowerCase()) + "</b><span class='mu'>M " + Policy.benefitAmount(kind) + " per month, paid around the "
                            + Policy.payday(YearMonth.from(today)).getDayOfMonth() + "th</span>" + Html.pill("Enrolled")));
                } else if (retired) {
                    sb.append(Html.card("<b>" + Policy.benefitLabel(kind) + "</b><span class='mu'>You can apply. Pensions will check your ID with Home Affairs.</span>")).append("<a class='bt' href='" + href + "'>Apply for the " + Html.esc(Policy.benefitLabel(kind).toLowerCase()) + "</a>");
                } else if (!govt) {
                    Period p = Period.between(today, Policy.turnsPensionAge(me));
                    sb.append(Html.card("<b>" + Policy.benefitLabel(kind) + ": not yet</b><span class='mu'>You can apply from " + Policy.turnsPensionAge(me) + " (in " + p.getYears() + " years, " + p.getMonths() + " months).</span>" + Html.pill("Pending")));
                }
            }
            if (!govt && retired) sb.append(Html.mu("You worked in the private sector, so you can receive both the grant and the pension."));
        }
        List<Payment> pays = Store.benefitPaymentsOf(me.id);
        if (!pays.isEmpty()) sb.append(Html.label("MY PAYMENTS"));
        for (Payment p : pays)
            sb.append(Html.row(Policy.benefitLabel(p.kind) + " " + p.month, "M " + p.amount + (p.receipt == null ? "" : " · receipt " + p.receipt), Html.pill(p.paid ? "Paid" : "Sent to Finance"), null));
        sb.append(bookLink(Models.Dept.PE, "Pension enquiry", "Book an appointment at Pensions"));
        return Resp.html(Html.page("My Pension", B, "/home", sb.toString()));
    }

    // ------------------------------------------------------------------ appointments and queue
    /** When the citizen came from one department's page, the department is fixed. From the main menu they can choose. */
    /** Opened from one department: show only that department. Opened from the dashboard: let the citizen choose. */
    private static String deptField(String selected) {
        boolean locked = false;
        for (Models.Dept d : Models.Dept.values()) if (d.name().equals(selected)) locked = true;
        if (!locked) return deptSelect(selected);
        Models.Dept d = dept(selected);
        return "<label class='f'>Department<input type='text' value='" + Html.esc(d.label) + "' readonly style='background:#eef2fa'></label>" + Html.hidden("dept", d.name())
                + "<a href='/book' style='font-size:12px;font-weight:700;color:#1a4fb4;margin:-4px 0 8px;display:block'>Choose another department</a>";
    }
    private static String deptSelect(String selected) {
        boolean fixed = false;
        for (Models.Dept d : Models.Dept.values()) if (d.name().equals(selected)) fixed = true;
        if (fixed) {
            Models.Dept d = Models.Dept.valueOf(selected);
            return "<label class='f'>Department<input type='text' value='" + Html.esc(d.label) + "' readonly style='background:#eef2fa'></label><input type='hidden' name='dept' value='" + d.name() + "'>";
        }
        StringBuilder o = new StringBuilder("<label class='f'>Department<select name='dept'>");
        for (Models.Dept d : Models.Dept.values())
            o.append("<option value='").append(d.name()).append("'").append(d.name().equals(selected) ? " selected" : "").append(">").append(Html.esc(d.label)).append("</option>");
        return o.append("</select></label>").toString();
    }

    /** A service box with ideas to pick from, and room to type anything else. */
    /** The things a citizen can book or queue for at one department. Only this department's own services. */
    static List<String> bookable(Models.Dept d) {
        List<String> l = new ArrayList<>();
        switch (d) {
            case HA: l.add("Fingerprints for my ID card"); l.add("Collect a document"); break;
            case TR: l.add("Learner's licence test"); l.add("Driving test"); l.add("Roadworthy inspection"); l.add("Collect a document"); break;
            case FI: l.add("Payment enquiry"); break;
            case PE: l.add("Pension enquiry"); break;
            case PA: l.add("Collect a document"); break;
            default: break;
        }
        for (Policy.Service s : Policy.of(d)) if (!l.contains(s.name)) l.add(s.name);
        l.add("General enquiry");
        return l;
    }
    private static String serviceBox(Models.Dept d, String value) {
        StringBuilder o = new StringBuilder("<label class='f'>Service<select name='service'>");
        List<String> l = bookable(d);
        for (String s : l) o.append("<option value='").append(Html.esc(s)).append("'").append(s.equals(value) ? " selected" : "").append(">").append(Html.esc(s)).append("</option>");
        return o.append("</select></label>").toString();
    }
    /** Changes the Service list when the Department changes, so each department shows only its own things. */
    private static String serviceScript() {
        StringBuilder j = new StringBuilder("{");
        for (Models.Dept d : Models.Dept.values()) {
            if (j.length() > 1) j.append(",");
            j.append("\"").append(d.name()).append("\":[");
            boolean first = true;
            for (String s : bookable(d)) { j.append(first ? "" : ",").append("\"").append(s.replace("\\", "").replace("\"", "")).append("\""); first = false; }
            j.append("]");
        }
        j.append("}");
        return "<script>(function(){var S=" + j + ";document.querySelectorAll('select[name=dept]').forEach(function(ds){var f=ds.form;if(!f)return;var ss=f.querySelector('select[name=service]');if(!ss)return;"
                + "ds.addEventListener('change',function(){ss.innerHTML='';(S[ds.value]||[]).forEach(function(n){var o=document.createElement('option');o.value=n;o.textContent=n;ss.appendChild(o);});});});})();</script>";
    }

    static Resp book(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        LocalDate today = LocalDate.now();

        if (r.post()) {
            String act = r.get("act");
            if (act.equals("cancel")) {
                Appointment a = null;
                try { a = Store.appointment(Integer.parseInt(r.get("id"))); } catch (Exception ignored) { }
                if (a == null || !a.citizenId.equals(me.id) || !a.status.equals("Booked")) return Resp.to("/book", "err:Appointment not found.");
                Store.cancelAppointment(a.id);
                Store.notify(me.id, "Your appointment at " + a.dept.label + " on " + a.slot.format(WHEN) + " was cancelled.");
                return Resp.to("/book", "ok:Appointment cancelled.");
            }
            Models.Dept d = dept(r.get("dept"));
            String service = r.get("service").trim();
            if (service.isEmpty()) service = "General enquiry";
            if (service.length() > 80) service = service.substring(0, 80);
            String back = bookHref(d, service);
            if (!act.equals("cancel") && !bookable(d).contains(service)) return Resp.to(bookHref(d, ""), "err:Choose a service from the list for " + d.label + ".");

            if (act.equals("queue")) {
                for (Ticket t : Store.waiting(d))
                    if (t.citizenId.equals(me.id)) return Resp.to(back, "err:You are already in the queue at " + d.label + " with number " + t.number + ".");
                Ticket t = Store.addTicket(me.id, d, service);
                Store.notify(me.id, "Your queue number at " + d.label + " is " + t.number + " for " + service + ".");
                return Resp.to("/book", "ok:Your queue number is " + t.number + " at " + d.label + ".");
            }

            LocalDate date;
            LocalTime time;
            try { date = LocalDate.parse(r.get("date")); time = LocalTime.parse(r.get("time")); }
            catch (Exception e) { return Resp.to(back, "err:Choose a date and a time."); }
            if (!SLOTS.contains(time)) return Resp.to(back, "err:Choose one of the times in the list.");
            if (date.isBefore(today) || date.isAfter(today.plusDays(60))) return Resp.to(back, "err:Choose a date in the next 60 days.");
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) return Resp.to(back, "err:Offices are open Monday to Friday.");
            LocalDateTime slot = date.atTime(time);
            if (!slot.isAfter(LocalDateTime.now())) return Resp.to(back, "err:That time has already passed.");
            if (Store.hasBooking(me.id, d, slot)) return Resp.to(back, "err:You already have an appointment at " + d.label + " at that time.");
            if (Store.slotsTaken(d, slot) >= Store.SLOT_CAPACITY) return Resp.to(back, "err:That time is full. Please choose another time.");
            Appointment a = Store.addAppointment(me.id, d, service, slot);
            Store.notify(me.id, "Appointment booked at " + d.label + " for " + service + " on " + a.slot.format(WHEN) + ".");
            return Resp.to("/book", "ok:Appointment booked at " + d.label + " on " + a.slot.format(WHEN) + ".");
        }

        String selDept = r.get("dept"), svc = r.get("service");
        StringBuilder timeOpts = new StringBuilder();
        for (LocalTime t : SLOTS) timeOpts.append("<option value='").append(t).append("'>").append(t).append("</option>");

        StringBuilder sb = new StringBuilder(Html.banner(r.get("m"))
                + Html.intro("Choose how you want to be served: join the line now, or book a time and skip the wait. For a test or an inspection, book an appointment."));

        sb.append(Html.label("JOIN THE QUEUE NOW")).append(Html.card(
                Html.mu("You get a queue number. Come to the office and wait for your number to be called.")
                        + Html.form("/book", Html.hidden("act", "queue") + deptField(selDept) + serviceBox(dept(selDept), svc) + "<br>" + Html.button("Get my queue number"))));

        sb.append(Html.label("BOOK A TIME")).append(Html.card(
                Html.mu("Offices are open Monday to Friday, 08:00 to 16:00.")
                        + Html.form("/book", Html.hidden("act", "book") + deptField(selDept) + serviceBox(dept(selDept), svc)
                        + "<label class='f'>Date<input type='date' name='date' min='" + today + "' max='" + today.plusDays(60) + "' required></label>"
                        + "<label class='f'>Time<select name='time'>" + timeOpts + "</select></label>"
                        + "<br>" + Html.button("Book appointment"))));

        sb.append(Html.label("MY APPOINTMENTS"));
        List<Appointment> mine = Store.appointmentsOf(me.id);
        for (Appointment a : mine) {
            boolean open = a.status.equals("Booked") && a.slot.isAfter(LocalDateTime.now());
            String shown = a.status.equals("Booked") && !open ? "Past" : a.status;
            sb.append("<div class='rw'><div><b>").append(Html.esc(a.dept.label)).append("</b><small>").append(Html.esc(a.service)).append(" · ").append(a.slot.format(WHEN)).append("</small></div>")
                    .append(Html.pill(shown))
                    .append(open ? Html.form("/book", Html.hidden("act", "cancel") + Html.hidden("id", String.valueOf(a.id)) + "<button class='sm'>Cancel</button>") : "")
                    .append("</div>");
        }
        if (mine.isEmpty()) sb.append(Html.mu("No appointments yet."));

        sb.append(Html.label("MY QUEUE NUMBERS"));
        List<Ticket> tickets = Store.ticketsOf(me.id);
        for (Ticket t : tickets) sb.append(Html.row(t.number, t.dept.label + " · " + t.service, Html.pill(t.served ? "Completed" : "Pending"), null));
        if (tickets.isEmpty()) sb.append(Html.mu("No queue numbers yet."));
        sb.append(serviceScript());
        return Resp.html(Html.page("Appointments & Queue", B, "/home", sb.toString()));
    }

    static Resp notifs(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        StringBuilder sb = new StringBuilder(Html.intro("Updates from every department in one place."));
        for (Notification n : Store.notesOf(me.id)) sb.append(Html.row(n.text, n.time.format(WHEN), null, null));
        return Resp.html(Html.page("Notifications", B, "/home", sb.toString()));
    }

    /** Only the citizen sees who looked at their data. Staff have no page for this. */
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