package lesotho.igss;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** All data classes of the system in one place. */
public final class Models {
    private Models() {}

    public enum Dept {
        HA("Home Affairs", "bank", 'H'), TR("Traffic Services", "car", 'T'), FI("Finance Services", "shield", 'F'),
        PE("Pensions", "coin", 'P'), PO("Police Services", "shield", 'L'), PA("Passport Services", "pass", 'S');

        public final String label, icon;
        public final char letter;
        Dept(String label, String icon, char letter) { this.label = label; this.icon = icon; this.letter = letter; }
    }

    public enum Status {
        PAYMENT_DUE("Payment required"), VERIFIED("Identity verified"), PROCESSING("Processing"), NEEDS_INFO("Needs info"),
        APPROVED("Approved"), COMPLETED("Completed"), REJECTED("Rejected");

        public final String label;
        Status(String label) { this.label = label; }
    }

    /** A record in the national register held by Home Affairs. The account fields are filled when the citizen registers. */
    public static class Citizen {
        public String id, name, gender, phone, email, address, fingerprint, salt, hash, registeredName;
        public LocalDate dob;
        public boolean alive = true, accountVerified;
        public boolean hasAccount() { return hash != null; }
    }

    public static class Official { public String staffId, name, salt, hash; public Dept dept; }

    public static class Application {
        public int id, fee;
        public String citizenId, service, reference, note = "", receipt;
        public Dept dept;
        public Status status;
        public LocalDateTime created;
        public boolean paid;
    }

    public static class Doc { public int id; public String citizenId, type, filename, stored, mime, status; public LocalDateTime uploaded; }

    public static class Notification { public String citizenId, text; public LocalDateTime time; }

    public static class AccessEntry { public String citizenId, officer, fields, purpose; public Dept dept; public LocalDateTime time; }

    public static class Ticket { public int id; public String citizenId, number, service; public Dept dept; public LocalDateTime time; public boolean served; }

    /** kind is FEE (service fee) or PENSION (monthly pension). */
    public static class Payment {
        public int id, amount, appId;
        public String citizenId, kind, ref, month, receipt;
        public boolean paid;
        public LocalDateTime created;
    }
}
