package lesotho.igss;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/** The shared building blocks of every paper-style form: field definitions, drawing a field, checking a value and checking an uploaded file. */
final class FormKit {
    private FormKit() {}

    static final int MAX_FILE = 5 * 1024 * 1024;

    static final List<String> DISTRICTS = List.of("Berea", "Butha-Buthe", "Leribe", "Mafeteng", "Maseru",
            "Mohale's Hoek", "Mokhotlong", "Qacha's Nek", "Quthing", "Thaba-Tseka");
    static final List<String> MARITAL = List.of("Single", "Married", "Widowed", "Divorced", "Separated");
    static final List<String> BANKS = List.of("Standard Lesotho Bank", "Nedbank Lesotho", "First National Bank Lesotho", "Lesotho PostBank", "Other");

    // ------------------------------------------------------------------ field definitions
    /**
     * One box on a form. type is one of: text, area, date (past only), datefut (any date), time, tel, email, idnum (digits), num (a number), year, select.
     * A field marked with when(...) is required only when another field has one of the given values.
     */
    static final class Fld {
        final String name, label, type, hint, def, condName;
        final boolean req;
        final List<String> opts, condVals;

        Fld(String name, String label, String type, boolean req, String hint, String def, List<String> opts, String condName, List<String> condVals) {
            this.name = name; this.label = label; this.type = type; this.req = req; this.hint = hint; this.def = def;
            this.opts = opts; this.condName = condName; this.condVals = condVals;
        }

        Fld(String name, String label, String type, boolean req, String hint, String def, List<String> opts) {
            this(name, label, type, req, hint, def, opts, null, List.of());
        }

        Fld when(String other, String... values) { return new Fld(name, label, type, req, hint, def, opts, other, List.of(values)); }
        Fld withHint(String h) { return new Fld(name, label, type, req, h, def, opts, condName, condVals); }
        Fld withDefault(String d) { return new Fld(name, label, type, req, hint, d, opts, condName, condVals); }

        boolean required(Map<String, String> vals) {
            return req || (condName != null && condVals.contains(vals.getOrDefault(condName, "")));
        }
    }

    static final class Sec {
        final String title;
        final List<Fld> fields;
        Sec(String title, Fld... fields) { this.title = title; this.fields = List.of(fields); }
    }

    static Fld f(String name, String label, String type, boolean req) { return new Fld(name, label, type, req, "", "", List.of()); }
    static Fld f(String name, String label, String type, boolean req, String hint) { return new Fld(name, label, type, req, hint, "", List.of()); }
    static Fld fd(String name, String label, String type, boolean req, String def) { return new Fld(name, label, type, req, "", def, List.of()); }
    static Fld sel(String name, String label, boolean req, String... opts) { return new Fld(name, label, "select", req, "", "", List.of(opts)); }
    static Fld selList(String name, String label, boolean req, List<String> opts) { return new Fld(name, label, "select", req, "", "", opts); }
    static Fld yn(String name, String label, boolean req) { return sel(name, label, req, "Yes", "No"); }

    static Fld txt(String n, String l, boolean r) { return f(n, l, "text", r); }
    static Fld area(String n, String l, boolean r) { return f(n, l, "area", r); }
    static Fld dt(String n, String l, boolean r) { return f(n, l, "date", r); }
    static Fld dtf(String n, String l, boolean r) { return f(n, l, "datefut", r); }
    static Fld tm(String n, String l, boolean r) { return f(n, l, "time", r); }
    static Fld tel(String n, String l, boolean r) { return f(n, l, "tel", r); }
    static Fld email(String n, String l, boolean r) { return f(n, l, "email", r); }
    static Fld idnum(String n, String l, boolean r) { return f(n, l, "idnum", r); }
    static Fld num(String n, String l, boolean r) { return f(n, l, "num", r); }
    static Fld year(String n, String l, boolean r) { return f(n, l, "year", r); }

