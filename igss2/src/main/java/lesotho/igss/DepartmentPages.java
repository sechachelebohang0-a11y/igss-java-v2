package lesotho.igss;

import lesotho.igss.Models.Dept;
import lesotho.igss.Models.Official;

/**
 * Department Selection (/staff/departments) and one login page per department (/staff/login?dept=HA).
 * An official can only log in on the page of the department they belong to.
 */
final class DepartmentPages {
    private DepartmentPages() {}

    private static final String CSS = """
        *{box-sizing:border-box}
        html,body{margin:0}
        body{background:#dfe6ee;font-family:'Segoe UI',Arial,Helvetica,sans-serif;color:#475569}
        a{text-decoration:none;color:inherit}
        .phone{max-width:420px;margin:0 auto;min-height:100vh;display:flex;flex-direction:column;padding:26px 24px 20px;background:#eef7ff}
        h2.t{color:#00209f;font-size:17px;text-align:center;margin:44px 0 0}
        p.s{color:#00209f;font-weight:700;font-size:14px;text-align:center;margin:30px 0 22px}
        .grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}
        .tile{background:#fff;border:1px solid #6b7280;border-radius:12px;aspect-ratio:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;text-align:center;color:#00209f;font-weight:700;font-size:13px;padding:8px;line-height:1.25}
        .tile:hover{background:#f3f8ff;box-shadow:0 2px 8px rgba(0,32,159,.18)}
        .tile svg{width:28px;height:28px}
        .grow{flex:1;min-height:30px}
        .back{display:block;text-align:center;color:#00209f;font-weight:700;font-size:13px;padding:10px;margin-top:16px}
        .top{display:flex;align-items:center;margin-top:44px}
        .top a{color:#00209f;font-weight:800;font-size:16px;padding:4px 10px}
        .top h2{flex:1;text-align:center;margin:0 38px 0 0;color:#00209f;font-size:16px}
        p.in{color:#00209f;font-weight:700;font-size:14px;text-align:center;margin:30px 0 8px}
        form{display:flex;flex-direction:column;margin-top:18px}
        label.f{display:flex;flex-direction:column;gap:6px;font-size:12px;font-weight:700;color:#33466e;margin-bottom:14px}
        input{border:1.5px solid #c9d6f0;border-radius:10px;padding:13px;font-size:15px;background:#fff;color:#0a2a8f}
        .btn{display:block;width:100%;border:0;border-radius:8px;background:#0b5ed7;color:#fff;font:700 15px 'Segoe UI',Arial,sans-serif;padding:15px;text-align:center;cursor:pointer;margin-top:6px}
        .btn:hover{background:#0a53bd}
        .ok,.err{border-radius:12px;padding:10px 14px;font-size:13px;font-weight:700;margin-top:10px}
        .ok{background:#dbe6fb;color:#0a2a8f}
        .err{background:#fff;color:#1a4fb4;border:2px solid #1a4fb4}
        .foot{text-align:center;color:#00209f;font-weight:700;font-size:11.5px;margin:22px 0 2px}
        """;

    private static final String FOOT = "<div class='foot'>Secure &middot; Simple &middot; Connected</div>";

    private static Resp shell(String title, String body) {
        return Resp.html("<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + Html.esc(title) + " - IGSS Lesotho</title><style>" + CSS + "</style></head>"
                + "<body><div class='phone'>" + body + "</div></body></html>");
    }

