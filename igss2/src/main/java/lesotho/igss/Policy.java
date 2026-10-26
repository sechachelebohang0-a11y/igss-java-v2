package lesotho.igss;

import lesotho.igss.Models.*;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Business rules: who may see what, the service catalogue, document rules, collection offices and the pension rules. */
public final class Policy {
    private Policy() {}

    public static final int PENSION_AMOUNT = 2500; // sample amount, Maloti per month
    public static final int PENSION_AGE = 60;

    /** Shown on the pension pages and used in error messages. */
    public static final String PENSION_RULE = "The old-age grant and the retirement pension are two different benefits. People who worked in the private sector can receive both. People who worked for the government must choose one of the two, because both are paid from the same government money.";

    /** Shown on the Finance Services page. */
    public static final String FINANCE_NOTE = "Finance Services keeps the financial records (income and expenses), receives service fees, pays grants and pensions, and allocates money to the other departments. It does not collect tax.";

    public static final int RETIREMENT_PENSION_AMOUNT = 1800; // sample amount, Maloti per month
    public static final String GRANT_SERVICE = "Old-Age Grant Application";
    public static final String PENSION_SERVICE = "Retirement Pension Claim";

    /** GRANT = old-age grant, PENSION = retirement pension. */
    public static String benefitLabel(String kind) { return "GRANT".equals(kind) ? "Old-Age Grant" : "Retirement Pension"; }
    public static int benefitAmount(String kind) { return "GRANT".equals(kind) ? PENSION_AMOUNT : RETIREMENT_PENSION_AMOUNT; }

    /** District offices where finished documents are collected. */
    public static final List<String> OFFICES = List.of("Maseru", "Berea", "Leribe", "Butha-Buthe", "Mokhotlong", "Thaba-Tseka",
            "Qacha's Nek", "Quthing", "Mohale's Hoek", "Mafeteng");

    /** The office nearest to an address: the district named in it, or Maseru when no district is found. */
    public static String officeFor(String text) {
        if (text != null) {
            String t = text.toLowerCase(Locale.ROOT);
            for (String o : OFFICES) if (t.contains(o.toLowerCase(Locale.ROOT))) return o;
        }
        return "Maseru";
    }

    public enum Field {
        NAME("Name"), ID("ID number"), DOB("Date of birth"), PHOTO("Photo"), ADDRESS("Address"),
        PHONE("Phone"), LIFE("Life status"), FP("Fingerprint match"),
        EMPLOYMENT("Employment"), TAX("Tax standing (SRL)");
        public final String label;
        Field(String label) { this.label = label; }
    }

    /** Minimum necessary access: each department sees only these fields. */
    public static List<Field> allowed(Dept d) {
        switch (d) {
            case HA: return List.of(Field.NAME, Field.ID, Field.DOB, Field.PHOTO, Field.ADDRESS, Field.PHONE, Field.LIFE, Field.FP);
            case TR: return List.of(Field.NAME, Field.ID, Field.DOB, Field.PHOTO);
            case FI: return List.of(Field.NAME, Field.ID, Field.EMPLOYMENT, Field.TAX);
            case PE: return List.of(Field.NAME, Field.ID, Field.DOB, Field.LIFE, Field.EMPLOYMENT, Field.TAX);
            case PO: return List.of(Field.NAME, Field.PHOTO, Field.ADDRESS, Field.FP);
            default: return List.of(Field.NAME, Field.DOB, Field.PHOTO);
        }
    }

    public static String value(Citizen c, Field f, boolean fingerprintUsed) {
        switch (f) {
            case NAME: return c.name;
            case ID: return c.id;
            case DOB: return c.dob.toString();
            case PHOTO: return Store.docOf(c.id, "Passport photo") == null ? "No photo on file" : "Photo on file";
            case ADDRESS: return c.address;
            case PHONE: return c.phone;
            case LIFE: return c.alive ? "Alive" : "Deceased";
            case EMPLOYMENT: return Store.govtWorker(c.id) ? "Government employee (" + Store.govtMinistry(c.id) + ")" : "Private sector employee";
            case TAX: { int owed = Store.taxOwed(c.id); return owed <= 0 ? "Pays tax well. No tax owed to SRL." : "Does not pay tax well. M " + owed + " owed to SRL, taken from payments."; }
            default: return fingerprintUsed ? "Match confirmed" : "Not used in this search";
        }
    }

