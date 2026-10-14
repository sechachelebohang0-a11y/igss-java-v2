package lesotho.igss;

import lesotho.igss.Models.*;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Business rules: who may see what, the service catalogue, document rules and the pension rules. */
public final class Policy {
    private Policy() {}

    public static final int PENSION_AMOUNT = 2500; // sample amount, Maloti per month
    public static final int PENSION_AGE = 60;

    public enum Field {
        NAME("Name"), ID("ID number"), DOB("Date of birth"), PHOTO("Photo"), ADDRESS("Address"),
        PHONE("Phone"), LIFE("Life status"), FP("Fingerprint match");
        public final String label;
        Field(String label) { this.label = label; }
    }

    /** Minimum necessary access: each department sees only these fields. */
    public static List<Field> allowed(Dept d) {
        switch (d) {
            case HA: return List.of(Field.NAME, Field.ID, Field.DOB, Field.PHOTO, Field.ADDRESS, Field.PHONE, Field.LIFE, Field.FP);
            case TR: return List.of(Field.NAME, Field.ID, Field.DOB, Field.PHOTO);
            case FI: return List.of(Field.NAME, Field.ID);
            case PE: return List.of(Field.NAME, Field.DOB, Field.LIFE);
            case PO: return List.of(Field.NAME, Field.PHOTO, Field.FP);
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

    public static final List<String> DOC_TYPES = List.of("National ID copy", "Birth certificate", "Proof of residence", "Passport photo", "Marriage certificate",
        "Medical certificate", "Vehicle papers", "Death notification", "Bank details", "Old passport");

    public static final List<Service> ALL = new ArrayList<>();

    private static void s(Dept d, String cat, String name, int fee, String desc, String... docs) { ALL.add(new Service(d, cat, name, fee, desc, false, docs)); }

    static {
        // Home Affairs: every civil registration and immigration service a citizen can use (fees are sample amounts, in Maloti)
        s(Dept.HA, "National ID", "First-time ID Card", 50, "Apply for your first national identity card.", "Birth certificate", "Proof of residence", "Passport photo");
        s(Dept.HA, "National ID", "ID Replacement (lost or damaged)", 100, "Replace a lost, stolen or damaged ID card.", "Proof of residence", "Passport photo");
        s(Dept.HA, "National ID", "ID Renewal", 50, "Renew an ID card that is expiring.", "National ID copy", "Passport photo");
        s(Dept.HA, "National ID", "ID Details Correction", 75, "Correct a wrong name, date of birth or other detail.", "National ID copy", "Birth certificate");
        s(Dept.HA, "Birth", "Birth Registration", 0, "Register the birth of a child.", "Medical certificate", "National ID copy");
        s(Dept.HA, "Birth", "Birth Certificate Copy", 30, "Get a certified copy of a birth certificate.", "National ID copy");
        s(Dept.HA, "Marriage", "Marriage Registration", 100, "Register a civil marriage.", "National ID copy", "Birth certificate");
        s(Dept.HA, "Marriage", "Marriage Certificate Copy", 30, "Get a certified copy of a marriage certificate.", "National ID copy");
        s(Dept.HA, "Marriage", "Certificate of No Impediment", 60, "Proof that you are free to marry.", "National ID copy", "Birth certificate");
        s(Dept.HA, "Death", "Death Registration", 0, "Register a death in the family.", "Death notification", "National ID copy");
        s(Dept.HA, "Death", "Death Certificate Copy", 30, "Get a certified copy of a death certificate.", "National ID copy");
        s(Dept.HA, "Citizenship and Immigration", "Citizenship Certificate", 150, "Prove or apply for Basotho citizenship.", "Birth certificate", "National ID copy");
        s(Dept.HA, "Citizenship and Immigration", "Residence Permit", 300, "Permission to live in Lesotho.", "Passport photo", "Proof of residence");
        s(Dept.HA, "Citizenship and Immigration", "Work Permit", 400, "Permission to work in Lesotho.", "Passport photo", "Medical certificate");
        s(Dept.HA, "Citizenship and Immigration", "Visa Extension", 200, "Extend a visitor visa.", "Old passport");
        s(Dept.HA, "Citizenship and Immigration", "Permanent Residence", 500, "Apply to live in Lesotho permanently.", "Proof of residence");
        s(Dept.HA, "Personal records", "Change of Name", 120, "Change your legal name.", "National ID copy", "Birth certificate");

        s(Dept.TR, "Driving", "Learner's Licence", 60, "Start learning to drive.", "National ID copy", "Medical certificate");
        s(Dept.TR, "Driving", "Driving Licence (new)", 150, "Get your first driving licence.", "National ID copy", "Medical certificate", "Passport photo");
        s(Dept.TR, "Driving", "Driving Licence Renewal", 100, "Renew your driving licence.", "National ID copy", "Passport photo");
        s(Dept.TR, "Driving", "Licence Replacement", 90, "Replace a lost or damaged licence.", "National ID copy");
        s(Dept.TR, "Vehicles", "Vehicle Registration", 250, "Register a vehicle in your name.", "Vehicle papers", "National ID copy");
        s(Dept.TR, "Vehicles", "Vehicle Ownership Transfer", 150, "Transfer a vehicle to a new owner.", "Vehicle papers");
        s(Dept.TR, "Vehicles", "Roadworthy Certificate", 80, "Certificate that a vehicle is safe to drive.", "Vehicle papers");
        s(Dept.TR, "Permits", "D Permit (public transport)", 200, "Permit to carry passengers for payment.", "National ID copy", "Medical certificate");

        s(Dept.FI, "Tax", "Tax Clearance Certificate", 0, "Proof that your taxes are in order.", "National ID copy");
        s(Dept.FI, "Refunds", "Refund Request", 0, "Ask for a refund of money paid in error.", "Bank details");
        s(Dept.FI, "Payments", "Payment Receipt Copy", 0, "Get a copy of a lost payment receipt.");

        ALL.add(new Service(Dept.PE, "Pension", "Old-Age Pension Application", 0, "Monthly pension for citizens aged " + PENSION_AGE + " and above.", true, "National ID copy"));
        ALL.add(new Service(Dept.PE, "Pension", "Retirement Benefit Claim", 0, "Claim retirement or terminal benefits (age " + PENSION_AGE + " and above).", true, "National ID copy", "Bank details"));

        s(Dept.PO, "Clearance", "Police Clearance Certificate", 50, "Certificate showing your police record.", "National ID copy", "Passport photo");
        s(Dept.PO, "Reports", "Report a Lost Document", 0, "Report a lost ID, licence or passport.", "National ID copy");

        s(Dept.PA, "Passport", "New Passport", 300, "Apply for your first passport.", "National ID copy", "Birth certificate", "Passport photo");
        s(Dept.PA, "Passport", "Passport Renewal", 300, "Renew a passport that is expiring.", "Old passport", "Passport photo");
        s(Dept.PA, "Passport", "Lost or Stolen Passport", 350, "Replace a lost or stolen passport.", "National ID copy", "Passport photo");
        s(Dept.PA, "Travel", "Emergency Travel Document", 150, "Urgent travel document.", "National ID copy", "Passport photo");
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

    public static final List<String> STEPS = List.of("Received", "Payment and identity", "Processing", "Approved", "Completed");

    public static int stepIndex(Status s) {
        switch (s) {
            case PAYMENT_DUE: return 1;
            case VERIFIED: case PROCESSING: case NEEDS_INFO: return 2;
            case APPROVED: return 3;
            case COMPLETED: return 4;
            default: return 1;
        }
    }
}
