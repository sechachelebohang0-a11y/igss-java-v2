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
        APPROVED("Approved"), READY("Ready to collect"), COMPLETED("Completed"), REJECTED("Rejected");

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

    /** A booked time at a department. status is Booked or Cancelled. */
    public static class Appointment {
        public int id;
        public String citizenId, service, status;
        public Dept dept;
        public LocalDateTime slot, created;
    }

    /** Police ask Home Affairs for a citizen's address. status is Pending, Approved or Rejected. */
    public static class AddressRequest {
        public int id;
        public String citizenId, caseNo, reason, officer, status, decidedBy;
        public LocalDateTime requested, decided;
    }

    /** A police case. status is Open case, Under investigation, Suspect identified, In court or Closed. citizenId is the person the case is about (can be null). */
    public static class CaseRec {
        public int id;
        public String caseNo, title, details, citizenId, officer, status;
        public LocalDateTime opened, updated;
    }

    /** One update in the history of a police case. */
    public static class CaseNote {
        public int id;
        public String caseNo, officer, text;
        public LocalDateTime time;
    }

    /** A lost or stolen ID card or passport recorded by Police. status is Reported or Found. */
    public static class LostDoc {
        public int id;
        public String citizenId, docType, docNo, place, details, officer, caseNo, status;
        public LocalDate lostDate;
        public LocalDateTime created;
    }

    /** A vehicle or driving licence record held by Traffic. */
    public static class TrafficRec {
        public int id;
        public String citizenId, kind, detail, status;
        public LocalDateTime created;
    }

    /** kind is FEE (service fee) or PENSION (monthly pension). */
    public static class Payment {
        public int id, amount, appId;
        public String citizenId, kind, ref, month, receipt;
        public boolean paid;
        public LocalDateTime created;
    }
}