    // ---------- service catalogue ----------
    public static final class Service {
        public final Dept dept;
        public final String category, name, desc;
        public final int fee;
        public final List<String> docs;
        public final boolean pension;

        Service(Dept dept, String category, String name, int fee, String desc, boolean pension, String... docs) {
            this.dept = dept; this.category = category; this.name = name; this.fee = fee; this.desc = desc; this.pension = pension; this.docs = List.of(docs);
        }
    }

    public static final List<String> DOC_TYPES = List.of("Passport photo", "National ID copy", "Birth certificate", "Proof of residence", "Marriage certificate",
            "Medical certificate", "Vehicle papers", "Death notification", "Bank details", "Old passport", "Driving licence copy");

    public static final List<Service> ALL = new ArrayList<>();

    private static void s(Dept d, String cat, String name, int fee, String desc, String... docs) { ALL.add(new Service(d, cat, name, fee, desc, false, docs)); }

    static {
        // Home Affairs services for a logged-in citizen (fees are sample amounts, in Maloti). Anything to do with the national ID card is free.
        // The First-time ID Card is made after login. The birth certificate copy is public-only (PUBLIC_ONLY below); immigration services are not offered here.
        s(Dept.HA, "National ID", "First-time ID Card", 0, "Apply for your first national identity card (age 16 and above). Apply here, no need to go to the office first.", "Birth certificate", "Proof of residence", "Passport photo");
        s(Dept.HA, "National ID", "ID Replacement (lost or damaged)", 0, "Replace a lost, stolen or damaged ID card.", "Proof of residence", "Passport photo");
        s(Dept.HA, "National ID", "ID Renewal", 0, "Renew an ID card that is expiring.", "National ID copy", "Passport photo");
        s(Dept.HA, "National ID", "ID Details Correction", 0, "Correct a wrong name, date of birth or other detail.", "National ID copy", "Birth certificate");
        s(Dept.HA, "Birth", "Birth Registration", 0, "Register the birth of a child.", "Medical certificate", "National ID copy");
        s(Dept.HA, "Marriage", "Marriage Registration", 100, "Register a civil marriage.", "National ID copy", "Birth certificate");
        s(Dept.HA, "Marriage", "Certificate of No Impediment", 60, "Proof that you are free to marry.", "National ID copy", "Birth certificate");
        s(Dept.HA, "Death", "Death Registration", 0, "Register a death in the family.", "Death notification", "National ID copy");
        s(Dept.HA, "Personal records", "Change of Names", 120, "Change your legal names.", "National ID copy", "Birth certificate");

        s(Dept.TR, "Driving", "Driving Licence Renewal", 100, "Renew your driving licence.", "National ID copy", "Driving licence copy", "Passport photo");
        s(Dept.TR, "Driving", "Licence Replacement", 90, "Replace a lost or damaged licence.", "National ID copy");
        s(Dept.TR, "Vehicles", "Vehicle Registration", 250, "Register a vehicle in your name.", "Vehicle papers", "National ID copy");
        s(Dept.TR, "Vehicles", "Change of Ownership", 150, "Change the owner of a vehicle.", "Vehicle papers");
        s(Dept.TR, "Permits", "D Permit (public transport)", 200, "Permit to carry passengers for payment.", "National ID copy", "Medical certificate");


        ALL.add(new Service(Dept.PE, "Pension", "Old-Age Grant Application", 0, "Monthly old-age grant for citizens aged " + PENSION_AGE + " and above.", true, "National ID copy"));
        ALL.add(new Service(Dept.PE, "Pension", "Retirement Pension Claim", 0, "Claim your pension when you retire (age " + PENSION_AGE + " and above). Government employees choose either this or the grant.", true, "National ID copy", "Bank details"));

        s(Dept.PO, "Police Clearance", "Police Clearance Certificate", 50, "Certificate that you have no criminal record. Police check your fingerprints with Home Affairs, then tell you where to collect it.", "National ID copy");
        s(Dept.PO, "Incident Reports", "Report a Robbery", 0, "Report a robbery, hijacking or mugging.");
        s(Dept.PO, "Incident Reports", "Report an Incident", 0, "Report a theft, break-in, assault, fraud or another crime.");
        s(Dept.PO, "Incident Reports", "Report a Lost Document", 0, "Report a lost ID, licence or passport.", "National ID copy");

        s(Dept.PA, "Passport", "New Passport", 300, "Apply for your first passport.", "National ID copy", "Birth certificate", "Passport photo");
        s(Dept.PA, "Passport", "Passport Renewal", 300, "Renew a passport that is expiring.", "Old passport", "Passport photo");
        s(Dept.PA, "Passport", "Lost or Stolen Passport", 350, "Replace a lost or stolen passport.", "National ID copy", "Passport photo");
        s(Dept.PA, "Travel", "Emergency Travel Document", 150, "Urgent travel document.", "National ID copy", "Passport photo");
    }

