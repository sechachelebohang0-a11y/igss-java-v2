package lesotho.igss;

import java.util.*;

/** Page building blocks. The whole system uses one blue palette. */
final class Html {
    private Html() {}

    static final String BLUE = "#1a4fb4";
    static final Map<String, String> ICONS = new HashMap<>();
    static {
        ICONS.put("bank", "<path d='M3 9l9-6 9 6zM5 11v7M9.5 11v7M14.5 11v7M19 11v7M3 21h18'/>");
        ICONS.put("car", "<path d='M5 16v-4l2-5h10l2 5v4M3 16h18M3 12h18'/><circle cx='7.5' cy='17.5' r='1.5'/><circle cx='16.5' cy='17.5' r='1.5'/>");
        ICONS.put("shield", "<path d='M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z'/><path d='M9 12l2 2 4-4'/>");
        ICONS.put("coin", "<circle cx='12' cy='12' r='9'/><path d='M12 7v10M9.5 9.5h4a1.6 1.6 0 010 3.2h-3a1.6 1.6 0 000 3.2h4'/>");
        ICONS.put("pass", "<rect x='6' y='3' width='12' height='18' rx='2'/><circle cx='12' cy='10' r='3'/><path d='M9 16h6'/>");
        ICONS.put("user", "<circle cx='12' cy='8' r='4'/><path d='M4 21c0-4 4-6 8-6s8 2 8 6'/>");
        ICONS.put("doc", "<path d='M7 3h8l4 4v14H7z'/><path d='M15 3v4h4M10 13h6M10 17h6'/>");
        ICONS.put("cal", "<rect x='3' y='5' width='18' height='16' rx='2'/><path d='M3 10h18M8 3v4M16 3v4'/>");
        ICONS.put("bell", "<path d='M6 9a6 6 0 0112 0c0 6 2 7 2 7H4s2-1 2-7M10 20a2 2 0 004 0'/>");
        ICONS.put("lock", "<rect x='5' y='11' width='14' height='10' rx='2'/><path d='M8 11V8a4 4 0 018 0v3'/>");
        ICONS.put("folder", "<path d='M3 6h6l2 2h10v11H3z'/>");
        ICONS.put("search", "<circle cx='11' cy='11' r='6'/><path d='M16 16l4 4'/>");
        ICONS.put("id", "<rect x='3' y='5' width='18' height='14' rx='2'/><circle cx='9' cy='11' r='2'/><path d='M6 16c0-2 6-2 6 0M14 10h4M14 14h4'/>");
        ICONS.put("chart", "<circle cx='12' cy='12' r='9'/><path d='M7 15l4-4 3 2 3-4'/>");
        ICONS.put("fp", "<path d='M5 11a7 7 0 0114 0v2M8 20c-1-2-1-4-1-6a5 5 0 0110 0c0 3 0 5 1 7M12 11v4c0 2 .5 3.5 1.5 5'/>");
        ICONS.put("alert", "<path d='M12 3l10 18H2z'/><path d='M12 10v5M12 18h.01'/>");
        ICONS.put("card", "<rect x='3' y='6' width='18' height='12' rx='2'/><path d='M3 10h18M6 15h4'/>");
        ICONS.put("up", "<path d='M12 16V5M7 10l5-5 5 5M4 20h16'/>");
    }

