package lesotho.igss;

import lesotho.igss.Models.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/** All database access (SQLite). Pages never write SQL; they call these methods. */
public final class Store {
    private Store() {}

    public static final Object LOCK = new Object();
    static final Path UPLOADS = Paths.get("uploads");

    // ---------- security helpers ----------
    static String salt() { byte[] b = new byte[12]; new SecureRandom().nextBytes(b); return Base64.getEncoder().encodeToString(b); }

    static String hash(String salt, String pw) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest((salt + pw).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    static String norm(String id) { return id == null ? "" : id.replace(" ", ""); }

    // ---------- row mappers ----------
    static final Db.Mapper<Citizen> CZ = r -> {
        Citizen c = new Citizen();
        c.id = r.getString("id"); c.name = r.getString("name"); c.gender = r.getString("gender"); c.phone = r.getString("phone");
        c.email = r.getString("email"); c.address = r.getString("address"); c.fingerprint = r.getString("fingerprint");
        c.dob = LocalDate.parse(r.getString("dob")); c.alive = r.getInt("alive") == 1; c.salt = r.getString("salt"); c.hash = r.getString("hash");
        c.registeredName = r.getString("registered_name"); c.accountVerified = r.getInt("account_verified") == 1;
        return c;
    };
    static final Db.Mapper<Official> OFF = r -> {
        Official o = new Official();
        o.staffId = r.getString("staff_id"); o.name = r.getString("name"); o.dept = Dept.valueOf(r.getString("dept")); o.salt = r.getString("salt"); o.hash = r.getString("hash");
        return o;
    };
    static final Db.Mapper<Application> APP = r -> {
        Application a = new Application();
        a.id = r.getInt("id"); a.citizenId = r.getString("citizen_id"); a.dept = Dept.valueOf(r.getString("dept")); a.service = r.getString("service");
        a.reference = r.getString("reference"); a.note = r.getString("note") == null ? "" : r.getString("note"); a.status = Status.valueOf(r.getString("status"));
        a.created = LocalDateTime.parse(r.getString("created")); a.fee = r.getInt("fee"); a.paid = r.getInt("paid") == 1; a.receipt = r.getString("receipt");
        return a;
    };
    static final Db.Mapper<Doc> DOC = r -> {
        Doc d = new Doc();
        d.id = r.getInt("id"); d.citizenId = r.getString("citizen_id"); d.type = r.getString("doc_type"); d.filename = r.getString("filename");
        d.stored = r.getString("stored"); d.mime = r.getString("mime"); d.status = r.getString("status"); d.uploaded = LocalDateTime.parse(r.getString("uploaded"));
        return d;
    };
    static final Db.Mapper<Notification> NOTE = r -> {
        Notification n = new Notification();
        n.citizenId = r.getString("citizen_id"); n.text = r.getString("text"); n.time = LocalDateTime.parse(r.getString("time"));
        return n;
    };
    static final Db.Mapper<AccessEntry> ACC = r -> {
        AccessEntry e = new AccessEntry();
        e.citizenId = r.getString("citizen_id"); e.dept = Dept.valueOf(r.getString("dept")); e.officer = r.getString("officer");
        e.fields = r.getString("fields"); e.purpose = r.getString("purpose"); e.time = LocalDateTime.parse(r.getString("time"));
        return e;
    };
    static final Db.Mapper<Ticket> TKT = r -> {
        Ticket t = new Ticket();
        t.id = r.getInt("id"); t.citizenId = r.getString("citizen_id"); t.dept = Dept.valueOf(r.getString("dept")); t.service = r.getString("service");
        t.number = r.getString("number"); t.time = LocalDateTime.parse(r.getString("time")); t.served = r.getInt("served") == 1;
        return t;
    };
    static final Db.Mapper<Appointment> APT = r -> {
        Appointment a = new Appointment();
        a.id = r.getInt("id"); a.citizenId = r.getString("citizen_id"); a.dept = Dept.valueOf(r.getString("dept")); a.service = r.getString("service");
        a.slot = LocalDateTime.parse(r.getString("slot")); a.status = r.getString("status"); a.created = LocalDateTime.parse(r.getString("created"));
        return a;
    };
    static final Db.Mapper<AddressRequest> ADR = r -> {
        AddressRequest a = new AddressRequest();
        a.id = r.getInt("id"); a.citizenId = r.getString("citizen_id"); a.caseNo = r.getString("case_no"); a.reason = r.getString("reason");
        a.officer = r.getString("officer"); a.status = r.getString("status"); a.decidedBy = r.getString("decided_by");
        a.requested = LocalDateTime.parse(r.getString("requested"));
        a.decided = r.getString("decided") == null ? null : LocalDateTime.parse(r.getString("decided"));
        return a;
    };
    static final Db.Mapper<CaseRec> CASE = r -> {
        CaseRec c = new CaseRec();
        c.id = r.getInt("id"); c.caseNo = r.getString("case_no"); c.title = r.getString("title");
        c.details = r.getString("details") == null ? "" : r.getString("details");
        c.citizenId = r.getString("citizen_id"); c.officer = r.getString("officer"); c.status = r.getString("status");
        c.opened = LocalDateTime.parse(r.getString("opened")); c.updated = LocalDateTime.parse(r.getString("updated"));
        return c;
    };
    static final Db.Mapper<CaseNote> CNOTE = r -> {
        CaseNote n = new CaseNote();
        n.id = r.getInt("id"); n.caseNo = r.getString("case_no"); n.officer = r.getString("officer"); n.text = r.getString("text");
        n.time = LocalDateTime.parse(r.getString("time"));
        return n;
    };
    static final Db.Mapper<LostDoc> LOST = r -> {
        LostDoc l = new LostDoc();
        l.id = r.getInt("id"); l.citizenId = r.getString("citizen_id"); l.docType = r.getString("doc_type");
        l.docNo = r.getString("doc_no") == null ? "" : r.getString("doc_no");
        l.lostDate = LocalDate.parse(r.getString("lost_date")); l.place = r.getString("place");
        l.details = r.getString("details") == null ? "" : r.getString("details");
        l.officer = r.getString("officer"); l.caseNo = r.getString("case_no"); l.status = r.getString("status");
        l.created = LocalDateTime.parse(r.getString("created"));
        return l;
    };
    static final Db.Mapper<TrafficRec> TRF = r -> {
        TrafficRec t = new TrafficRec();
        t.id = r.getInt("id"); t.citizenId = r.getString("citizen_id"); t.kind = r.getString("kind"); t.detail = r.getString("detail");
        t.status = r.getString("status"); t.created = LocalDateTime.parse(r.getString("created"));
        return t;
    };
    static final Db.Mapper<Payment> PAY = r -> {
        Payment p = new Payment();
        p.id = r.getInt("id"); p.citizenId = r.getString("citizen_id"); p.kind = r.getString("kind"); p.ref = r.getString("ref"); p.amount = r.getInt("amount");
        p.month = r.getString("month"); p.paid = r.getInt("paid") == 1; p.receipt = r.getString("receipt"); p.appId = r.getInt("app_id");
        p.created = LocalDateTime.parse(r.getString("created"));
        return p;
    };

    // ---------- citizens (the Home Affairs register) ----------
    public static Citizen citizen(String id) { return Db.one("select * from citizens where id=?", CZ, norm(id)); }

    public static List<Citizen> allCitizens() { return Db.list("select * from citizens order by name", CZ); }

    /** Any combination of the four boxes; every box that is filled must match. */
    public static List<Citizen> search(String name, String id, String phone, String fp) {
        StringBuilder sql = new StringBuilder("select * from citizens where 1=1");
        List<Object> p = new ArrayList<>();
        if (!name.isEmpty()) { sql.append(" and lower(name) like ?"); p.add("%" + name.toLowerCase() + "%"); }
        if (!id.isEmpty()) { sql.append(" and id=?"); p.add(norm(id)); }
        if (!phone.isEmpty()) { sql.append(" and replace(phone,' ','') like ?"); p.add("%" + phone.replace(" ", "") + "%"); }
        if (!fp.isEmpty()) { sql.append(" and upper(fingerprint)=?"); p.add(fp.toUpperCase()); }
        return Db.list(sql.append(" order by name limit 5").toString(), CZ, p.toArray());
    }

    public static void saveAccount(Citizen c) {
        Db.exec("update citizens set salt=?,hash=?,registered_name=?,account_verified=?,phone=? where id=?", c.salt, c.hash, c.registeredName, c.accountVerified, c.phone, c.id);
    }

    public static void setAccountVerified(String id) { Db.exec("update citizens set account_verified=1 where id=?", id); }

    /** The citizen's fingerprint code (for example FP-4821). Made once, the first time a fingerprint is scanned; an existing code is kept. */
    public static String ensureFingerprintCode(String citizenId) {
        Citizen c = citizen(citizenId);
        if (c.fingerprint != null && !c.fingerprint.isEmpty()) return c.fingerprint;
        SecureRandom rnd = new SecureRandom();
        String code;
        do { code = "FP-" + (3000 + rnd.nextInt(7000)); } while (Db.count("select count(*) from citizens where upper(fingerprint)=?", code) > 0);
        Db.exec("update citizens set fingerprint=? where id=?", code, citizenId);
        return code;
    }

    /** Home Affairs records a death. Open applications are rejected and unpaid pension payments are cancelled. */
    public static void registerDeath(String id) {
        Db.exec("update citizens set alive=0 where id=?", id);
        Db.exec("update applications set status='REJECTED', note='Home Affairs records the applicant as deceased' where citizen_id=? and status not in ('COMPLETED','REJECTED')", id);
        Db.exec("delete from payments where citizen_id=? and kind in ('PENSION','GRANT') and paid=0", id);
    }

    public static Official official(String staffId) { return Db.one("select * from officials where lower(staff_id)=lower(?)", OFF, staffId); }

    // ---------- birth certificates: a new ID number and a new register record ----------
    /** Lesotho ID numbers have 11 digits. The sample people use 0412345767 plus one last digit (04123457670 to 04123457678). */
    static final int ID_LENGTH = 11;

    /** An unused 11-digit ID number for a new person (a child at birth, or a first-time ID card): 0412345 and four more digits. */
    public static String newIdNumber(LocalDate dob, boolean male) {
        SecureRandom rnd = new SecureRandom();
        for (int i = 0; i < 2000; i++) {
            String id = "0412345" + String.format("%04d", rnd.nextInt(10000));
            if (citizen(id) == null) return id;
        }
        throw new IllegalStateException("No free ID number");
    }

    /** A child registered at birth. No account yet: the person can create one later with this ID number. */
    public static void addBirthCitizen(String id, String name, LocalDate dob, String gender, String address) {
        Db.exec("insert into citizens(id,name,gender,phone,email,address,dob,fingerprint,alive,salt,hash,registered_name,account_verified) values(?,?,?,?,?,?,?,?,1,null,null,null,0)",
                id, name, gender, "", "", address, dob, null);
    }

    public static String birthCert(String source, int appId) {
        List<String> l = Db.list("select id_number from birth_certs where source=? and app_id=?", rs -> rs.getString(1), source, appId);
        return l.isEmpty() ? null : l.get(0);
    }
    public static void saveBirthCert(String source, int appId, String idNumber) {
        Db.exec("insert into birth_certs(source,app_id,id_number) values(?,?,?)", source, appId, idNumber);
    }

    // ---------- government workers (they do not receive a pension) ----------
    public static boolean govtWorker(String citizenId) { return Db.count("select count(*) from govt_workers where citizen_id=?", citizenId) > 0; }

    public static String govtMinistry(String citizenId) {
        List<String> l = Db.list("select ministry from govt_workers where citizen_id=?", rs -> rs.getString(1), citizenId);
        return l.isEmpty() || l.get(0) == null ? "Government of Lesotho" : l.get(0);
    }

    public static List<Citizen> govtWorkers() { return Db.list("select c.* from citizens c join govt_workers g on g.citizen_id=c.id order by c.name", CZ); }

    /** Records the person as working for the government. A retired government employee can receive the grant OR the pension, never both (see benefitProblem). */
    public static void setGovtWorker(String citizenId, String ministry) {
        Db.exec("insert or replace into govt_workers(citizen_id,ministry,since) values(?,?,?)", citizenId, ministry, LocalDate.now());
    }

    // ---------- the choice a retired government employee makes: GRANT or PENSION ----------
    /** GRANT, PENSION, or null when the person has not chosen. */
    public static String benefitChoice(String citizenId) {
        List<String> l = Db.list("select choice from benefit_choice where citizen_id=?", rs -> rs.getString(1), citizenId);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Saves the choice and removes the other benefit: it leaves its register and unpaid payments are cancelled. */
    public static void setBenefitChoice(String citizenId, String kind) {
        Db.exec("insert or replace into benefit_choice(citizen_id,choice,chosen) values(?,?,?)", citizenId, kind, LocalDate.now());
        String other = kind.equals("GRANT") ? "PENSION" : "GRANT";
        Db.exec(other.equals("GRANT") ? "delete from pension where citizen_id=?" : "delete from gov_pension where citizen_id=?", citizenId);
        Db.exec("delete from payments where citizen_id=? and kind=? and paid=0", citizenId, other);
    }

    /**
     * The one rule Finance, Pensions and the citizen pages all use. Returns why this benefit must not be paid or applied for, or null when it is allowed.
     * Home Affairs: the person must be alive. Government records: a government employee may have only the benefit they chose.
     */
    public static String benefitProblem(Citizen c, String kind) {
        if (!c.alive) return "Home Affairs records this person as deceased. Payments are stopped.";
        if (govtWorker(c.id)) {
            String ch = benefitChoice(c.id);
            if (ch == null) return "Government employee: must choose the old-age grant or the retirement pension first. Both cannot be paid.";
            if (!ch.equals(kind)) return "Government employee: chose the " + Policy.benefitLabel(ch) + ". The " + Policy.benefitLabel(kind) + " cannot also be paid.";
        }
        return null;
    }

    public static void clearGovtWorker(String citizenId) { Db.exec("delete from govt_workers where citizen_id=?", citizenId); }

    // ---------- police address requests (Home Affairs decides) ----------
    public static AddressRequest addAddressRequest(String citizenId, String caseNo, String reason, String officer) {
        int id = Db.insert("insert into address_requests(citizen_id,case_no,reason,officer,status,requested) values(?,?,?,?,?,?)",
                citizenId, caseNo, reason, officer, "Pending", LocalDateTime.now());
        return addressRequest(id);
    }
    public static AddressRequest addressRequest(int id) { return Db.one("select * from address_requests where id=?", ADR, id); }
    /** The newest address request for this person in this case, or null. */
    public static AddressRequest latestAddressRequest(String citizenId, String caseNo) {
        return Db.one("select * from address_requests where citizen_id=? and case_no=? order by id desc limit 1", ADR, citizenId, caseNo);
    }
    public static List<AddressRequest> addressRequestsBy(String officer) { return Db.list("select * from address_requests where officer=? order by id desc limit 50", ADR, officer); }
    public static List<AddressRequest> addressRequestsPending() { return Db.list("select * from address_requests where status='Pending' order by id", ADR); }
    public static List<AddressRequest> addressRequestsDecided() { return Db.list("select * from address_requests where status<>'Pending' order by id desc limit 20", ADR); }
    public static void decideAddress(int id, String status, String by) {
        Db.exec("update address_requests set status=?, decided_by=?, decided=? where id=?", status, by, LocalDateTime.now(), id);
    }

    // ---------- police cases ----------
    public static CaseRec addCase(String title, String details, String citizenId, String officer) {
        int id = Db.insert("insert into cases(case_no,title,details,citizen_id,officer,status,opened,updated) values(?,?,?,?,?,?,?,?)",
                "", title, details, citizenId, officer, "Open case", LocalDateTime.now(), LocalDateTime.now());
        String no = "CAS-" + LocalDate.now().getYear() + "-" + String.format("%04d", id);
        Db.exec("update cases set case_no=? where id=?", no, id);
        return caseByNo(no);
    }
    public static CaseRec caseByNo(String no) { return Db.one("select * from cases where case_no=?", CASE, no == null ? "" : no.trim().toUpperCase()); }
    /** Cases that are linked to this person: a criminal record. */
    public static List<CaseRec> casesOfPerson(String citizenId) { return Db.list("select * from cases where citizen_id=? order by id", CASE, citizenId); }
    public static List<CaseRec> cases() { return Db.list("select * from cases order by (status='Closed'), id desc limit 100", CASE); }
    public static List<CaseRec> openCases() { return Db.list("select * from cases where status<>'Closed' order by id desc limit 100", CASE); }
    public static void updateCase(String no, String status) { Db.exec("update cases set status=?, updated=? where case_no=?", status, LocalDateTime.now(), no); }
    public static void linkCase(String no, String citizenId) { Db.exec("update cases set citizen_id=?, updated=? where case_no=?", citizenId, LocalDateTime.now(), no); }
    public static void addCaseNote(String no, String officer, String text) {
        Db.exec("insert into case_notes(case_no,officer,text,time) values(?,?,?,?)", no, officer, text, LocalDateTime.now());
        Db.exec("update cases set updated=? where case_no=?", LocalDateTime.now(), no);
    }
    public static List<CaseNote> notesOfCase(String no) { return Db.list("select * from case_notes where case_no=? order by id desc", CNOTE, no); }

    // ---------- lost IDs and passports (recorded by Police) ----------
    public static LostDoc addLost(String citizenId, String docType, String docNo, LocalDate lostDate, String place, String details, String officer, String caseNo) {
        int id = Db.insert("insert into lost_docs(citizen_id,doc_type,doc_no,lost_date,place,details,officer,case_no,status,created) values(?,?,?,?,?,?,?,?,?,?)",
                citizenId, docType, docNo, lostDate, place, details, officer, caseNo, "Reported", LocalDateTime.now());
        return lost(id);
    }
    public static LostDoc lost(int id) { return Db.one("select * from lost_docs where id=?", LOST, id); }
    public static List<LostDoc> lostOf(String citizenId) { return Db.list("select * from lost_docs where citizen_id=? order by id desc", LOST, citizenId); }
    public static List<LostDoc> lostRecent(int limit) { return Db.list("select * from lost_docs order by id desc limit ?", LOST, limit); }
    public static void setLostStatus(int id, String status) { Db.exec("update lost_docs set status=? where id=?", status, id); }

    // ---------- Traffic: vehicle and driving licence records ----------
    public static void addTraffic(String citizenId, String kind, String detail, String status) {
        Db.exec("insert into traffic_records(citizen_id,kind,detail,status,created) values(?,?,?,?,?)", citizenId, kind, detail, status, LocalDateTime.now());
    }
    public static boolean trafficHas(String citizenId, String kind, String detail) {
        return Db.count("select count(*) from traffic_records where citizen_id=? and kind=? and detail=?", citizenId, kind, detail) > 0;
    }
    public static List<TrafficRec> trafficOf(String citizenId) { return Db.list("select * from traffic_records where citizen_id=? order by id desc", TRF, citizenId); }

    // ---------- applications ----------
    public static Application app(int id) { return Db.one("select * from applications where id=?", APP, id); }
    public static List<Application> appsOf(String citizenId) { return Db.list("select * from applications where citizen_id=? order by id desc", APP, citizenId); }
    public static List<Application> appsOf(Dept d) { return Db.list("select * from applications where dept=? order by id desc", APP, d.name()); }

    public static boolean hasOpen(String citizenId, String service) {
        return Db.count("select count(*) from applications where citizen_id=? and service=? and status not in ('COMPLETED','REJECTED')", citizenId, service) > 0;
    }

    public static Application addApp(String citizenId, Dept d, String service, Status st, int fee, String note) {
        int id = Db.insert("insert into applications(citizen_id,dept,service,reference,note,status,created,fee,paid) values(?,?,?,?,?,?,?,?,0)",
                citizenId, d.name(), service, "", note, st.name(), LocalDateTime.now(), fee);
        Db.exec("update applications set reference=? where id=?", d.name() + "-" + LocalDate.now().getYear() + "-" + String.format("%04d", id), id);
        return app(id);
    }

    public static void saveApp(Application a) {
        Db.exec("update applications set status=?, note=?, paid=?, receipt=? where id=?", a.status.name(), a.note, a.paid, a.receipt, a.id);
    }

    // ---------- where a finished document is collected ----------
    public static void setCollection(int appId, String office) {
        Db.exec("insert or replace into collection(app_id,office,ready_on) values(?,?,?)", appId, office, LocalDate.now());
    }
    /** The district office chosen for collection, or null when none was set. */
    public static String collectionOf(int appId) {
        List<String> l = Db.list("select office from collection where app_id=?", rs -> rs.getString(1), appId);
        return l.isEmpty() ? null : l.get(0);
    }

    // ---------- documents ----------
    public static List<Doc> docsOf(String citizenId) { return Db.list("select * from documents where citizen_id=? order by doc_type", DOC, citizenId); }
    public static Doc doc(int id) { return Db.one("select * from documents where id=?", DOC, id); }
    public static Doc docOf(String citizenId, String type) { return Db.one("select * from documents where citizen_id=? and doc_type=?", DOC, citizenId, type); }

    /** One document per type. Uploading again replaces the old file and sends it back for verification. */
    public static void putDoc(String citizenId, String type, String filename, String stored, String mime, String status) {
        Doc old = docOf(citizenId, type);
        if (old != null) {
            if (old.stored != null) try { Files.deleteIfExists(UPLOADS.resolve(old.stored)); } catch (IOException ignored) { }
            Db.exec("delete from documents where id=?", old.id);
        }
        Db.exec("insert into documents(citizen_id,doc_type,filename,stored,mime,status,uploaded) values(?,?,?,?,?,?,?)", citizenId, type, filename, stored, mime, status, LocalDateTime.now());
    }

    public static void setDocStatus(int id, String status) { Db.exec("update documents set status=? where id=?", status, id); }

    public static List<Doc> uploadedDocs() { return Db.list("select * from documents where status='Uploaded' order by id", DOC); }

    // ---------- notifications and the access log ----------
    public static void notify(String citizenId, String text) {
        Db.exec("insert into notifications(citizen_id,text,time) values(?,?,?)", citizenId, text, LocalDateTime.now());
        Ussd.logSms(citizenId, text);
    }
    public static List<Notification> notesOf(String citizenId) { return Db.list("select * from notifications where citizen_id=? order by id desc", NOTE, citizenId); }

    /** Only the citizen reads this log (My Data page). Staff pages do not show it. */
    public static void logAccess(String citizenId, Dept d, String officer, String fields, String purpose) {
        Db.exec("insert into access_log(citizen_id,dept,officer,fields,purpose,time) values(?,?,?,?,?,?)", citizenId, d.name(), officer, fields, purpose, LocalDateTime.now());
    }
    public static List<AccessEntry> logOf(String citizenId) { return Db.list("select * from access_log where citizen_id=? order by id desc", ACC, citizenId); }

    // ---------- queue ----------
    public static Ticket addTicket(String citizenId, Dept d, String service) {
        int n = Db.count("select count(*) from tickets where dept=?", d.name());
        String number = d.letter + "-" + String.format("%03d", 100 + n);
        int id = Db.insert("insert into tickets(citizen_id,dept,service,number,time,served) values(?,?,?,?,?,0)", citizenId, d.name(), service, number, LocalDateTime.now());
        return Db.one("select * from tickets where id=?", TKT, id);
    }
    public static List<Ticket> ticketsOf(String citizenId) { return Db.list("select * from tickets where citizen_id=? order by id desc", TKT, citizenId); }
    public static List<Ticket> waiting(Dept d) { return Db.list("select * from tickets where dept=? and served=0 order by id", TKT, d.name()); }
    public static void serve(int id) { Db.exec("update tickets set served=1 where id=?", id); }

    // ---------- booked appointments ----------
    public static final int SLOT_CAPACITY = 3; // people per department per time slot

    public static Appointment addAppointment(String citizenId, Dept d, String service, LocalDateTime slot) {
        int id = Db.insert("insert into appointments(citizen_id,dept,service,slot,status,created) values(?,?,?,?,?,?)",
                citizenId, d.name(), service, slot.toString(), "Booked", LocalDateTime.now());
        return appointment(id);
    }
    public static Appointment appointment(int id) { return Db.one("select * from appointments where id=?", APT, id); }
    public static int slotsTaken(Dept d, LocalDateTime slot) { return Db.count("select count(*) from appointments where dept=? and slot=? and status='Booked'", d.name(), slot.toString()); }
    public static boolean hasBooking(String citizenId, Dept d, LocalDateTime slot) {
        return Db.count("select count(*) from appointments where citizen_id=? and dept=? and slot=? and status='Booked'", citizenId, d.name(), slot.toString()) > 0;
    }
    /** True when the citizen already has this service approved, ready or completed. */
    public static boolean hasIssued(String citizenId, String service) {
        return Db.count("select count(*) from applications where citizen_id=? and service=? and status in ('APPROVED','READY','COMPLETED')", citizenId, service) > 0;
    }
    /** A booked appointment at this department, today or later. */
    public static boolean hasUpcomingBooking(String citizenId, Dept d) {
        return Db.count("select count(*) from appointments where citizen_id=? and dept=? and status='Booked' and slot>=?", citizenId, d.name(), LocalDate.now().atStartOfDay().toString()) > 0;
    }
    public static List<Appointment> appointmentsOf(String citizenId) { return Db.list("select * from appointments where citizen_id=? order by slot desc", APT, citizenId); }
    public static List<Appointment> upcoming(Dept d) {
        return Db.list("select * from appointments where dept=? and status='Booked' and slot>=? order by slot", APT, d.name(), LocalDate.now().atStartOfDay().toString());
    }
    public static void cancelAppointment(int id) { Db.exec("update appointments set status='Cancelled' where id=?", id); }

    // ---------- payments (service fees and pensions) ----------
    public static String nextReceipt() { return "RCT-" + LocalDate.now().getYear() + "-" + String.format("%04d", Db.count("select count(*) from payments where receipt is not null") + 1); }

    public static void addPayment(String citizenId, String kind, String ref, int amount, String month, boolean paid, String receipt, int appId) {
        Db.exec("insert into payments(citizen_id,kind,ref,amount,month,paid,receipt,app_id,created) values(?,?,?,?,?,?,?,?,?)", citizenId, kind, ref, amount, month, paid, receipt, appId, LocalDateTime.now());
    }
    public static Payment pensionPayment(String citizenId, YearMonth m, String kind) { return Db.one("select * from payments where citizen_id=? and kind=? and month=?", PAY, citizenId, kind, m.toString()); }
    /** Grant and pension payments, newest first. */
    public static List<Payment> benefitPayments() { return Db.list("select * from payments where kind in ('PENSION','GRANT') order by id desc", PAY); }
    public static List<Payment> benefitPaymentsOf(String citizenId) { return Db.list("select * from payments where citizen_id=? and kind in ('PENSION','GRANT') order by id desc", PAY, citizenId); }
    public static List<Payment> paymentsOf(String citizenId) { return Db.list("select * from payments where citizen_id=? order by id desc", PAY, citizenId); }
    public static List<Payment> payments(String kind) { return Db.list("select * from payments where kind=? order by id desc", PAY, kind); }
    public static Payment payment(int id) { return Db.one("select * from payments where id=?", PAY, id); }
    public static void markPaid(int id, String receipt) { Db.exec("update payments set paid=1, receipt=? where id=?", receipt, id); }

    // ---------- pension register ----------
    public static boolean enrolled(String citizenId) { return Db.count("select count(*) from pension where citizen_id=?", citizenId) > 0; }
    public static void enrol(String citizenId) { if (!enrolled(citizenId)) Db.exec("insert into pension(citizen_id,since) values(?,?)", citizenId, LocalDate.now()); }
    // retirement pension register (the grant register is the "pension" table above)
    public static boolean pensionEnrolled(String citizenId) { return Db.count("select count(*) from gov_pension where citizen_id=?", citizenId) > 0; }
    public static void enrolPension(String citizenId) { if (!pensionEnrolled(citizenId)) Db.exec("insert into gov_pension(citizen_id,since) values(?,?)", citizenId, LocalDate.now()); }
    public static List<Citizen> pensionList() { return Db.list("select c.* from citizens c join gov_pension p on p.citizen_id=c.id order by c.name", CZ); }

    // ---------- money the Finance department hands to other departments ----------
    public static void addAllocation(Dept d, int amount, String purpose, String officer) {
        Db.exec("insert into allocations(dept,amount,purpose,officer,created) values(?,?,?,?,?)", d.name(), amount, purpose, officer, LocalDateTime.now());
    }
    public static List<String[]> allocations(int limit) {
        return Db.list("select dept,amount,purpose,officer,created from allocations order by id desc limit " + limit,
                rs -> new String[]{rs.getString(1), String.valueOf(rs.getInt(2)), rs.getString(3), rs.getString(4), rs.getString(5)});
    }
    public static int allocatedTotal() { Integer n = Db.one("select coalesce(sum(amount),0) from allocations", rs -> rs.getInt(1)); return n == null ? 0 : n; }
    public static int allocatedTo(Dept d) { Integer n = Db.one("select coalesce(sum(amount),0) from allocations where dept=?", rs -> rs.getInt(1), d.name()); return n == null ? 0 : n; }
    /** Fees paid, grouped by the department that gave the service: {department code, total}. */
    public static List<String[]> incomeByDept() {
        return Db.list("select a.dept, sum(p.amount) from payments p join applications a on a.id=p.app_id where p.kind='FEE' and p.paid=1 group by a.dept",
                rs -> new String[]{rs.getString(1), String.valueOf(rs.getInt(2))});
    }
    public static int paidTotal(String... kinds) {
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < kinds.length; i++) in.append(i > 0 ? ",?" : "?");
        Integer n = Db.one("select coalesce(sum(amount),0) from payments where paid=1 and kind in (" + in + ")", rs -> rs.getInt(1), (Object[]) kinds);
        return n == null ? 0 : n;
    }

    public static List<Citizen> enrolledList() { return Db.list("select c.* from citizens c join pension p on p.citizen_id=c.id order by c.name", CZ); }

    // ---------- one-time change of old 13-digit ID numbers to 11-digit Lesotho ID numbers ----------
    private static final String[][] OLD_SEED_IDS = {
            {"9901015678082", "04123457670"},
            {"8804121234081", "04123457671"},
            {"9505055057081", "04123457672"},
            {"5106155051083", "04123457673"},
            {"5605205052084", "04123457674"},
            {"4903105053085", "04123457675"},
            {"4201105054086", "04123457676"},
            {"5405105055087", "04123457677"},
            {"5606305056088", "04123457678"}
    };

    /** One-time renames from the earlier version: the old-age pension became the old-age grant, the retirement claim became the retirement pension. */
    // ---------- tax standing (SRL tells us; Finance only holds money back) ----------
    public static void initTax() {
        Db.exec("create table if not exists pension_invites(citizen_id text primary key, sent text)");
        Db.exec("create table if not exists tax_standing(citizen_id text primary key, owed integer, updated text)");
        Db.exec("create table if not exists lra_transfers(id integer primary key autoincrement,citizen_id text,amount integer,ref text,created text)");
        if (Db.count("select count(*) from tax_standing") == 0) {
            List<String> ids = Db.list("select id from citizens order by id limit 1 offset 2", rs -> rs.getString(1));
            for (String id : ids) Db.exec("insert into tax_standing(citizen_id,owed,updated) values(?,?,?)", id, 1200, LocalDate.now().toString());
        }
    }
    public static int taxOwed(String citizenId) { return Db.count("select owed from tax_standing where citizen_id=?", citizenId); }
    public static void setTaxOwed(String citizenId, int owed) {
        Db.exec("insert or replace into tax_standing(citizen_id,owed,updated) values(?,?,?)", citizenId, Math.max(0, owed), LocalDate.now().toString());
    }
    /** A government employee\'s choice is cleared so they can choose again. Not allowed once a benefit was paid. Returns an error text or null. */
    public static String resetChoice(String citizenId) {
        if (Db.count("select count(*) from payments where citizen_id=? and kind in ('PENSION','GRANT') and paid=1", citizenId) > 0)
            return "A grant or pension was already paid to this person, so the choice cannot be changed.";
        Db.exec("delete from benefit_choice where citizen_id=?", citizenId);
        Db.exec("delete from pension where citizen_id=?", citizenId);
        Db.exec("delete from gov_pension where citizen_id=?", citizenId);
        Db.exec("delete from payments where citizen_id=? and kind in ('PENSION','GRANT') and paid=0", citizenId);
        return null;
    }
    public static boolean invitedToPension(String citizenId) { return Db.count("select count(*) from pension_invites where citizen_id=?", citizenId) > 0; }
    public static void markInvited(String citizenId) { Db.exec("insert or ignore into pension_invites(citizen_id,sent) values(?,?)", citizenId, LocalDate.now().toString()); }

    /** Money held back from a payment and sent to SRL. */
    public static void lraTransfer(String citizenId, int amount, String ref) {
        Db.exec("insert into lra_transfers(citizen_id,amount,ref,created) values(?,?,?,?)", citizenId, amount, ref, LocalDateTime.now().toString());
    }
    public static List<String[]> lraOf(String citizenId) {
        return Db.list("select amount,ref,created from lra_transfers where citizen_id=? order by id desc", rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, citizenId);
    }
    public static int lraTotal() { return Db.count("select coalesce(sum(amount),0) from lra_transfers"); }
    public static List<String[]> lraTransfers() {
        return Db.list("select t.citizen_id,c.name,t.amount,t.ref,t.created from lra_transfers t left join citizens c on c.id=t.citizen_id order by t.id desc limit 30",
                rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)});
    }
    public static List<String[]> taxOwing() {
        return Db.list("select t.citizen_id,c.name,t.owed from tax_standing t left join citizens c on c.id=t.citizen_id where t.owed>0 order by c.name",
                rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
    }

    // ---------- Traffic fines and the traffic register ----------
    public static final List<String> OFFENCES = List.of("Speeding", "Driving without a licence", "Driving an unroadworthy vehicle", "Overloading",
            "Drink driving", "Not wearing a seatbelt", "Ignoring a traffic light or stop sign", "Using a phone while driving", "Parking offence", "Other");
    public static void initTraffic() {
        Db.exec("create table if not exists traffic_fines(id integer primary key autoincrement,citizen_id text,offence text,amount integer,place text,offence_date text,officer text,status text,receipt text,created text)");
    }
    public static int addFine(String citizenId, String offence, int amount, String place, String date, String officer) {
        return Db.insert("insert into traffic_fines(citizen_id,offence,amount,place,offence_date,officer,status,receipt,created) values(?,?,?,?,?,?,?,?,?)",
                citizenId, offence, amount, place, date, officer, "Unpaid", "", LocalDateTime.now().toString());
    }
    private static final Db.Mapper<String[]> FINE = rs -> new String[]{rs.getString("id"), rs.getString("citizen_id"), rs.getString("offence"), rs.getString("amount"),
            rs.getString("place"), rs.getString("offence_date"), rs.getString("officer"), rs.getString("status"), rs.getString("receipt")};
    /** {id, citizen_id, offence, amount, place, date, officer, status, receipt} */
    public static List<String[]> finesOf(String citizenId) { return Db.list("select * from traffic_fines where citizen_id=? order by id desc", FINE, citizenId); }
    public static List<String[]> recentFines() { return Db.list("select * from traffic_fines order by (status='Paid'), id desc limit 40", FINE); }
    public static String[] fine(int id) { return Db.one("select * from traffic_fines where id=?", FINE, id); }
    public static void payFine(int id, String receipt) { Db.exec("update traffic_fines set status='Paid', receipt=? where id=?", receipt, id); }
    public static List<String> searchTrafficPeople(String q) {
        String like = "%" + q.toLowerCase() + "%";
        return Db.list("select distinct c.id from citizens c left join traffic_records t on t.citizen_id=c.id where c.id=? or lower(c.name) like ? or lower(t.detail) like ? order by c.name limit 20",
                rs -> rs.getString(1), Store.norm(q), like, like);
    }

    // ---------- ID cards, name changes and notices to departments ----------
    public static void initIdCards() {
        Db.exec("create table if not exists id_cards(citizen_id text primary key, issued text)");
        Db.exec("create table if not exists dept_notices(id integer primary key autoincrement,dept text,citizen_id text,text text,created text)");
        Db.exec("delete from dept_notices where text not like '%You are told because you hold%'"); // old notices that were sent to every department
        if (Db.count("select count(*) from id_cards") == 0)
            Db.exec("insert into id_cards(citizen_id,issued) select id, date('now') from citizens where dob <= date('now','-16 years')");
    }
    public static boolean hasIdCard(String citizenId) { return Db.count("select count(*) from id_cards where citizen_id=?", citizenId) > 0; }
    public static void giveIdCard(String citizenId) { Db.exec("insert or ignore into id_cards(citizen_id,issued) values(?,?)", citizenId, LocalDate.now().toString()); }

    /** Changes the person's name in the register. Every department reads the name from there, so it changes everywhere. */
    public static void rename(String citizenId, String newName) {
        Db.exec("update citizens set name=? where id=?", newName, citizenId);
        Db.exec("update citizens set registered_name=? where id=? and registered_name is not null and registered_name<>''", newName, citizenId);
    }
    /** The departments (other than Home Affairs) that hold something about this person. */
    /** The departments (other than Home Affairs) that hold a real record about this person, with the reason for each. */
    public static java.util.Map<Dept, String> connectedDepts(String citizenId) {
        java.util.Map<Dept, String> m = new java.util.LinkedHashMap<>();
        for (Dept d : Dept.values()) {
            if (d == Dept.HA) continue;
            List<String> why = new ArrayList<>();
            int apps = Db.count("select count(*) from applications where citizen_id=? and dept=? and status not in ('REJECTED','PAYMENT_DUE')", citizenId, d.name());
            if (apps > 0) why.add(apps + " application" + (apps == 1 ? "" : "s"));
            if (d == Dept.PE && (pensionEnrolled(citizenId) || enrolled(citizenId))) why.add("a grant or pension registration");
            if ((d == Dept.PE || d == Dept.FI) && govtWorker(citizenId)) why.add("a place on the government workers list");
            if (d == Dept.FI && Db.count("select count(*) from payments where citizen_id=?", citizenId) > 0) why.add("payments");
            if (d == Dept.PA && Db.count("select count(*) from passports where citizen_id=?", citizenId) > 0) why.add("a passport");
            if (d == Dept.TR && Db.count("select count(*) from traffic_records where citizen_id=?", citizenId) > 0) why.add("traffic records");
            if (d == Dept.TR && Db.count("select count(*) from certificates where citizen_id=?", citizenId) > 0) why.add("a licence or certificate");
            if (d == Dept.PO && Db.count("select count(*) from cases where citizen_id=?", citizenId) > 0) why.add("a police case");
            if (!why.isEmpty()) m.put(d, String.join(", ", why));
        }
        return m;
    }
    public static void noticeDept(Dept d, String citizenId, String text) {
        Db.exec("insert into dept_notices(dept,citizen_id,text,created) values(?,?,?,?)", d.name(), citizenId, text, LocalDateTime.now().toString());
    }
    public static List<String[]> deptNotices(Dept d) {
        return Db.list("select text,created from dept_notices where dept=? and created>=? order by id desc limit 10", rs -> new String[]{rs.getString(1), rs.getString(2)}, d.name(), LocalDateTime.now().minusDays(30).toString());
    }

    /** Passport register: every passport Passport Services issued. Creates the table and one example the first time. */
    public static void initPassports() {
        Db.exec("create table if not exists passports(id integer primary key autoincrement,citizen_id text,number text,issued text,expires text,status text,app_id integer)");
        if (Db.count("select count(*) from passports") == 0) {
            List<String> ids = Db.list("select id from citizens order by id limit 2", rs -> rs.getString(1));
            for (int i = 0; i < ids.size(); i++)
                Db.exec("insert into passports(citizen_id,number,issued,expires,status,app_id) values(?,?,?,?,?,0)", ids.get(i), "LS" + (4100230 + i),
                        LocalDate.now().minusYears(i == 0 ? 9 : 2).minusMonths(i == 0 ? 3 : 0).toString(), LocalDate.now().plusYears(i == 0 ? 0 : 8).plusMonths(i == 0 ? 3 : 0).toString(), "Valid");
        }
    }
    /** The passport the citizen holds now: {number, issued, expires}, or null. */
    public static String[] currentPassport(String citizenId) {
        List<String[]> l = Db.list("select number,issued,expires from passports where citizen_id=? and status='Valid' order by expires desc limit 1", rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, citizenId);
        return l.isEmpty() ? null : l.get(0);
    }
    /** A passport was issued: the old one is replaced and the new one gets a number and ten years. */
    public static String issuePassport(String citizenId, int appId) {
        if (Db.count("select count(*) from passports where app_id=?", appId) > 0) return null;
        Db.exec("update passports set status='Replaced' where citizen_id=? and status='Valid'", citizenId);
        String no = "LS" + (4100000 + Db.count("select count(*) from passports") * 7 + 231);
        Db.exec("insert into passports(citizen_id,number,issued,expires,status,app_id) values(?,?,?,?,?,?)", citizenId, no, LocalDate.now().toString(), LocalDate.now().plusYears(10).toString(), "Valid", appId);
        return no;
    }

    public static void migrateBenefits() {
        Db.exec("update payments set kind='GRANT', ref=replace(ref,'Pension ','Old-Age Grant ') where kind='PENSION' and ref like 'Pension %'");
        Db.exec("update applications set service='Old-Age Grant Application' where service='Old-Age Pension Application'");
        Db.exec("update applications set service='Retirement Pension Claim' where service='Retirement Benefit Claim'");
    }

    /** Changes every ID number that is not 11 digits long, in every table. Safe to run on every start-up: it does nothing when all IDs are already 11 digits. */
    public static void migrateIds() {
        List<String[]> old = Db.list("select id,dob,gender from citizens where length(id)<>" + ID_LENGTH, rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
        if (old.isEmpty()) return;
        String[] tables = {"documents", "notifications", "access_log", "tickets", "appointments", "payments", "applications", "pension", "govt_workers",
                "address_requests", "cases", "lost_docs", "traffic_records", "travel_flags"};
        for (String[] c : old) {
            String from = c[0], to = null;
            for (String[] s : OLD_SEED_IDS) if (s[0].equals(from)) to = s[1];
            if (to == null || citizen(to) != null) to = newIdNumber(LocalDate.parse(c[1]), "Male".equals(c[2]));
            Db.exec("update citizens set id=? where id=?", to, from);
            for (String t : tables) Db.exec("update " + t + " set citizen_id=? where citizen_id=?", to, from);
            Db.exec("update birth_certs set id_number=? where id_number=?", to, from);
            Db.exec("update public_apps set id_number=? where id_number=?", to, from);
            Db.exec("update form_answers set value=? where value=?", to, from);
            Db.exec("update public_answers set value=? where value=?", to, from);
            Db.exec("update notifications set text=replace(text,?,?) where text like ?", from, to, "%" + from + "%");
            Db.exec("update applications set note=replace(note,?,?) where note like ?", from, to, "%" + from + "%");
            Db.exec("update public_apps set note=replace(note,?,?) where note like ?", from, to, "%" + from + "%");
            Db.exec("update case_notes set text=replace(text,?,?) where text like ?", from, to, "%" + from + "%");
            Db.exec("update access_log set purpose=replace(purpose,?,?) where purpose like ?", from, to, "%" + from + "%");
            System.out.println("ID number changed: " + from + " -> " + to);
        }
    }

    // ---------- start-up: create tables and sample data ----------
    public static void init(String dbFile) throws IOException {
        Files.createDirectories(UPLOADS);
        Db.open(dbFile);
        Db.exec("create table if not exists citizens(id text primary key,name text,gender text,phone text,email text,address text,dob text,fingerprint text,alive integer default 1,salt text,hash text,registered_name text,account_verified integer default 0)");
        Db.exec("create table if not exists officials(staff_id text primary key,name text,dept text,salt text,hash text)");
        Db.exec("create table if not exists applications(id integer primary key autoincrement,citizen_id text,dept text,service text,reference text,note text,status text,created text,fee integer default 0,paid integer default 0,receipt text)");
        Db.exec("create table if not exists documents(id integer primary key autoincrement,citizen_id text,doc_type text,filename text,stored text,mime text,status text,uploaded text)");
        Db.exec("create table if not exists notifications(id integer primary key autoincrement,citizen_id text,text text,time text)");
        Db.exec("create table if not exists access_log(id integer primary key autoincrement,citizen_id text,dept text,officer text,fields text,purpose text,time text)");
        Db.exec("create table if not exists tickets(id integer primary key autoincrement,citizen_id text,dept text,service text,number text,time text,served integer default 0)");
        Db.exec("create table if not exists appointments(id integer primary key autoincrement,citizen_id text,dept text,service text,slot text,status text,created text)");
        Db.exec("create table if not exists payments(id integer primary key autoincrement,citizen_id text,kind text,ref text,amount integer,month text,paid integer default 0,receipt text,app_id integer,created text)");
        Db.exec("create table if not exists pension(citizen_id text primary key,since text)");
        Db.exec("create table if not exists govt_workers(citizen_id text primary key,ministry text,since text)");
        Db.exec("create table if not exists gov_pension(citizen_id text primary key,since text)");
        Db.exec("create table if not exists benefit_choice(citizen_id text primary key,choice text,chosen text)");
        Db.exec("create table if not exists allocations(id integer primary key autoincrement,dept text,amount integer,purpose text,officer text,created text)");
        Db.exec("create table if not exists address_requests(id integer primary key autoincrement,citizen_id text,case_no text,reason text,officer text,status text,requested text,decided_by text,decided text)");
        Db.exec("create table if not exists birth_certs(source text,app_id integer,id_number text)");
        Db.exec("create table if not exists collection(app_id integer primary key,office text,ready_on text)");
        Db.exec("create table if not exists cases(id integer primary key autoincrement,case_no text,title text,details text,citizen_id text,officer text,status text,opened text,updated text)");
        Db.exec("create table if not exists case_notes(id integer primary key autoincrement,case_no text,officer text,text text,time text)");
        Db.exec("create table if not exists lost_docs(id integer primary key autoincrement,citizen_id text,doc_type text,doc_no text,lost_date text,place text,details text,officer text,case_no text,status text,created text)");
        Db.exec("create table if not exists traffic_records(id integer primary key autoincrement,citizen_id text,kind text,detail text,status text,created text)");
        if (Db.count("select count(*) from citizens") == 0) seed();
    }

    private static void cz(String id, String name, LocalDate dob, String gender, String phone, String address, String fp, String pw, boolean alive) {
        String salt = pw == null ? null : salt();
        Db.exec("insert into citizens(id,name,gender,phone,email,address,dob,fingerprint,alive,salt,hash,registered_name,account_verified) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, name, gender, phone, "", address, dob, fp, alive, salt, pw == null ? null : hash(salt, pw), pw == null ? null : name, pw != null);
        Db.exec("insert into documents(citizen_id,doc_type,filename,stored,mime,status,uploaded) values(?,?,?,?,?,?,?)", id, "National ID copy", "paper record", null, null, "Verified", LocalDateTime.now());
        Db.exec("insert into documents(citizen_id,doc_type,filename,stored,mime,status,uploaded) values(?,?,?,?,?,?,?)", id, "Birth certificate", "paper record", null, null, "Verified", LocalDateTime.now());
    }

    private static void staff(String id, String name, Dept d) {
        String s = salt();
        Db.exec("insert into officials(staff_id,name,dept,salt,hash) values(?,?,?,?,?)", id, name, d.name(), s, hash(s, "pass123"));
    }

    /** Sample register, staff, pension records, Traffic records and one police case. There are no made-up applications: every application is made through the real forms. */
    private static void seed() {
        LocalDate t = LocalDate.now();
        cz("04123457670", "Thabo Mokoena", LocalDate.of(1999, 1, 1), "Male", "+266 5712 3456", "Maseru, Lesotho", "FP-1001", "thabo123", true);
        cz("04123457671", "Mpho Letsie", LocalDate.of(1988, 4, 12), "Female", "+266 5890 1122", "Leribe, Lesotho", "FP-1002", "mpho123", true);
        cz("04123457672", "Lerato Mabote", LocalDate.of(1995, 5, 5), "Female", "+266 5333 7788", "Mafeteng, Lesotho", "FP-1003", null, true);
        cz("04123457673", "Mokete Tau", t.minusYears(72).minusDays(100), "Male", "+266 5611 2200", "Berea, Lesotho", "FP-2001", null, true);
        cz("04123457674", "Nthabeleng Rakoto", t.minusYears(61).minusDays(12), "Female", "+266 5622 3300", "Maseru, Lesotho", "FP-2002", "nthabeleng123", true);
        cz("04123457675", "Lineo Khati", t.minusYears(75).minusDays(40), "Female", "+266 5633 4400", "Mohale's Hoek, Lesotho", "FP-2003", null, true);
        cz("04123457676", "Palesa Nkosi", t.minusYears(80).minusDays(5), "Female", "+266 5644 5500", "Quthing, Lesotho", "FP-2004", null, false);
        cz("04123457677", "Retsepile Mohapi", t.minusYears(71).minusDays(200), "Male", "+266 5655 6600", "Thaba-Tseka, Lesotho", "FP-2005", null, true);
        cz("04123457678", "Tumelo Sekhonyana", t.minusYears(60).plusDays(25), "Male", "+266 5666 7700", "Butha-Buthe, Lesotho", "FP-2006", null, true);
        for (String[] d : new String[][]{{"04123457671", "Proof of residence", "Verified"}, {"04123457670", "Proof of residence", "Uploaded"}, {"04123457674", "Proof of residence", "Verified"}})
            Db.exec("insert into documents(citizen_id,doc_type,filename,stored,mime,status,uploaded) values(?,?,?,?,?,?,?)", d[0], d[1], "paper record", null, null, d[2], LocalDateTime.now());

        staff("HA001", "Lindiwe Mokoena", Dept.HA);
        staff("TR001", "M. Lephoko", Dept.TR);
        staff("FI001", "K. Molapo", Dept.FI);
        staff("PE001", "N. Mokhapi", Dept.PE);
        staff("PO001", "S. Ramoroka", Dept.PO);
        staff("PA001", "D. Letuka", Dept.PA);

        for (String id : new String[]{"04123457673", "04123457675", "04123457676", "04123457677"}) enrol(id);
        YearMonth now = YearMonth.now();
        addPayment("04123457675", "GRANT", "Old-Age Grant " + now, Policy.PENSION_AMOUNT, now.toString(), true, "RCT-" + t.getYear() + "-0001", 0);
        addPayment("04123457677", "GRANT", "Old-Age Grant " + now, Policy.PENSION_AMOUNT, now.toString(), false, null, 0);

        addTraffic("04123457670", "Vehicle", "Toyota Corolla 2015, white, registration A 1234", "Registered");
        addTraffic("04123457670", "Driving licence", "Licence B-004512, class: Light motor vehicle, expires 2028-06-30", "Valid");
        addTraffic("04123457671", "Driving licence", "Licence B-007781, class: Light motor vehicle, expires 2027-03-15", "Valid");
        addCase("Sample case: house break-in reported", "Reported at Maseru central. The suspect is not known yet.", null, "S. Ramoroka");

        Db.exec("insert into govt_workers(citizen_id,ministry,since) values(?,?,?)", "04123457674", "Ministry of Education", t.minusYears(30));
        notify("04123457670", "Welcome! Your identity was verified once by Home Affairs.");
    }
}