    // ------------------------------------------------------------------ drawing a field
    static String input(Fld f, String value) {
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
            case "datefut": ctl = "<input type='date' name='" + n + "' value='" + ev + "' min='1900-01-01' max='2100-12-31'" + rq + ">"; break;
            case "time": ctl = "<input type='time' name='" + n + "' value='" + ev + "'" + rq + ">"; break;
            case "tel": ctl = "<input type='tel' inputmode='tel' name='" + n + "' value='" + ev + "' maxlength='20' autocomplete='off'" + rq + ">"; break;
            case "email": ctl = "<input type='email' name='" + n + "' value='" + ev + "' maxlength='120' autocomplete='off'" + rq + ">"; break;
            case "idnum": ctl = "<input type='text' inputmode='numeric' name='" + n + "' value='" + ev + "' maxlength='13' autocomplete='off'" + rq + ">"; break;
            case "num": ctl = "<input type='text' inputmode='decimal' name='" + n + "' value='" + ev + "' maxlength='12' autocomplete='off'" + rq + ">"; break;
            case "year": ctl = "<input type='text' inputmode='numeric' name='" + n + "' value='" + ev + "' maxlength='4' autocomplete='off'" + rq + ">"; break;
            default: ctl = "<input type='text' name='" + n + "' value='" + ev + "' maxlength='120' autocomplete='off'" + rq + ">";
        }
        return "<label class='f'><span>" + Html.esc(f.label) + (f.req ? " <i>*</i>" : "") + "</span>" + ctl
                + (f.hint.isEmpty() ? "" : "<small>" + Html.esc(f.hint) + "</small>") + "</label>";
    }

    /** The photo box: upload a file or take a picture with the camera. Needs CAMERA_JS on the page. */
    static String photoInner() {
        return "<small>Face the camera in good light, with a plain light background. No hat or sunglasses. JPG or PNG, up to 5 MB.</small>"
                + "<div class='ph'><img id='pv' alt='Your photo' hidden><span id='pn'>No photo yet</span></div>"
                + "<video id='vid' autoplay playsinline muted hidden></video>"
                + "<div class='row'><label class='sm'>Upload a photo<input type='file' name='photo' id='photo' accept='image/jpeg,image/png' hidden></label>"
                + "<button type='button' class='sm' id='cam'>Take a photo</button><button type='button' class='sm' id='snap' hidden>Capture</button></div>"
                + "<small id='cmsg' style='display:block;color:#1a4fb4;font-weight:700;margin:4px 0 8px'></small>";
    }

    static final String CAMERA_JS = """
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
            if(inp.dataset.need!=='0' && (!inp.files||!inp.files.length)){ e.preventDefault(); msg.textContent='Please add your photo first.'; inp.scrollIntoView({block:'center'}); }
          });
        })();
        """;

    // ------------------------------------------------------------------ checking values
    /** Removes control characters. A multi-line box keeps its line breaks. */
    static String clean(String s, boolean multiline) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\n' && multiline) sb.append(c);
            else if (c < 32 || c == 127) sb.append(' ');
            else sb.append(c);
        }
        return sb.toString().trim();
    }

    static LocalDate date(String v) {
        try { return LocalDate.parse(v); } catch (DateTimeParseException e) { return null; }
    }

    static String digits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) if (c >= '0' && c <= '9') sb.append(c);
        return sb.toString();
    }

    /** Returns what is wrong with a value, or null when it is fine. vals holds every value on the form (for fields that depend on another field). */
    static String fieldError(Fld f, String v, Map<String, String> vals) {
        if (v.isEmpty()) return f.required(vals) ? f.label + " is required." : null;
        switch (f.type) {
            case "select": return f.opts.contains(v) ? null : f.label + ": choose one of the options.";
            case "date": {
                LocalDate d = date(v);
                return d == null || d.isAfter(LocalDate.now()) || d.getYear() < 1900 ? f.label + " must be a real date in the past." : null;
            }
            case "datefut": {
                LocalDate d = date(v);
                return d == null || d.getYear() < 1900 || d.getYear() > 2100 ? f.label + " must be a real date." : null;
            }
            case "time":
                try { LocalTime.parse(v); return null; } catch (DateTimeParseException e) { return f.label + " is not a valid time."; }
            case "tel": {
                int n = digits(v).length();
                return n >= 7 && n <= 15 && v.matches("[0-9 +()-]+") ? null : f.label + " is not a valid phone number.";
            }
            case "idnum":
                return v.replace(" ", "").replace("-", "").matches("[0-9]{11}") ? null : f.label + " is wrong. A Lesotho ID number has exactly 11 digits (for example 04123457678).";
            case "num":
                return v.matches("[0-9]{1,9}([.][0-9]{1,2})?") ? null : f.label + " must be a number.";
            case "year":
                return v.matches("[0-9]{4}") && Integer.parseInt(v) >= 1950 && Integer.parseInt(v) <= LocalDate.now().getYear() + 1 ? null : f.label + " must be a year, for example 2018.";
            case "email":
                return v.length() <= 120 && v.matches("[^@ ]+@[^@ ]+[.][^@ ]+") ? null : f.label + " is not a valid email address.";
            case "area": return v.length() <= 400 ? null : f.label + " is too long.";
            default: return v.length() <= 120 ? null : f.label + " is too long.";
        }
    }

    // ------------------------------------------------------------------ checking uploaded files
    /** What the file really is, judged from its first bytes (never from the name the browser sent). */
    static String sniff(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length > 5 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return "application/pdf";
        return null;
    }

    /** Returns what is wrong with an upload, or null when it is fine. */
    static String fileError(Part p, boolean allowPdf) {
        if (p.data.length > MAX_FILE) return "must be 5 MB or smaller.";
        String m = sniff(p.data);
        if (m == null || (!allowPdf && m.equals("application/pdf"))) return allowPdf ? "must be a JPG, PNG or PDF file." : "must be a JPG or PNG image.";
        return null;
    }

    static String extension(String mime) { return mime.equals("image/png") ? ".png" : mime.equals("application/pdf") ? ".pdf" : ".jpg"; }

    static String safeName(String n) {
        if (n == null) return "file";
        int i = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (i >= 0) n = n.substring(i + 1);
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-' || c == ' ') sb.append(c);
        String s = sb.toString().trim();
        if (s.length() > 60) s = s.substring(0, 60);
        return s.isEmpty() ? "file" : s;
    }
}