    static final String CSS = "*{box-sizing:border-box}body{margin:0;background:#e6eefc;color:#0a2a8f;font:15px Arial,Helvetica,sans-serif}"
            + "#app{max-width:540px;margin:0 auto;min-height:100vh;background:#fff;box-shadow:0 0 30px #1a4fb433}a{color:inherit;text-decoration:none}"
            + ".hdr{background:#1a4fb4;color:#fff;padding:22px 22px 24px;border-radius:0 0 26px 26px}.hdr a{font-size:14px;font-weight:700;opacity:.92}.hdr h1{margin:10px 0 0;font-size:24px}"
            + ".body{padding:18px 20px 34px;display:flex;flex-direction:column;gap:14px}.in{margin:0;font-size:16px;font-weight:700;line-height:1.35}"
            + "svg{width:26px;height:26px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}"
            + ".tg{display:grid;grid-template-columns:repeat(3,1fr);gap:11px}.tl{border:1.5px solid #0a2a8f;border-radius:12px;aspect-ratio:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:7px;text-align:center;font-size:11px;font-weight:700;padding:4px;line-height:1.2}"
            + ".card{border:1px solid #c9d6f0;background:#f1f5fd;border-radius:16px;padding:14px;display:flex;flex-direction:column;gap:6px}.card .pl{align-self:flex-start}"
            + ".rw{display:flex;gap:10px;align-items:center;border:1px solid #c9d6f0;background:#f1f5fd;border-radius:16px;padding:13px}.rw>div{flex:1}.rw b{display:block;font-size:15px}.rw small{color:#5f6f8c;font-size:12px}"
            + ".pl{border-radius:20px;padding:4px 10px;color:#fff;font-size:11px;font-weight:700;white-space:nowrap}.pl.d{background:#0a2a8f}.pl.m{background:#2f6fdc}.pl.o{background:#fff;color:#1a4fb4;border:1.5px solid #1a4fb4}"
            + ".lb{font-size:11px;letter-spacing:.07em;font-weight:700;color:#5f6f8c;margin-top:4px}"
            + ".ch{display:flex;flex-wrap:wrap;gap:7px}.ch span{border-radius:20px;padding:6px 12px;font-weight:700;font-size:13px}.y span{background:#dbe6fb;color:#0a2a8f}.n span{background:#eef2fa;color:#8a98b8;text-decoration:line-through}"
            + "label.f{display:flex;flex-direction:column;gap:4px;font-size:12px;font-weight:700;color:#33466e}input,select{border:1.5px solid #c9d6f0;border-radius:10px;padding:11px;font-size:15px;background:#fff;color:#0a2a8f}"
            + ".bt{border:0;border-radius:12px;background:#1a4fb4;color:#fff;padding:14px;font-weight:700;font-size:16px;text-align:center;cursor:pointer;display:block;width:100%}.sm{border:1.5px solid #1a4fb4;background:#fff;color:#1a4fb4;border-radius:16px;padding:6px 12px;font-weight:700;font-size:12px;cursor:pointer;display:inline-block}"
            + ".ok,.err{border-radius:12px;padding:10px 14px;font-size:13px;font-weight:700}.ok{background:#dbe6fb;color:#0a2a8f}.err{background:#fff;color:#1a4fb4;border:2px solid #1a4fb4}"
            + ".sp{display:flex;gap:14px;align-items:flex-start;position:relative;padding-bottom:16px}.sp i{width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-style:normal;font-weight:700;flex:none;background:#c9d6f0;color:#5f6f8c}"
            + ".sp:not(:last-child):before{content:'';position:absolute;left:13px;top:28px;bottom:0;width:2px;background:#c9d6f0}.sp b{padding-top:4px}.sp small{display:block;color:#5f6f8c;font-weight:400}"
            + ".bar{height:8px;border-radius:5px;background:#c9d6f0}.bar i{display:block;height:8px;border-radius:5px;background:#1a4fb4}.mu{color:#5f6f8c;font-size:12px}form{margin:0}"
            + "img.ph{width:96px;height:96px;object-fit:cover;border-radius:12px;border:2px solid #1a4fb4}img.big{max-width:100%;border-radius:12px;border:1px solid #c9d6f0}"
            + ".two{display:flex;gap:12px;align-items:center}.two>div{flex:1}input[type=file]{padding:9px;width:100%}";

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String page(String title, String backText, String backHref, String body) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><title>"
                + esc(title) + " - IGSS Lesotho</title><style>" + CSS + "</style></head><body><div id='app'><div class='hdr'>"
                + (backHref == null ? "" : "<a href='" + backHref + "'>&lsaquo; " + esc(backText) + "</a>") + "<h1>" + esc(title) + "</h1></div><div class='body'>"
                + body + "</div></div></body></html>";
    }

    static String icon(String n) { return "<svg viewBox='0 0 24 24'>" + ICONS.getOrDefault(n, "") + "</svg>"; }
    static String intro(String s) { return "<p class='in'>" + esc(s) + "</p>"; }
    static String mu(String s) { return "<div class='mu'>" + esc(s) + "</div>"; }
    static String label(String s) { return "<div class='lb'>" + esc(s) + "</div>"; }

    /** msg looks like "ok:text" or "err:text". */
    static String banner(String msg) {
        if (msg == null || msg.isEmpty()) return "";
        if (msg.startsWith("ok:")) return "<div class='ok'>&#10003; " + esc(msg.substring(3)) + "</div>";
        if (msg.startsWith("err:")) return "<div class='err'>! " + esc(msg.substring(4)) + "</div>";
        return "";
    }

    static String pill(String t) {
        String c;
        switch (t) {
            case "Verified": case "Approved": case "Completed": case "Paid": case "Alive": case "Logged": case "Enrolled": c = "d"; break;
            case "Needs info": case "Missing": case "Overdue": case "Rejected": case "Deceased": case "Payment required": c = "o"; break;
            default: c = "m";
        }
        return "<span class='pl " + c + "'>" + esc(t) + "</span>";
    }

    /** items: {href, iconName, label} */
    static String tiles(List<String[]> items) {
        StringBuilder sb = new StringBuilder("<div class='tg'>");
        for (String[] i : items) sb.append("<a class='tl' href='").append(i[0]).append("'>").append(icon(i[1])).append(esc(i[2])).append("</a>");
        return sb.append("</div>").toString();
    }

    static String row(String title, String sub, String pillHtml, String href) {
        String inner = "<div><b>" + esc(title) + "</b><small>" + esc(sub) + "</small></div>" + (pillHtml == null ? "" : pillHtml) + (href == null ? "" : "<span class='mu'>&rsaquo;</span>");
        return href == null ? "<div class='rw'>" + inner + "</div>" : "<a class='rw' href='" + href + "'>" + inner + "</a>";
    }

    static String card(String inner) { return "<div class='card'>" + inner + "</div>"; }

    /**
     * One input box. A password is never written back into the page (even after a wrong login), and text and password
     * boxes are marked so the browser does not auto-fill an old ID number or password into them.
     */
    static String field(String label, String name, String type, String value) {
        boolean pw = type.equals("password");
        boolean guard = pw || type.equals("text");
        return "<label class='f'>" + esc(label) + "<input name='" + name + "' type='" + type + "' value='" + (pw ? "" : esc(value)) + "'"
                + " autocomplete='" + (pw ? "new-password" : "off") + "'"
                + (guard ? " readonly onfocus=\"this.removeAttribute('readonly')\"" : "") + "></label>";
    }

    /** options are "value|label" strings. */
    static String select(String label, String name, String... options) {
        StringBuilder sb = new StringBuilder("<label class='f'>" + esc(label) + "<select name='" + name + "'>");
        for (String o : options) {
            String[] p = o.split("\\|", 2);
            sb.append("<option value='").append(esc(p[0])).append("'>").append(esc(p.length > 1 ? p[1] : p[0])).append("</option>");
        }
        return sb.append("</select></label>").toString();
    }

    static String button(String text) { return "<button class='bt'>" + esc(text) + "</button>"; }
    static String form(String action, String inner) { return "<form method='post' action='" + action + "'>" + inner + "</form>"; }

    /** A form that uploads a file chosen from the user's computer. */
    static String uploadForm(String action, String hiddenAndSelects, String buttonText) {
        return "<form method='post' action='" + action + "' enctype='multipart/form-data' style='display:flex;flex-direction:column;gap:10px'>" + hiddenAndSelects
                + "<label class='f'>Choose a file from your computer (JPG, PNG or PDF, up to 5 MB)<input type='file' name='file' accept='image/png,image/jpeg,application/pdf' required></label>" + button(buttonText) + "</form>";
    }

    static String hidden(String name, String value) { return "<input type='hidden' name='" + name + "' value='" + esc(value) + "'>"; }

    static String chips(Collection<String> items, boolean good) {
        StringBuilder sb = new StringBuilder("<div class='ch " + (good ? "y" : "n") + "'>");
        for (String i : items) sb.append("<span>").append(good ? "&#10003; " : "&#128274; ").append(esc(i)).append("</span>");
        return sb.append("</div>").toString();
    }

    static String steps(List<String> labels, int cur) {
        StringBuilder sb = new StringBuilder("<div>");
        for (int i = 0; i < labels.size(); i++) {
            boolean done = i < cur, now = i == cur;
            String st = done ? "background:#0a2a8f;color:#fff" : now ? "background:#1a4fb4;color:#fff;box-shadow:0 0 0 5px #1a4fb433" : "";
            sb.append("<div class='sp'><i style='").append(st).append("'>").append(done ? "&#10003;" : String.valueOf(i + 1)).append("</i><b>")
                    .append(esc(labels.get(i))).append("<small>").append(done ? "Done" : now ? "In progress" : "Next").append("</small></b></div>");
        }
        return sb.append("</div>").toString();
    }
}