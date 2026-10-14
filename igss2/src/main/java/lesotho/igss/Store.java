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

    /** Home Affairs records a death. Open applications are rejected and unpaid pension payments are cancelled. */
    public static void registerDeath(String id) {
        Db.exec("update citizens set alive=0 where id=?", id);
        Db.exec("update applications set status='REJECTED', note='Home Affairs records the applicant as deceased' where citizen_id=? and status not in ('COMPLETED','REJECTED')", id);
        Db.exec("delete from payments where citizen_id=? and kind='PENSION' and paid=0", id);
    }

    public static Official official(String staffId) { return Db.one("select * from officials where lower(staff_id)=lower(?)", OFF, staffId); }

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
    public static void notify(String citizenId, String text) { Db.exec("insert into notifications(citizen_id,text,time) values(?,?,?)", citizenId, text, LocalDateTime.now()); }
    public static List<Notification> notesOf(String citizenId) { return Db.list("select * from notifications where citizen_id=? order by id desc", NOTE, citizenId); }

    public static void logAccess(String citizenId, Dept d, String officer, String fields, String purpose) {
        Db.exec("insert into access_log(citizen_id,dept,officer,fields,purpose,time) values(?,?,?,?,?,?)", citizenId, d.name(), officer, fields, purpose, LocalDateTime.now());
    }
    public static List<AccessEntry> logOf(String citizenId) { return Db.list("select * from access_log where citizen_id=? order by id desc", ACC, citizenId); }
    public static List<AccessEntry> logRecent(Dept onlyDept, int limit) {
        return onlyDept == null ? Db.list("select * from access_log order by id desc limit ?", ACC, limit)
            : Db.list("select * from access_log where dept=? order by id desc limit ?", ACC, onlyDept.name(), limit);
    }

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

    // ---------- payments (service fees and pensions) ----------
    public static String nextReceipt() { return "RCT-" + LocalDate.now().getYear() + "-" + String.format("%04d", Db.count("select count(*) from payments where receipt is not null") + 1); }

    public static void addPayment(String citizenId, String kind, String ref, int amount, String month, boolean paid, String receipt, int appId) {
        Db.exec("insert into payments(citizen_id,kind,ref,amount,month,paid,receipt,app_id,created) values(?,?,?,?,?,?,?,?,?)", citizenId, kind, ref, amount, month, paid, receipt, appId, LocalDateTime.now());
    }
    public static Payment pensionPayment(String citizenId, YearMonth m) { return Db.one("select * from payments where citizen_id=? and kind='PENSION' and month=?", PAY, citizenId, m.toString()); }
    public static List<Payment> paymentsOf(String citizenId) { return Db.list("select * from payments where citizen_id=? order by id desc", PAY, citizenId); }
    public static List<Payment> payments(String kind) { return Db.list("select * from payments where kind=? order by id desc", PAY, kind); }
    public static Payment payment(int id) { return Db.one("select * from payments where id=?", PAY, id); }
    public static void markPaid(int id, String receipt) { Db.exec("update payments set paid=1, receipt=? where id=?", receipt, id); }

    // ---------- pension register ----------
    public static boolean enrolled(String citizenId) { return Db.count("select count(*) from pension where citizen_id=?", citizenId) > 0; }
    public static void enrol(String citizenId) { if (!enrolled(citizenId)) Db.exec("insert into pension(citizen_id,since) values(?,?)", citizenId, LocalDate.now()); }
    public static List<Citizen> enrolledList() { return Db.list("select c.* from citizens c join pension p on p.citizen_id=c.id order by c.name", CZ); }

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
        Db.exec("create table if not exists payments(id integer primary key autoincrement,citizen_id text,kind text,ref text,amount integer,month text,paid integer default 0,receipt text,app_id integer,created text)");
        Db.exec("create table if not exists pension(citizen_id text primary key,since text)");
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

    private static void seed() {
        LocalDate t = LocalDate.now();
        cz("9901015678082", "Thabo Mokoena", LocalDate.of(1999, 1, 1), "Male", "+266 5712 3456", "Maseru, Lesotho", "FP-1001", "thabo123", true);
        cz("8804121234081", "Mpho Letsie", LocalDate.of(1988, 4, 12), "Female", "+266 5890 1122", "Leribe, Lesotho", "FP-1002", "mpho123", true);
        cz("9505055057081", "Lerato Mabote", LocalDate.of(1995, 5, 5), "Female", "+266 5333 7788", "Mafeteng, Lesotho", "FP-1003", null, true);
        cz("5106155051083", "Mokete Tau", t.minusYears(72).minusDays(100), "Male", "+266 5611 2200", "Berea, Lesotho", "FP-2001", null, true);
        cz("5605205052084", "Nthabeleng Rakoto", t.minusYears(61).minusDays(12), "Female", "+266 5622 3300", "Maseru, Lesotho", "FP-2002", "nthabeleng123", true);
        cz("4903105053085", "Lineo Khati", t.minusYears(75).minusDays(40), "Female", "+266 5633 4400", "Mohale's Hoek, Lesotho", "FP-2003", null, true);
        cz("4201105054086", "Palesa Nkosi", t.minusYears(80).minusDays(5), "Female", "+266 5644 5500", "Quthing, Lesotho", "FP-2004", null, false);
        cz("5405105055087", "Retsepile Mohapi", t.minusYears(71).minusDays(200), "Male", "+266 5655 6600", "Thaba-Tseka, Lesotho", "FP-2005", null, true);
        cz("5606305056088", "Tumelo Sekhonyana", t.minusYears(60).plusDays(25), "Male", "+266 5666 7700", "Butha-Buthe, Lesotho", "FP-2006", null, true);
        for (String[] d : new String[][]{{"8804121234081", "Proof of residence", "Verified"}, {"8804121234081", "Passport photo", "Verified"}, {"9901015678082", "Proof of residence", "Uploaded"}, {"5605205052084", "Proof of residence", "Verified"}})
            Db.exec("insert into documents(citizen_id,doc_type,filename,stored,mime,status,uploaded) values(?,?,?,?,?,?,?)", d[0], d[1], "paper record", null, null, d[2], LocalDateTime.now());

        staff("HA001", "Lindiwe Mokoena", Dept.HA);
        staff("TR001", "M. Lephoko", Dept.TR);
        staff("FI001", "K. Molapo", Dept.FI);
        staff("PE001", "N. Mokhapi", Dept.PE);
        staff("PO001", "S. Ramoroka", Dept.PO);
        staff("PA001", "D. Letuka", Dept.PA);

        Application a = addApp("9901015678082", Dept.PA, "New Passport", Status.PROCESSING, 300, "");
        a.paid = true; a.receipt = "RCT-" + t.getYear() + "-0001"; saveApp(a);
        addPayment(a.citizenId, "FEE", a.reference, 300, null, true, a.receipt, a.id);
        addApp("9901015678082", Dept.TR, "Driving Licence Renewal", Status.PAYMENT_DUE, 100, "");
        Application b = addApp("8804121234081", Dept.HA, "ID Replacement (lost or damaged)", Status.APPROVED, 100, "");
        b.paid = true; b.receipt = "RCT-" + t.getYear() + "-0002"; saveApp(b);
        addPayment(b.citizenId, "FEE", b.reference, 100, null, true, b.receipt, b.id);

        for (String id : new String[]{"5106155051083", "4903105053085", "4201105054086", "5405105055087"}) enrol(id);
        YearMonth now = YearMonth.now();
        addPayment("4903105053085", "PENSION", "Pension " + now, Policy.PENSION_AMOUNT, now.toString(), true, "RCT-" + t.getYear() + "-0003", 0);
        addPayment("5405105055087", "PENSION", "Pension " + now, Policy.PENSION_AMOUNT, now.toString(), false, null, 0);

        notify("9901015678082", "Welcome! Your identity was verified once by Home Affairs.");
        logAccess("9901015678082", Dept.TR, "M. Lephoko", "Name, ID number, Date of birth, Photo", "Driving licence renewal");
    }
}