    private static String icon(Dept d) {
        String navy = "#00209f";
        switch (d) {
            case HA: return "<svg viewBox='0 0 24 24'><circle cx='12' cy='8' r='4' fill='" + navy + "'/><path d='M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7z' fill='" + navy + "'/></svg>";
            case TR: return "<svg viewBox='0 0 24 24' fill='" + navy + "'><rect x='3' y='10' width='18' height='7' rx='2'/><path d='M6 10l2-4.5h8L18 10z'/><circle cx='7.5' cy='17.5' r='1.8'/><circle cx='16.5' cy='17.5' r='1.8'/></svg>";
            case FI: return "<svg viewBox='0 0 24 24' fill='#0a9b4b'><ellipse cx='12' cy='6' rx='7' ry='3'/><path d='M5 8.5v9.5c0 1.7 3.1 3 7 3s7-1.3 7-3V8.5c0 1.7-3.1 3-7 3s-7-1.3-7-3z'/></svg>";
            case PE: return "<svg viewBox='0 0 24 24'><circle cx='12' cy='12' r='11' fill='#fff' stroke='#d1d5db'/><circle cx='12' cy='9.5' r='3.2' fill='" + navy + "'/><path d='M5.5 19c.8-3.4 3.5-5 6.5-5s5.7 1.6 6.5 5c-1.7 1.8-3.9 2.8-6.5 2.8S7.2 20.8 5.5 19z' fill='" + navy + "'/></svg>";
            case PO: return "<svg viewBox='0 0 24 24'><path d='M12 2l8 3v6c0 5-3.4 9-8 11-4.6-2-8-6-8-11V5z' fill='" + navy + "'/><path d='M12 7.5l1.4 3 3.3.4-2.4 2.3.6 3.3-2.9-1.6-2.9 1.6.6-3.3-2.4-2.3 3.3-.4z' fill='#fff'/></svg>";
            default: return "<svg viewBox='0 0 24 24'><rect x='5' y='2' width='14' height='20' rx='2' fill='" + navy + "'/><circle cx='12' cy='10' r='3' fill='none' stroke='#fff' stroke-width='1.5'/><rect x='8.5' y='15.5' width='7' height='1.6' rx='.8' fill='#fff'/></svg>";
        }
    }

    private static Dept dept(String s) {
        try { return Dept.valueOf(s); } catch (Exception e) { return null; }
    }

    /** Department Selection: six tiles, each one leads to that department's own login. */
    static Resp select(Req r) {
        StringBuilder tiles = new StringBuilder("<div class='grid'>");
        for (Dept d : Dept.values())
            tiles.append("<a class='tile' href='/staff/login?dept=").append(d.name()).append("'>")
                    .append(icon(d)).append("<span>").append(Html.esc(d.label)).append("</span></a>");
        tiles.append("</div>");
        return shell("Select Department", "<h2 class='t'>Select Department</h2>"
                + "<p class='s'>Choose the department you want to access</p>" + tiles
                + "<div class='grow'></div><a class='back' href='/start'>Back</a>");
    }

    /** Login page of one department. Staff of another department are refused here. */
    static Resp login(Req r) {
        Dept dept = dept(r.get("dept"));
        if (dept == null) return Resp.redirect("/staff/departments");
        String sv = "", idErr = "", pwErr = "";
        if (r.post()) {
            sv = r.get("staff");
            Official o = Store.official(sv);
            String pw = r.p.getOrDefault("password", "");
            if (sv.isEmpty()) idErr = "Enter your staff ID.";
            else if (o == null || o.dept != dept) idErr = "Couldn't find that staff ID in " + dept.label + ". Check it and try again.";
            else if (pw.isEmpty()) pwErr = "Enter your password.";
            else if (!Store.hash(o.salt, pw).equals(o.hash)) pwErr = "Wrong password. Try again.";
            else {
                Resp resp = Resp.redirect("/staff");
                resp.newSession = new Sess("O", o.staffId);
                return resp;
            }
        }
        return shell(dept.label + " Login",
                "<div class='top'><a href='/staff/departments' aria-label='Back'>&lt;</a><h2>" + Html.esc(dept.label) + " Login</h2></div>"
                        + "<p class='in'>Staff only. Every action is logged.</p>" + Html.banner(r.get("m"))
                        + "<form method='post' action='/staff/login' autocomplete='off'>" + Html.hidden("dept", dept.name())
                        + Html.fieldErr("Staff ID", "staff", "text", sv, idErr) + Html.fieldErr("Password", "password", "password", "", pwErr)
                        + "<button class='btn'>Login</button></form>"
                        + "<div class='grow'></div><a class='back' href='/staff/departments'>Back</a>" + FOOT);
    }
}