package lesotho.igss;

import lesotho.igss.Models.Citizen;
import lesotho.igss.Models.Dept;
import lesotho.igss.Models.Official;

import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Applications that need NO login: national ID card, birth registration and birth certificate copy.
 *   /public               choose a service
 *   /public/id            National ID card (first-time, replacement, renewal), with photo upload or camera
 *   /public/birth         Register a birth
 *   /public/birth-copy    Birth certificate copy
 *   /public/done          confirmation with reference and tracking code
 *   /public/track         check progress with the reference and tracking code
 * Home Affairs staff process them under /staff/public.
 */
final class PublicApply {
    private PublicApply() {}

    static final int MAX_FILE = 5 * 1024 * 1024;
    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);
    static final List<String> DISTRICTS = List.of("Berea", "Butha-Buthe", "Leribe", "Mafeteng", "Maseru",
            "Mohale's Hoek", "Mokhotlong", "Qacha's Nek", "Quthing", "Thaba-Tseka");

    // ------------------------------------------------------------------ statuses
    private static final Map<String, String> STATUS = new LinkedHashMap<>();
    private static final Map<String, String> HELP = new HashMap<>();
    static {
        STATUS.put("RECEIVED", "Received");
        STATUS.put("PROCESSING", "Processing");
        STATUS.put("NEEDS_INFO", "Needs info");
        STATUS.put("APPROVED", "Approved");
        STATUS.put("COMPLETED", "Completed");
        STATUS.put("REJECTED", "Rejected");
        HELP.put("RECEIVED", "We have your application. Home Affairs will check it against the national register.");
        HELP.put("PROCESSING", "Home Affairs is working on your application.");
        HELP.put("NEEDS_INFO", "Home Affairs needs more from you. Read the note below and visit the nearest Home Affairs office.");
        HELP.put("APPROVED", "Your application is approved. Visit the Home Affairs office to collect it. Bring this reference and your ID if you have one.");
        HELP.put("COMPLETED", "This application is complete.");
        HELP.put("REJECTED", "This application was not accepted. Read the note below or visit the nearest Home Affairs office.");
    }

    // ------------------------------------------------------------------ form definitions
    private static final class Fld {
        final String name, label, type, hint, def;
        final boolean req;
        final List<String> opts;
        Fld(String name, String label, String type, boolean req, String hint, String def, List<String> opts) {
            this.name = name; this.label = label; this.type = type; this.req = req; this.hint = hint; this.def = def; this.opts = opts;
        }
    }

    private static Fld f(String name, String label, String type, boolean req) { return new Fld(name, label, type, req, "", "", List.of()); }
    private static Fld f(String name, String label, String type, boolean req, String hint) { return new Fld(name, label, type, req, hint, "", List.of()); }
    private static Fld fd(String name, String label, String type, boolean req, String def) { return new Fld(name, label, type, req, "", def, List.of()); }
    private static Fld sel(String name, String label, boolean req, String... opts) { return new Fld(name, label, "select", req, "", "", List.of(opts)); }
    private static Fld selList(String name, String label, boolean req, List<String> opts) { return new Fld(name, label, "select", req, "", "", opts); }

    private static final class Sec {
        final String title;
        final List<Fld> fields;
        Sec(String title, Fld... fields) { this.title = title; this.fields = List.of(fields); }
    }

    /** An optional or required file upload. reqFor: null = optional, "*" = always, otherwise the service it is required for. */
    private static final class Slot {
        final String name, label, hint, reqFor;
        Slot(String name, String label, String hint, String reqFor) { this.name = name; this.label = label; this.hint = hint; this.reqFor = reqFor; }
    }

    private static final class Form {
        final String key, path, title, intro, service, prefix, idKey, phoneKey;
        final boolean photo;
        final List<String> nameKeys, idNameKeys;
        final List<Sec> secs;
        final List<Slot> slots;
        Form(String key, String path, String title, String intro, String service, String prefix, boolean photo,
             List<String> nameKeys, List<String> idNameKeys, String idKey, String phoneKey, List<Sec> secs, List<Slot> slots) {
            this.key = key; this.path = path; this.title = title; this.intro = intro; this.service = service; this.prefix = prefix; this.photo = photo;
            this.nameKeys = nameKeys; this.idNameKeys = idNameKeys; this.idKey = idKey; this.phoneKey = phoneKey; this.secs = secs; this.slots = slots;
        }
    }

    private static final Map<String, Form> FORMS = new LinkedHashMap<>();
    static {
        List<String> marital = List.of("Single", "Married", "Widowed", "Divorced", "Separated");

        FORMS.put("id", new Form("id", "/public/id", "National ID Card Application",
                "For citizens aged 16 and older. An ID card is valid for ten years.", null, "NID", true,
                List.of("first_names", "surname"), List.of("first_names", "surname"), "existing_id", "phone",
                List.of(
                        new Sec("Type of application",
                                sel("service", "What do you need?", true, "First-time ID Card", "ID Replacement (lost or damaged)", "ID Renewal"),
                                f("existing_id", "Existing ID number", "idnum", false, "Needed for a replacement or renewal. Leave empty for your first card."),
                                f("reason", "Reason for replacement", "text", false, "For example: lost, stolen or damaged"),
                                f("police_no", "Police report number", "text", false, "If the card was lost or stolen")),
                        new Sec("Your details",
                                f("surname", "Surname", "text", true),
                                f("first_names", "First names", "text", true),
                                sel("sex", "Sex", true, "Male", "Female"),
                                f("dob", "Date of birth", "date", true, "You must be 16 or older."),
                                f("birth_place", "Place of birth (village or town)", "text", true),
                                selList("birth_district", "District of birth", true, DISTRICTS),
                                fd("nationality", "Nationality", "text", true, "Mosotho"),
                                sel("marital", "Marital status", true, marital.toArray(new String[0])),
                                f("occupation", "Occupation", "text", false)),
                        new Sec("Your parents",
                                f("mother", "Mother's full name", "text", true, "Include her maiden surname if you know it."),
                                f("father", "Father's full name", "text", false)),
                        new Sec("Where you live and how to reach you",
                                f("village", "Village or town", "text", true),
                                f("chief_area", "Chief's area (chieftainship)", "text", false),
                                selList("district", "District", true, DISTRICTS),
                                f("postal", "Postal address", "area", false),
                                f("phone", "Phone number", "tel", true),
                                f("email", "Email", "email", false))),
                List.of(
                        new Slot("d_birth", "Birth certificate", "Required for a first-time application. JPG, PNG or PDF.", "First-time ID Card"),
                        new Slot("d_chief", "Letter from your chief (Informant)", "Required for a first-time application. JPG, PNG or PDF.", "First-time ID Card"),
                        new Slot("d_parent", "Parent's or guardian's ID copy", "Optional. JPG, PNG or PDF.", null))));

        FORMS.put("birth", new Form("birth", "/public/birth", "Birth Certificate Application",
                "To apply for a birth certificate for a child. The person applying (the informant) is usually a parent.", "Birth Registration", "BRG", false,
                List.of("child_first", "child_surname"), List.of("inf_name"), "inf_id", "inf_phone",
                List.of(
                        new Sec("Who is the certificate for?",
                                sel("for_whom", "Whose birth certificate is this?", true, "My own birth certificate", "My child's birth certificate")),
                        new Sec("The person who was born (you, or your child)",
                                f("child_first", "First names", "text", true),
                                f("child_surname", "Surname", "text", true),
                                sel("child_sex", "Sex", true, "Male", "Female"),
                                f("child_dob", "Date of birth", "date", true),
                                f("child_time", "Time of birth", "time", false, "If you know it"),
                                sel("place_type", "Where was the child born?", true, "Hospital", "Clinic", "Home", "Other"),
                                f("place_name", "Name of the hospital, clinic or village", "text", true),
                                selList("birth_district", "District of birth", true, DISTRICTS)),
                        new Sec("The mother",
                                f("mother_name", "Full name", "text", true),
                                f("mother_maiden", "Maiden surname", "text", false),
                                f("mother_id", "ID number", "idnum", false),
                                f("mother_dob", "Date of birth", "date", false),
                                fd("mother_nat", "Nationality", "text", false, "Mosotho"),
                                sel("mother_marital", "Marital status", false, marital.toArray(new String[0]))),
                        new Sec("The father",
                                f("father_name", "Full name", "text", false),
                                f("father_id", "ID number", "idnum", false),
                                fd("father_nat", "Nationality", "text", false, "Mosotho")),
                        new Sec("The person applying (informant)",
                                f("inf_name", "Your full name", "text", true),
                                sel("inf_rel", "Your relationship to the person who was born", true, "Myself", "Mother", "Father", "Grandparent", "Guardian", "Other"),
                                f("inf_id", "Your ID number", "idnum", false, "Needed when you apply for your child. Leave empty if this is your own first birth certificate."),
                                f("inf_phone", "Your phone number", "tel", true),
                                f("inf_email", "Your email", "email", false),
                                f("inf_village", "Your village or town", "text", true),
                                selList("inf_district", "Your district", true, DISTRICTS))),
                List.of(
                        new Slot("d_notif", "Birth notification from the hospital or clinic", "Optional. JPG, PNG or PDF.", null),
                        new Slot("d_chief", "Letter from the chief (Informant)", "Optional. JPG, PNG or PDF.", null),
                        new Slot("d_parent", "Parent's ID copy", "Optional. JPG, PNG or PDF.", null))));

        FORMS.put("birthcopy", new Form("birthcopy", "/public/birth-copy", "Birth Certificate Copy",
                "To get a certified copy of a birth certificate that was already issued.", "Birth Certificate Copy", "BCC", false,
                List.of("c_first", "c_surname"), List.of("a_name"), "a_id", "a_phone",
                List.of(
                        new Sec("Details on the birth certificate",
                                f("c_first", "First names", "text", true),
                                f("c_surname", "Surname", "text", true),
                                f("c_dob", "Date of birth", "date", true),
                                f("c_place", "Place of birth", "text", true),
                                selList("c_district", "District of birth", true, DISTRICTS),
                                f("c_mother", "Mother's full name", "text", true),
                                f("c_father", "Father's full name", "text", false),
                                f("c_regno", "Birth registration number", "text", false, "If you know it")),
                        new Sec("The person applying",
                                f("a_name", "Your full name", "text", true),
                                f("a_id", "Your ID number", "idnum", true),
                                sel("a_rel", "Your relationship to the person on the certificate", true, "Myself", "Parent", "Guardian", "Other"),
                                f("a_phone", "Your phone number", "tel", true),
                                f("a_email", "Your email", "email", false),
                                sel("a_reason", "Why do you need a copy?", true, "Lost", "Damaged", "Needed for a passport or ID", "Needed for school or work", "Other"))),
                List.of(new Slot("d_aid", "Copy of your ID", "Optional. JPG, PNG or PDF.", null))));
    }

    // ------------------------------------------------------------------ database
    private static final class PApp {
        int id, fee;
        String ref, code, service, form, name, idNumber, idName, phone, status, note;
        LocalDateTime created;
    }

    private static final class PFile { int id; String kind, filename, stored, mime; }

    private static final Db.Mapper<PApp> PA = r -> {
        PApp a = new PApp();
        a.id = r.getInt("id"); a.ref = r.getString("ref"); a.code = r.getString("code"); a.service = r.getString("service"); a.form = r.getString("form");
        a.name = r.getString("name"); a.idNumber = r.getString("id_number") == null ? "" : r.getString("id_number");
        a.idName = r.getString("id_name") == null ? "" : r.getString("id_name"); a.phone = r.getString("phone");
        a.status = r.getString("status"); a.note = r.getString("note") == null ? "" : r.getString("note");
        a.fee = r.getInt("fee"); a.created = LocalDateTime.parse(r.getString("created"));
        return a;
    };

    private static final Db.Mapper<PFile> PF = r -> {
        PFile p = new PFile();
        p.id = r.getInt("id"); p.kind = r.getString("kind"); p.filename = r.getString("filename"); p.stored = r.getString("stored"); p.mime = r.getString("mime");
        return p;
    };

    /** Creates the tables. Call once at start-up, after Store.init. */
    static void init() {
        Db.exec("create table if not exists public_apps(id integer primary key autoincrement,ref text,code text,service text,form text,name text,id_number text,id_name text,phone text,status text,note text,fee integer default 0,created text)");
        Db.exec("create table if not exists public_answers(app_id integer,ord integer,label text,value text)");
        Db.exec("create table if not exists public_files(id integer primary key autoincrement,app_id integer,kind text,filename text,stored text,mime text)");
    }

    private static PApp byId(String id) {
        try { return Db.one("select * from public_apps where id=?", PA, Integer.parseInt(id)); } catch (NumberFormatException e) { return null; }
    }

    private static String normCode(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toUpperCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) sb.append(c);
        return sb.toString();
    }

    private static String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private static String showCode(String c) { return c.length() == 10 ? c.substring(0, 5) + "-" + c.substring(5) : c; }

    // ------------------------------------------------------------------ page shell (same look as the welcome screens)
    private static final String CSS = """
        *{box-sizing:border-box}
        [hidden]{display:none!important}
        html,body{margin:0}
        body{background:#dfe6ee;font-family:'Segoe UI',Arial,Helvetica,sans-serif;color:#475569}
        a{text-decoration:none;color:inherit}
        .phone{max-width:480px;margin:0 auto;min-height:100vh;background:#eef7ff;padding:20px 22px 30px}
        .top a{color:#00209f;font-weight:800;font-size:14px;display:inline-block;padding:4px 0}
        h2.t{color:#00209f;font-size:20px;margin:10px 0 4px}
        p.in{font-size:13.5px;margin:0 0 16px;line-height:1.4}
        fieldset{border:1px solid #c9d6f0;background:#fff;border-radius:12px;padding:4px 14px 4px;margin:0 0 16px}
        legend{color:#00209f;font-weight:800;font-size:13px;padding:0 6px}
        label.f{display:flex;flex-direction:column;gap:5px;font-size:12px;font-weight:700;color:#33466e;margin:10px 0 12px}
        label.f i{color:#c0262d;font-style:normal}
        input,select,textarea{width:100%;border:1.5px solid #c9d6f0;border-radius:10px;padding:11px;font:15px 'Segoe UI',Arial,sans-serif;background:#fff;color:#0a2a8f}
        input[type=file]{padding:9px;font-size:13px}
        small{color:#5f6f8c;font-size:11.5px;font-weight:400;line-height:1.35}
        .btn{display:block;width:100%;border:0;border-radius:8px;background:#0b5ed7;color:#fff;font:700 15px 'Segoe UI',Arial,sans-serif;padding:15px;text-align:center;cursor:pointer;margin-top:6px}
        .btn:hover{background:#0a53bd}
        .sm{border:1.5px solid #0b5ed7;background:#fff;color:#0b5ed7;border-radius:18px;padding:8px 14px;font:700 13px 'Segoe UI',Arial,sans-serif;cursor:pointer;display:inline-block}
        .sm:hover{background:#eef4ff}
        .err,.ok{border-radius:12px;padding:10px 14px;font-size:13px;font-weight:700;margin:0 0 14px}
        .ok{background:#dbe6fb;color:#0a2a8f}
        .err{background:#fff;color:#1a4fb4;border:2px solid #1a4fb4}
        .err ul{margin:6px 0 0;padding-left:18px;font-weight:600}
        .hp{position:absolute;left:-9999px;top:-9999px;height:0;overflow:hidden}
        .chk{display:flex;gap:10px;align-items:flex-start;font-size:13px;margin:6px 0 16px;color:#33466e}
        .chk input{width:auto;margin-top:3px}
        .ph{width:150px;height:190px;border:2px dashed #9db4e6;border-radius:12px;background:#f6f9ff;display:flex;align-items:center;justify-content:center;overflow:hidden;margin:8px 0;font-size:12px;text-align:center;color:#5f6f8c}
        .ph img{width:100%;height:100%;object-fit:cover}
        video{width:100%;max-width:320px;border-radius:12px;background:#000;transform:scaleX(-1);margin:8px 0;display:block}
        .row{display:flex;gap:8px;flex-wrap:wrap;margin:4px 0}
        .tile{display:block;background:#fff;border:1px solid #6b7280;border-radius:12px;padding:16px;margin-bottom:12px}
        .tile:hover{background:#f3f8ff}
        .tile b{display:block;color:#00209f;font-size:15px}
        .tile small{display:block;margin-top:4px;font-size:12px}
        .card{background:#fff;border:1px solid #c9d6f0;border-radius:12px;padding:14px;margin-bottom:14px}
        .big{font-size:24px;font-weight:800;color:#00209f;letter-spacing:.05em;margin:2px 0 10px}
        .pill{display:inline-block;border-radius:20px;padding:4px 12px;background:#0b5ed7;color:#fff;font-size:12px;font-weight:700}
        .foot{text-align:center;color:#00209f;font-weight:700;font-size:11.5px;margin:22px 0 2px}
        @media print{.noprint{display:none!important}body{background:#fff}.phone{background:#fff}}
        """;

    private static final String CAMERA_JS = """
        (function(){
          var inp=document.getElementById('photo'); if(!inp) return;
          var pv=document.getElementById('pv'), pn=document.getElementById('pn'), vid=document.getElementById('vid');
          var cam=document.getElementById('cam'), snap=document.getElementById('snap'), msg=document.getElementById('cmsg');
          var stream=null;
          function show(file){
            if(!file){ pv.hidden=true; pn.hidden=false; return; }
            pv.src=URL.createObjectURL(file); pv.hidden=false; pn.hidden=true;
          }
          function stop(){
            if(stream){ stream.getTracks().forEach(function(t){ t.stop(); }); stream=null; }
            vid.hidden=true; snap.hidden=true; cam.hidden=false;
          }
          inp.addEventListener('change',function(){
            stop(); msg.textContent='';
            var f=inp.files[0];
            if(f && f.size>5*1024*1024){ msg.textContent='That photo is bigger than 5 MB.'; inp.value=''; show(null); return; }
            show(f);
          });
          cam.addEventListener('click',function(){
            msg.textContent='';
            if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){
              msg.textContent='The camera is not available in this browser. Use Upload instead. On a phone, Upload also offers the camera.'; return;
            }
            navigator.mediaDevices.getUserMedia({video:{facingMode:'user',width:{ideal:1280},height:{ideal:960}},audio:false}).then(function(s){
              stream=s; vid.srcObject=s; vid.hidden=false; snap.hidden=false; cam.hidden=true; pv.hidden=true; pn.hidden=true;
            }).catch(function(){ msg.textContent='Could not open the camera. Allow camera access, or use Upload.'; });
          });
          snap.addEventListener('click',function(){
            var w=vid.videoWidth||640, h=vid.videoHeight||480, k=Math.min(1,1024/Math.max(w,h));
            var c=document.createElement('canvas'); c.width=Math.round(w*k); c.height=Math.round(h*k);
            c.getContext('2d').drawImage(vid,0,0,c.width,c.height);
            c.toBlob(function(b){
              if(!b){ msg.textContent='Could not take the photo. Try again.'; return; }
              var file=new File([b],'camera.jpg',{type:'image/jpeg'});
              var dt=new DataTransfer(); dt.items.add(file); inp.files=dt.files;
              stop(); show(file);
            },'image/jpeg',0.9);
          });
          var form=document.getElementById('appform');
          if(form) form.addEventListener('submit',function(e){
            if(!inp.files||!inp.files.length){ e.preventDefault(); msg.textContent='Please add your photo first.'; inp.scrollIntoView({block:'center'}); }
          });
        })();
        """;

    private static Resp shell(String title, String body, boolean script) {
        return Resp.html("<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + Html.esc(title) + " - IGSS Lesotho</title><style>" + CSS + "</style></head>"
                + "<body><div class='phone'>" + body
                + "<div class='foot'>Secure &middot; Simple &middot; Connected</div></div>"
                + (script ? "<script>" + CAMERA_JS + "</script>" : "") + "</body></html>");
    }

    // ------------------------------------------------------------------ validation helpers
    private static String clean(String s, boolean multiline) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\n' && multiline) sb.append(c);
            else if (c < 32 || c == 127) sb.append(' ');
            else sb.append(c);
        }
        return sb.toString().trim();
    }

    private static LocalDate date(String v) {
        try { return LocalDate.parse(v); } catch (DateTimeParseException e) { return null; }
    }

    private static String digits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) if (c >= '0' && c <= '9') sb.append(c);
        return sb.toString();
    }

    private static String fieldError(Fld f, String v) {
        if (v.isEmpty()) return f.req ? f.label + " is required." : null;
        switch (f.type) {
            case "select": return f.opts.contains(v) ? null : f.label + ": choose one of the options.";
            case "date": {
                LocalDate d = date(v);
                return d == null || d.isAfter(LocalDate.now()) || d.getYear() < 1900 ? f.label + " must be a real date in the past." : null;
            }
            case "time":
                try { LocalTime.parse(v); return null; } catch (DateTimeParseException e) { return f.label + " is not a valid time."; }
            case "tel": {
                int n = digits(v).length();
                return n >= 7 && n <= 15 && v.matches("[0-9 +()-]+") ? null : f.label + " is not a valid phone number.";
            }
            case "idnum":
                return v.replace(" ", "").replace("-", "").matches("[0-9]{11}") ? null : f.label + " is wrong. A Lesotho ID number has exactly 11 digits (for example 04123457678).";
            case "email":
                return v.length() <= 120 && v.matches("[^@ ]+@[^@ ]+[.][^@ ]+") ? null : f.label + " is not a valid email address.";
            case "area": return v.length() <= 400 ? null : f.label + " is too long.";
            default: return v.length() <= 120 ? null : f.label + " is too long.";
        }
    }

    /** What the file really is, judged from its first bytes (never from the name the browser sent). */
    private static String sniff(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length > 5 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return "application/pdf";
        return null;
    }

    private static String fileError(Part p, boolean allowPdf) {
        if (p.data.length > MAX_FILE) return "must be 5 MB or smaller.";
        String m = sniff(p.data);
        if (m == null || (!allowPdf && m.equals("application/pdf"))) return allowPdf ? "must be a JPG, PNG or PDF file." : "must be a JPG or PNG image.";
        return null;
    }

    private static String safeName(String n) {
        if (n == null) return "file";
        int i = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (i >= 0) n = n.substring(i + 1);
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-' || c == ' ') sb.append(c);
        String s = sb.toString().trim();
        if (s.length() > 60) s = s.substring(0, 60);
        return s.isEmpty() ? "file" : s;
    }

    private static String join(Map<String, String> vals, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            String v = vals.getOrDefault(k, "");
            if (!v.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(v);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ public pages
    static Resp home(Req r) {
        return shell("Apply without an account",
                "<div class='top'><a href='/'>&lsaquo; Back</a></div><h2 class='t'>Apply without an account</h2>"
                        + "<p class='in'>You do not need to log in. Fill in the form, add your documents, and you get a reference number to follow your application. For an ID card, first get your birth certificate, create your account, then apply under Home Affairs services.</p>"
                        + "<a class='tile' href='/public/birth'><b>Birth certificate</b><small>For yourself or for your child. You get an ID number when Home Affairs approves it.</small></a>"
                        + "<a class='tile' href='/public/track'><b>Check my application</b><small>Use your reference number and tracking code.</small></a>", false);
    }

    private static String input(Fld f, String value) {
        String v = value.isEmpty() ? f.def : value;
        String ev = Html.esc(v), n = f.name, rq = f.req ? " required" : "";
        String ctl;
        switch (f.type) {
            case "select": {
                StringBuilder o = new StringBuilder("<select name='" + n + "'" + rq + "><option value=''>Choose...</option>");
                for (String s : f.opts) o.append("<option value='").append(Html.esc(s)).append("'").append(s.equals(v) ? " selected" : "").append(">").append(Html.esc(s)).append("</option>");
                ctl = o.append("</select>").toString();
                break;
            }
            case "area": ctl = "<textarea name='" + n + "' rows='2' maxlength='400'>" + ev + "</textarea>"; break;
            case "date": ctl = "<input type='date' name='" + n + "' value='" + ev + "' min='1900-01-01' max='" + LocalDate.now() + "'" + rq + ">"; break;
            case "time": ctl = "<input type='time' name='" + n + "' value='" + ev + "'" + rq + ">"; break;
            case "tel": ctl = "<input type='tel' inputmode='tel' name='" + n + "' value='" + ev + "' maxlength='20' autocomplete='off'" + rq + ">"; break;
            case "email": ctl = "<input type='email' name='" + n + "' value='" + ev + "' maxlength='120' autocomplete='off'" + rq + ">"; break;
            case "idnum": ctl = "<input type='text' inputmode='numeric' name='" + n + "' value='" + ev + "' maxlength='13' autocomplete='off'" + rq + ">"; break;
            default: ctl = "<input type='text' name='" + n + "' value='" + ev + "' maxlength='120' autocomplete='off'" + rq + ">";
        }
        return "<label class='f'><span>" + Html.esc(f.label) + (f.req ? " <i>*</i>" : "") + "</span>" + ctl
                + (f.hint.isEmpty() ? "" : "<small>" + Html.esc(f.hint) + "</small>") + "</label>";
    }

    private static String photoBlock() {
        return "<fieldset><legend>Your photo <i style='color:#c0262d;font-style:normal'>*</i></legend>"
                + "<small>Face the camera in good light, with a plain light background. No hat or sunglasses. JPG or PNG, up to 5 MB.</small>"
                + "<div class='ph'><img id='pv' alt='Your photo' hidden><span id='pn'>No photo yet</span></div>"
                + "<video id='vid' autoplay playsinline muted hidden></video>"
                + "<div class='row'><label class='sm'>Upload a photo<input type='file' name='photo' id='photo' accept='image/jpeg,image/png' hidden></label>"
                + "<button type='button' class='sm' id='cam'>Take a photo</button><button type='button' class='sm' id='snap' hidden>Capture</button></div>"
                + "<small id='cmsg' style='display:block;color:#1a4fb4;font-weight:700;margin:4px 0 8px'></small></fieldset>";
    }

    private static Resp renderForm(Form fm, Req r, List<String> errors) {
        StringBuilder sb = new StringBuilder("<div class='top'><a href='/public'>&lsaquo; Back</a></div><h2 class='t'>" + Html.esc(fm.title) + "</h2>"
                + "<p class='in'>" + Html.esc(fm.intro) + " Fields marked <b style='color:#c0262d'>*</b> are required.</p>");
        if (!errors.isEmpty()) {
            sb.append("<div class='err'>Please fix the following:<ul>");
            for (String e : errors) sb.append("<li>").append(Html.esc(e)).append("</li>");
            sb.append("</ul>").append(fm.photo || !fm.slots.isEmpty() ? "<div style='margin-top:6px'>Choose your photo and documents again.</div>" : "").append("</div>");
        }
        sb.append("<form id='appform' method='post' action='").append(fm.path).append("' enctype='multipart/form-data' autocomplete='off'>");
        for (Sec s : fm.secs) {
            sb.append("<fieldset><legend>").append(Html.esc(s.title)).append("</legend>");
            for (Fld f : s.fields) sb.append(input(f, r.p.containsKey(f.name) ? r.get(f.name) : ""));
            sb.append("</fieldset>");
        }
        if (fm.photo) sb.append(photoBlock());
        if (!fm.slots.isEmpty()) {
            sb.append("<fieldset><legend>Documents</legend>");
            for (Slot s : fm.slots)
                sb.append("<label class='f'>").append(Html.esc(s.label)).append("<input type='file' name='").append(s.name)
                        .append("' accept='image/jpeg,image/png,application/pdf'><small>").append(Html.esc(s.hint)).append("</small></label>");
            sb.append("</fieldset>");
        }
        sb.append("<label class='chk'><input type='checkbox' name='declare' value='yes'><span>I declare that the information I have given is true and complete. "
                + "I understand that false information can lead to my application being rejected.</span></label>");
        sb.append("<div class='hp' aria-hidden='true'><label>Leave this empty<input name='website' tabindex='-1' autocomplete='off'></label></div>");
        sb.append("<button class='btn'>Submit application</button></form>");
        Resp out = shell(fm.title, sb.toString(), fm.photo);
        if (!errors.isEmpty()) out.code = 400;
        return out;
    }

    /** GET shows the form, POST checks it and saves the application. */
    static Resp form(Req r, String key) {
        Form fm = FORMS.get(key);
        if (fm == null) return Resp.notFound();
        if (!r.post()) {
            if (fm.key.equals("id")) {
                String t = r.get("type");
                if (t.equals("first")) r.p.put("service", "First-time ID Card");
                else if (t.equals("replace")) r.p.put("service", "ID Replacement (lost or damaged)");
                else if (t.equals("renew")) r.p.put("service", "ID Renewal");
            }
            return renderForm(fm, r, List.of());
        }
        return submit(r, fm);
    }

    private static Resp submit(Req r, Form fm) {
        if (!r.get("website").isEmpty()) return Resp.error(400, "We could not accept this form.");

        Map<String, String> vals = new LinkedHashMap<>();
        List<String> errs = new ArrayList<>();
        for (Sec s : fm.secs)
            for (Fld f : s.fields) {
                String v = clean(r.get(f.name), f.type.equals("area"));
                r.p.put(f.name, v);
                vals.put(f.name, v);
                String e = fieldError(f, v);
                if (e != null) errs.add(e);
            }
        String service = fm.service != null ? fm.service : vals.getOrDefault("service", "");
        Policy.Service sv = service.isEmpty() ? null : Policy.publicService(service);

        if (fm.key.equals("id") && sv != null) {
            boolean first = service.equals("First-time ID Card");
            if (!first && vals.get("existing_id").isEmpty()) errs.add("Existing ID number is required for a replacement or renewal.");
            LocalDate dob = date(vals.get("dob"));
            if (dob != null && Period.between(dob, LocalDate.now()).getYears() < 16) errs.add("A national ID card is for people aged 16 or older.");
        }

        if (fm.key.equals("birth")) {
            boolean own = vals.get("for_whom").startsWith("My own");
            if (own && !vals.get("inf_rel").equals("Myself")) errs.add("This is your own birth certificate. Choose Myself as your relationship.");
            if (!own && vals.get("inf_rel").equals("Myself")) errs.add("This is for your child. Choose your relationship to the child (for example Mother or Father).");
            if (!own && vals.get("inf_id").isEmpty()) errs.add("Your ID number is required when you apply for your child.");
        }

        Map<String, Part> up = new LinkedHashMap<>();
        if (fm.photo) {
            Part p = r.files.get("photo");
            if (p == null) errs.add("Please add your photo. You can upload one or take one with the camera.");
            else {
                String why = fileError(p, false);
                if (why != null) errs.add("Photo " + why); else up.put("photo", p);
            }
        }
        for (Slot s : fm.slots) {
            Part p = r.files.get(s.name);
            boolean need = s.reqFor != null && (s.reqFor.equals("*") || s.reqFor.equals(service));
            if (p == null) { if (need) errs.add(s.label + " is required."); }
            else {
                String why = fileError(p, true);
                if (why != null) errs.add(s.label + " " + why); else up.put(s.name, p);
            }
        }
        if (!r.get("declare").equals("yes")) errs.add("Please tick the declaration at the bottom.");
        if (!errs.isEmpty()) return renderForm(fm, r, errs);

        // save the files first, so a disk problem leaves no half-saved application
        List<String[]> saved = new ArrayList<>(); // kind, original name, stored name, mime
        try {
            Files.createDirectories(Store.UPLOADS);
            for (Map.Entry<String, Part> e : up.entrySet()) {
                String mime = sniff(e.getValue().data);
                String ext = mime.equals("image/png") ? ".png" : mime.equals("application/pdf") ? ".pdf" : ".jpg";
                String stored = "pub-" + UUID.randomUUID() + ext;
                Files.write(Store.UPLOADS.resolve(stored), e.getValue().data);
                String kind = e.getKey().equals("photo") ? "Photo" : slotLabel(fm, e.getKey());
                saved.add(new String[]{kind, safeName(e.getValue().filename), stored, mime});
            }
        } catch (IOException ex) {
            for (String[] s : saved) try { Files.deleteIfExists(Store.UPLOADS.resolve(s[2])); } catch (IOException ignored) { }
            errs.add("We could not save your files. Please try again.");
            return renderForm(fm, r, errs);
        }

        String code = newCode();
        String idNumber = fm.idKey == null ? "" : vals.getOrDefault(fm.idKey, "").replace(" ", "").replace("-", "");
        int id = Db.insert("insert into public_apps(ref,code,service,form,name,id_number,id_name,phone,status,note,fee,created) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                "", code, service, fm.key, join(vals, fm.nameKeys), idNumber, join(vals, fm.idNameKeys), vals.getOrDefault(fm.phoneKey, ""),
                "RECEIVED", "", sv == null ? 0 : sv.fee, LocalDateTime.now());
        String ref = fm.prefix + "-" + LocalDate.now().getYear() + "-" + String.format("%04d", id);
        Db.exec("update public_apps set ref=? where id=?", ref, id);
        int ord = 0;
        for (Sec s : fm.secs)
            for (Fld f : s.fields) {
                String v = vals.get(f.name);
                if (!v.isEmpty()) Db.exec("insert into public_answers(app_id,ord,label,value) values(?,?,?,?)", id, ord++, f.label, v);
            }
        for (String[] s : saved) Db.exec("insert into public_files(app_id,kind,filename,stored,mime) values(?,?,?,?,?)", id, s[0], s[1], s[2], s[3]);
        return Resp.redirect("/public/done?c=" + code);
    }

    private static String slotLabel(Form fm, String name) {
        for (Slot s : fm.slots) if (s.name.equals(name)) return s.label;
        return name;
    }

    static Resp done(Req r) {
        PApp a = Db.one("select * from public_apps where code=?", PA, normCode(r.get("c")));
        if (a == null) return Resp.redirect("/public");
        return shell("Application received",
                "<h2 class='t'>Application received</h2>"
                        + "<p class='in'>Keep these two items. You need both to check your progress. Nobody else can see your application with them.</p>"
                        + "<div class='card'><small>Reference number</small><div class='big'>" + Html.esc(a.ref) + "</div><small>Tracking code</small><div class='big'>" + Html.esc(showCode(a.code)) + "</div></div>"
                        + "<div class='card'><b style='color:#00209f'>" + Html.esc(a.service) + "</b><br><small>"
                        + (a.fee > 0 ? "Fee: M " + a.fee + ". You pay this at the Home Affairs office when you collect." : "There is no fee for this service.")
                        + "</small></div>"
                        + "<p class='in'>Home Affairs will check your details against the national register. Use <b>Check my application</b> to see the result.</p>"
                        + "<div class='noprint'><button class='btn' onclick='window.print()'>Print this page</button>"
                        + "<a class='btn' style='background:#fff;color:#0b5ed7;border:1.5px solid #0b5ed7' href='/public/track'>Check my application</a>"
                        + "<a class='btn' style='background:#fff;color:#0b5ed7;border:1.5px solid #0b5ed7' href='/'>Home</a></div>", false);
    }

    static Resp track(Req r) {
        StringBuilder sb = new StringBuilder("<div class='top'><a href='/public'>&lsaquo; Back</a></div><h2 class='t'>Check my application</h2>"
                + "<p class='in'>Enter the reference number and tracking code you were given when you applied.</p>");
        if (r.post()) {
            PApp a = Db.one("select * from public_apps where replace(replace(upper(ref),' ',''),'-','')=? and replace(replace(upper(code),' ',''),'-','')=?", PA,
                    normCode(r.get("ref")), normCode(r.get("code")));
            if (a == null) sb.append("<div class='err'>No application was found with that reference and tracking code. Check both and try again.</div>");
            else {
                String label = STATUS.getOrDefault(a.status, a.status);
                sb.append("<div class='card'><small>").append(Html.esc(a.ref)).append(" &middot; ").append(Html.esc(a.service)).append("</small><br>")
                        .append("<span class='pill'>").append(Html.esc(label)).append("</span><p style='margin:10px 0 0;font-size:13.5px'>")
                        .append(Html.esc(HELP.getOrDefault(a.status, ""))).append("</p>");
                if (!a.note.isEmpty()) sb.append("<p style='margin:10px 0 0;font-size:13.5px'><b>Note from Home Affairs:</b> ").append(Html.esc(a.note)).append("</p>");
                sb.append("<small style='display:block;margin-top:10px'>Submitted ").append(WHEN.format(a.created)).append("</small></div>");
            }
        }
        sb.append("<form method='post' action='/public/track' autocomplete='off'>"
                + "<label class='f'>Reference number<input name='ref' maxlength='30' autocomplete='off' placeholder='NID-2026-0001'></label>"
                + "<label class='f'>Tracking code<input name='code' maxlength='20' autocomplete='off' placeholder='XXXXX-XXXXX'></label>"
                + "<button class='btn'>Check</button></form>");
        return shell("Check my application", sb.toString(), false);
    }

    // ------------------------------------------------------------------ Home Affairs staff pages
    private static Resp gate(Req r) {
        Official o = r.staff();
        if (o == null) return Resp.redirect("/staff/departments");
        if (o.dept != Dept.HA) return Resp.to("/staff", "err:Home Affairs staff only.");
        return null;
    }

    private static Set<String> tokens(String s) {
        Set<String> t = new TreeSet<>();
        for (String p : s.toLowerCase(Locale.ROOT).split("\\s+")) if (!p.isEmpty()) t.add(p);
        return t;
    }

    /** Walk-in applications still waiting for Home Affairs (shown on the dashboard and in Applications). */
    static int openCount() {
        return Db.count("select count(*) from public_apps where status in ('RECEIVED','PROCESSING','NEEDS_INFO')");
    }

    /** Rows for the open walk-in applications, newest first. Empty text when there are none. */
    static String openRows(int limit) {
        StringBuilder sb = new StringBuilder();
        for (PApp a : Db.list("select * from public_apps where status in ('RECEIVED','PROCESSING','NEEDS_INFO') order by id desc limit " + limit, PA))
            sb.append(Html.row(a.ref + " · " + a.service, a.name + " · " + WHEN.format(a.created), Html.pill(STATUS.getOrDefault(a.status, a.status)), "/staff/public/view?id=" + a.id));
        return sb.toString();
    }

    static Resp staffList(Req r) {
        Resp bad = gate(r);
        if (bad != null) return bad;
        List<PApp> l = Db.list("select * from public_apps order by id desc limit 200", PA);
        int open = 0;
        for (PApp a : l) if (a.status.equals("RECEIVED") || a.status.equals("PROCESSING") || a.status.equals("NEEDS_INFO")) open++;
        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")) + Html.intro(open + " open of " + l.size()
                + " applications made without an account. Check each one against the register."));
        for (PApp a : l)
            sb.append(Html.row(a.ref + " · " + a.service, a.name + " · " + WHEN.format(a.created), Html.pill(STATUS.getOrDefault(a.status, a.status)), "/staff/public/view?id=" + a.id));
        if (l.isEmpty()) sb.append(Html.mu("No applications yet."));
        return Resp.html(Html.page("Walk-in Applications", "Dashboard", "/staff", sb.toString()));
    }

    private static String[] prepend(List<String> offices) {
        List<String> l = new ArrayList<>();
        l.add("|Office nearest to the applicant");
        l.addAll(offices);
        return l.toArray(new String[0]);
    }

    static Resp staffView(Req r) {
        Resp bad = gate(r);
        if (bad != null) return bad;
        Official o = r.staff();
        PApp a = byId(r.get("id"));
        if (a == null) return Resp.to("/staff/public", "err:Application not found.");

        StringBuilder sb = new StringBuilder(Html.banner(r.get("m")));
        sb.append(Html.card("<b>" + Html.esc(a.service) + "</b><span class='mu'>" + Html.esc(a.ref) + " · " + WHEN.format(a.created)
                + (a.fee > 0 ? " · fee M " + a.fee : "") + "</span>" + Html.pill(STATUS.getOrDefault(a.status, a.status))));

        // check the ID number given in the form against the national register
        if (!a.idNumber.isEmpty()) {
            Citizen c = Store.citizen(a.idNumber);
            if (c == null) sb.append("<div class='err'>! ID number " + Html.esc(a.idNumber) + " is not in the national register.</div>");
            else {
                boolean same = !a.idName.isEmpty() && tokens(a.idName).equals(tokens(c.name));
                sb.append(same ? "<div class='ok'>&#10003; ID number matches the register: " + Html.esc(c.name) + (c.alive ? "" : " (recorded as deceased)") + ".</div>"
                        : "<div class='err'>! ID number belongs to " + Html.esc(c.name) + " in the register, but the form says " + Html.esc(a.idName) + ".</div>");
                Store.logAccess(c.id, o.dept, o.name, "Name, ID number", "Walk-in application " + a.ref);
            }
        }

        sb.append(Html.label("WHAT THE APPLICANT WROTE"));
        for (String[] row : Db.list("select label,value from public_answers where app_id=? order by ord", rs -> new String[]{rs.getString(1), rs.getString(2)}, a.id))
            sb.append("<div class='rw'><div><b>").append(Html.esc(row[1])).append("</b><small>").append(Html.esc(row[0])).append("</small></div></div>");

        List<PFile> files = Db.list("select * from public_files where app_id=? order by id", PF, a.id);
        if (!files.isEmpty()) sb.append(Html.label("FILES"));
        for (PFile f : files) {
            String url = "/staff/public/file?id=" + f.id;
            sb.append(Html.card("<b>" + Html.esc(f.kind) + "</b><span class='mu'>" + Html.esc(f.filename) + "</span>"
                    + (f.mime.startsWith("image/") ? "<img class='big' src='" + url + "' alt='" + Html.esc(f.kind) + "'>" : "")
                    + "<a class='sm' href='" + url + "' target='_blank'>Open</a>"));
        }

        List<String> optList = new ArrayList<>();
        for (Map.Entry<String, String> e : STATUS.entrySet()) optList.add(e.getKey() + "|" + e.getValue());
        sb.append(Html.label("UPDATE")).append(Html.form("/staff/public/update", Html.hidden("id", String.valueOf(a.id))
                + Html.select("New status", "status", optList.toArray(new String[0]))
                + Html.selectOf("Collection office (used when you choose Approved)", "office", "", prepend(Policy.OFFICES))
                + Html.field("Note to the applicant (they see it when they check)", "note", "text", a.note) + "<br>" + Html.button("Save")));
        return Resp.html(Html.page(a.ref, "Walk-in Applications", "/staff/public", sb.toString()));
    }

    static Resp staffUpdate(Req r) {
        Resp bad = gate(r);
        if (bad != null) return bad;
        PApp a = byId(r.get("id"));
        if (a == null || !r.post()) return Resp.to("/staff/public", "err:Application not found.");
        String st = r.get("status");
        if (!STATUS.containsKey(st)) return Resp.to("/staff/public/view?id=" + a.id, "err:Unknown status.");
        String note = clean(r.get("note"), false);
        if (note.length() > 300) note = note.substring(0, 300);
        Db.exec("update public_apps set status=?, note=? where id=?", st, note, a.id);
        return Resp.to("/staff/public/view?id=" + a.id, "ok:Saved. The applicant will see this when they check.");
    }

    static Resp staffFile(Req r) {
        Resp bad = gate(r);
        if (bad != null) return bad;
        PFile f;
        try { f = Db.one("select * from public_files where id=?", PF, Integer.parseInt(r.get("id"))); } catch (NumberFormatException e) { f = null; }
        if (f == null) return Resp.notFound();
        try {
            Resp out = new Resp();
            out.bytes = Files.readAllBytes(Store.UPLOADS.resolve(f.stored));
            out.type = f.mime;
            return out;
        } catch (IOException e) { return Resp.notFound(); }
    }
}