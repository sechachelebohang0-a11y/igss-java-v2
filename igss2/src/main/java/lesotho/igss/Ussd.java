package lesotho.igss;

import lesotho.igss.Models.Application;
import lesotho.igss.Models.Citizen;
import lesotho.igss.Models.Payment;
import lesotho.igss.Models.Dept;
import lesotho.igss.Models.Ticket;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * For people without a smartphone or internet. This is a prototype of the two channels they would use:
 *   - SMS: every notification is also written to an SMS log, so you can see the text the citizen would get on a basic phone.
 *   - USSD: a phone-style screen (dial *123#) that works with menus and numbers only. No real SMS or USSD gateway is connected.
 * The USSD PIN in this prototype is the last 4 digits of the ID number.
 */
final class Ussd {
    private Ussd() {}

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    static void init() {
        Db.exec("create table if not exists sms_log(id integer primary key autoincrement,citizen_id text,phone text,text text,created text)");
    }

    /** Called for every notification: keeps the text a basic phone would receive. */
    static void logSms(String citizenId, String text) {
        try {
            Citizen c = Store.citizen(citizenId);
            if (c == null || c.phone == null || c.phone.isEmpty()) return;
            String t = text.length() > 300 ? text.substring(0, 297) + "..." : text;
            Db.exec("insert into sms_log(citizen_id,phone,text,created) values(?,?,?,?)", citizenId, c.phone, t, LocalDateTime.now().toString());
        } catch (RuntimeException ignored) { }
    }

    // ---------------------------------------------------------------- SMS page (citizen)
    static Resp smsPage(Req r) {
        Citizen me = r.citizen();
        if (me == null) return Resp.redirect("/login");
        StringBuilder sb = new StringBuilder(Html.intro("Every update is also sent as an SMS, so people with a basic phone are not left out. These are the messages that would go to " + (me.phone == null ? "your phone" : me.phone) + ". This is a prototype: no real SMS is sent."));
        List<String[]> l = Db.list("select text,created from sms_log where citizen_id=? order by id desc limit 40", rs -> new String[]{rs.getString(1), rs.getString(2)}, me.id);
        for (String[] m : l) sb.append(Html.card("<span class='mu'>" + WHEN.format(LocalDateTime.parse(m[1])) + " · IGSS</span><span>" + Html.esc(m[0]) + "</span>"));
        if (l.isEmpty()) sb.append(Html.mu("No messages yet."));
        sb.append(Html.card("<b>No smartphone?</b><span class='mu'>Dial *123# on any phone to check an application, see a pension payment or get a queue number.</span><a class='sm' href='/ussd'>Try the phone screen</a>"));
        return Resp.html(Html.page("SMS Messages", "Home", "/home", sb.toString()));
    }

    // ---------------------------------------------------------------- USSD simulator
    private static Citizen byPhone(String phone) {
        String d = phone.replaceAll("[^0-9]", "");
        if (d.length() < 8) return null;
        d = d.substring(d.length() - 8);
        List<Citizen> l = Db.list("select * from citizens where replace(replace(phone,' ',''),'+','') like ?", Store.CZ, "%" + d);
        return l.size() == 1 ? l.get(0) : null;
    }

    /** One USSD answer. text is what the person typed so far, joined with *. Returns "CON ..." (menu goes on) or "END ...". */
    static String respond(String phone, String text) {
        Citizen c = byPhone(phone);
        if (c == null) return "END This phone number is not registered. Visit a Home Affairs office.";
        String[] s = text.isEmpty() ? new String[0] : text.split("\\*", -1);
        if (s.length == 0) return "CON IGSS Lesotho\nEnter your 4-digit PIN.";
        if (!s[0].equals(c.id.substring(c.id.length() - 4))) return "END Wrong PIN.";
        String menu = "CON Hello " + c.name.split(" ")[0] + "\n1. My applications\n2. Grant and pension\n3. Get a queue number\n4. My receipts\n0. Exit";
        if (s.length == 1) return menu;
        switch (s[1]) {
            case "0": return "END Thank you. Goodbye.";
            case "1": {
                List<Application> l = Store.appsOf(c.id);
                if (l.isEmpty()) return "END You have no applications.";
                StringBuilder sb = new StringBuilder("END Your applications:");
                for (int i = 0; i < Math.min(3, l.size()); i++) sb.append("\n").append(l.get(i).reference).append(" ").append(l.get(i).status.label);
                return sb.toString();
            }
            case "2": {
                List<Payment> l = Store.benefitPaymentsOf(c.id);
                int owed = Store.taxOwed(c.id);
                if (l.isEmpty()) return "END No grant or pension payments yet." + (owed > 0 ? "\nTax owed to SRL: M " + owed : "");
                StringBuilder sb = new StringBuilder("END Payments:");
                for (int i = 0; i < Math.min(3, l.size()); i++) sb.append("\n").append(l.get(i).month).append(" M ").append(l.get(i).amount).append(l.get(i).paid ? " Paid" : " Pending");
                if (owed > 0) sb.append("\nTax owed to SRL: M ").append(owed);
                return sb.toString();
            }
            case "3": {
                StringBuilder sb = new StringBuilder("CON Which office?");
                Dept[] ds = Dept.values();
                if (s.length == 2) { for (int i = 0; i < ds.length; i++) sb.append("\n").append(i + 1).append(". ").append(ds[i].label); return sb.toString(); }
                int k;
                try { k = Integer.parseInt(s[2]) - 1; } catch (NumberFormatException e) { k = -1; }
                if (k < 0 || k >= ds.length) return "END Wrong choice.";
                for (Ticket t : Store.waiting(ds[k])) if (t.citizenId.equals(c.id)) return "END You are already in the queue at " + ds[k].label + ": " + t.number + ".";
                Ticket t = Store.addTicket(c.id, ds[k], "General enquiry");
                Store.notify(c.id, "Your queue number at " + ds[k].label + " is " + t.number + " for General enquiry.");
                return "END Queue number " + t.number + " at " + ds[k].label + ". Come to the office and wait to be called.";
            }
            case "4": {
                StringBuilder sb = new StringBuilder("END Receipts:");
                int n = 0;
                for (Payment p : Store.paymentsOf(c.id)) {
                    if (!p.paid || p.receipt == null) continue;
                    sb.append("\n").append(p.receipt).append(" M ").append(p.amount);
                    if (++n == 3) break;
                }
                return n == 0 ? "END No receipts yet." : sb.toString();
            }
            default: return "END Wrong choice.";
        }
    }

    static Resp ussd(Req r) {
        String phone = r.get("phone"), text = r.get("text"), input = r.get("input"), shown = "";
        boolean ended = false;
        String err = "";
        if (r.post()) {
            if (r.get("act").equals("dial")) { text = ""; }
            else text = text.isEmpty() ? input : text + "*" + input;
            if (phone.replaceAll("[^0-9]", "").length() < 8) err = "Write the 8-digit phone number of the phone.";
            else {
                String resp = respond(phone, text);
                ended = resp.startsWith("END");
                shown = resp.substring(4);
            }
        }
        StringBuilder sb = new StringBuilder("<div class='top'><a href='/public'>&lsaquo; Back</a></div><h2 class='t'>Phone without internet</h2>"
                + "<p class='in'>This is how someone with a basic phone uses the system. Dial *123#, then type the number of your choice. Prototype: the PIN is the last 4 digits of your ID number.</p>");
        if (!err.isEmpty()) sb.append("<div class='err'>").append(Html.esc(err)).append("</div>");
        if (shown.isEmpty()) {
            sb.append("<form method='post' action='/ussd'><input type='hidden' name='act' value='dial'><label class='f'>Phone number<input name='phone' value='").append(Html.esc(phone)).append("' inputmode='tel' maxlength='16' autocomplete='off'></label>")
                    .append("<button class='btn'>Dial *123#</button></form>");
        } else {
            sb.append("<div class='card' style='font-family:monospace;white-space:pre-wrap;background:#e8f0e0;border:2px solid #1f2937'>").append(Html.esc(shown)).append("</div>");
            if (ended) sb.append("<form method='post' action='/ussd'><input type='hidden' name='act' value='dial'><input type='hidden' name='phone' value='").append(Html.esc(phone)).append("'><button class='btn'>Dial *123# again</button></form>");
            else sb.append("<form method='post' action='/ussd' autocomplete='off'><input type='hidden' name='phone' value='").append(Html.esc(phone)).append("'><input type='hidden' name='text' value='").append(Html.esc(text))
                    .append("'><label class='f'>Your reply<input name='input' inputmode='numeric' maxlength='11' autofocus></label><button class='btn'>Send</button></form>");
        }
        return Resp.html("<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><title>Phone without internet - IGSS Lesotho</title><style>"
                + "body{margin:0;background:#dfe6ee;font-family:'Segoe UI',Arial,sans-serif;color:#1f2937}.phone{max-width:420px;margin:0 auto;min-height:100vh;background:#eef7ff;padding:20px 22px 30px}"
                + ".top a{color:#00209f;font-weight:800;font-size:14px;text-decoration:none}h2.t{color:#00209f;font-size:20px;margin:10px 0 4px}p.in{font-size:13.5px;line-height:1.4}"
                + "label.f{display:flex;flex-direction:column;gap:4px;font-size:12px;font-weight:700;margin:10px 0}input{border:1.5px solid #1f2937;border-radius:10px;padding:11px;font:16px 'Segoe UI',Arial,sans-serif}"
                + ".btn{display:block;width:100%;border:0;border-radius:10px;background:#1a4fb4;color:#fff;font:700 15px 'Segoe UI',Arial,sans-serif;padding:15px;cursor:pointer;margin-top:8px}"
                + ".card{border-radius:12px;padding:14px;margin:12px 0}.err{border:2px solid #9b1c1c;color:#9b1c1c;background:#fff;border-radius:12px;padding:10px 14px;font-weight:700;font-size:13px}</style></head><body><div class='phone' id='main'>"
                + sb + "</div></body></html>");
    }
}