    /**
     * Services that are offered only BEFORE login, on the public application forms (see PublicApply).
     * They are deliberately not in ALL, so they never appear in the citizen dashboard.
     */
    public static final List<Service> PUBLIC_ONLY = List.of(
            new Service(Dept.HA, "Birth", "Birth Certificate Copy", 30, "Get a copy of your birth certificate.", false, "National ID copy"));

    /** Looks a Home Affairs service up among the public-only ones first, then among the dashboard services. */
    public static Service publicService(String name) {
        for (Service s : PUBLIC_ONLY) if (s.name.equals(name)) return s;
        return service(Dept.HA, name);
    }

    public static Service service(Dept d, String name) {
        for (Service s : ALL) if (s.dept == d && s.name.equals(name)) return s;
        return null;
    }

    public static List<Service> of(Dept d) {
        List<Service> out = new ArrayList<>();
        for (Service s : ALL) if (s.dept == d) out.add(s);
        return out;
    }

    /** May this officer open this uploaded document? Home Affairs, passport photos where the policy allows photos, or documents the service requires. */
    public static boolean canView(Official o, Doc d) {
        if (o.dept == Dept.HA) return true;
        if (d.type.equals("Passport photo") && allowed(o.dept).contains(Field.PHOTO)) return true;
        for (Application a : Store.appsOf(d.citizenId)) {
            Service sv = service(a.dept, a.service);
            if (a.dept == o.dept && sv != null && sv.docs.contains(d.type)) return true;
        }
        return false;
    }

    // ---------- pensions ----------
    public static int age(Citizen c, LocalDate today) { return Period.between(c.dob, today).getYears(); }
    public static boolean pensionAge(Citizen c, LocalDate today) { return age(c, today) >= PENSION_AGE; }
    public static LocalDate turnsPensionAge(Citizen c) { return c.dob.plusYears(PENSION_AGE); }
    public static LocalDate payday(YearMonth m) { return m.atDay(Math.min(30, m.lengthOfMonth())); }

    public static String pensionState(Payment p, LocalDate today) {
        if (p != null && p.paid) return "Paid";
        if (p != null) return "Sent to Finance";
        long d = ChronoUnit.DAYS.between(today, payday(YearMonth.from(today)));
        if (d < 0) return "Overdue";
        if (d == 0) return "Due today";
        if (d <= 7) return "Due soon";
        return "Upcoming";
    }

    // ---------- how an application moves ----------
    /** Services where nothing is handed over at an office: money, pensions and police reports. All others end with collection. */
    private static final Set<String> NO_COLLECT = Set.of("Old-Age Grant Application", "Retirement Pension Claim", "Report a Robbery", "Report an Incident", "Report a Lost Document");

    public static boolean collectable(String service) { return !NO_COLLECT.contains(service); }

    /** The steps this application goes through. A fee step is only there when the service has a fee. */
    public static List<String> steps(Application a) {
        List<String> l = new ArrayList<>();
        l.add("Application sent");
        if (a.fee > 0) l.add("Fee paid");
        l.add("Documents checked");
        l.add("Being processed");
        l.add("Approved");
        if (collectable(a.service)) { l.add("Ready to collect"); l.add("Collected"); }
        else l.add("Completed");
        return l;
    }

    /** Which step the application is on. A finished application returns the number of steps, so every step shows as done. */
    public static int stepIndex(Application a) {
        int off = a.fee > 0 ? 1 : 0;
        int docs = 1 + off, proc = 2 + off, appr = 3 + off, ready = 4 + off;
        switch (a.status) {
            case PAYMENT_DUE: return 1;
            case VERIFIED: case NEEDS_INFO: return docs;
            case PROCESSING: return proc;
            case APPROVED: return appr;
            case READY: return collectable(a.service) ? ready : appr;
            case COMPLETED: return steps(a).size();
            default: return docs; // rejected: shown where it stopped
        }
    }
}