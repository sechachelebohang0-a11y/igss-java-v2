package lesotho.igss;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The three entry screens of the system.
 *   /               Welcome page
 *   /start          Login / Sign Up (choose your role)
 *   /start/account  Already have an account?
 * Images are optional: put logo.png and background.jpg in a "static" folder next to the project
 * (or in src/main/resources/static). Without them the page shows a placeholder and a gradient.
 */
final class LandingPage {
    private LandingPage() {}

    private static final String CSS = """
        *{box-sizing:border-box}
        html,body{margin:0}
        body{background:#dfe6ee;font-family:'Segoe UI',Arial,Helvetica,sans-serif;color:#475569}
        a{text-decoration:none;color:inherit}
        .phone{max-width:420px;margin:0 auto;min-height:100vh;display:flex;flex-direction:column;padding:26px 24px 18px;background:#eef7ff}
        .phone.welcome{color:#fff;background:linear-gradient(rgba(0,0,0,.12),rgba(0,0,0,.28)),url('/background.jpg') center/cover no-repeat,linear-gradient(#7fa6c4 0%,#a9c8dc 35%,#4f7a95 60%,#5b7f3a 85%,#3d5a2a 100%)}
        .brand{display:flex;align-items:center;gap:10px}
        .logo{width:96px;height:96px;flex:none;display:flex;align-items:center;justify-content:center}
        .logo img{width:100%;height:100%;object-fit:contain}
        .ph{width:96px;height:96px;border:2px dashed #00209f;color:#00209f;border-radius:8px;display:flex;align-items:center;justify-content:center;text-align:center;font-size:11px;line-height:1.2}
        .welcome .ph{border-color:#fff;color:#fff}
        .brand h1{margin:0;font-size:15px;line-height:1.25;color:#00209f}
        .welcome .brand h1{color:#fff;text-shadow:0 1px 3px rgba(0,0,0,.6)}
        .tag{margin:70px 0 0;text-align:center;font-weight:700;font-size:15px;color:#fff;text-shadow:0 1px 3px rgba(0,0,0,.6)}
        .grow{flex:1;min-height:60px}
        .btn{display:block;width:100%;border:0;border-radius:8px;background:#0b5ed7;color:#fff;font:700 15px 'Segoe UI',Arial,sans-serif;padding:15px;text-align:center;cursor:pointer}
        .btn:hover{background:#0a53bd}
        .login{text-align:center;font-weight:700;font-size:14px;margin:24px 0 18px;color:#fff;text-shadow:0 1px 3px rgba(0,0,0,.7)}
        .login a{color:#6db3ff}
        h2.t{color:#00209f;font-size:17px;margin:56px 0 0}
        p.s{color:#475569;font-weight:700;font-size:15px;margin:22px 0 12px}
        .card{background:#fff;border:1px solid #6b7280;border-radius:12px;overflow:hidden}
        .opt{display:flex;gap:14px;align-items:center;padding:20px 18px;cursor:pointer;border-bottom:1px solid #e5e7eb;position:relative}
        .opt:last-child{border-bottom:0}
        .opt input{position:absolute;opacity:0}
        .radio{width:22px;height:22px;border-radius:50%;border:2px solid #00209f;flex:none;display:flex;align-items:center;justify-content:center}
        .opt input:checked+.radio::after{content:'';width:12px;height:12px;border-radius:50%;background:#00209f}
        .opt input:focus-visible+.radio{outline:3px solid #9cc2ff}
        .opt b{display:block;color:#00209f;font-size:13px}
        .opt small{display:block;color:#475569;font-size:11.5px;margin-top:4px}
        .foot{text-align:center;color:#00209f;font-weight:700;font-size:11.5px;margin:30px 0 4px}
        .top{display:flex;align-items:center;margin-top:60px}
        .top a{color:#00209f;font-weight:800;font-size:16px;padding:4px 10px}
        .top h2{flex:1;text-align:center;margin:0 38px 0 0;color:#00209f;font-size:16px}
        .q{text-align:center;font-weight:700;font-size:12px;margin:38px 0 14px}
        form{margin:0;display:flex;flex-direction:column;flex:1}
        """;

    private static Resp shell(String cls, String body) {
        return Resp.html("<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>Lesotho Integrated Government Services System</title><style>" + CSS + "</style></head>"
                + "<body><div class='phone " + cls + "'>" + body + "</div></body></html>");
    }

    private static String brand() {
        return "<div class='brand'><div class='logo'><img src='/logo.png' alt='Lesotho coat of arms' "
                + "onerror=\"this.parentNode.innerHTML='<span class=ph>add logo.png</span>'\"></div>"
                + "<h1>Lesotho<br>Integrated Government<br>Services System</h1></div>";
    }

    private static String option(String name, String value, String title, String sub, boolean checked) {
        return "<label class='opt'><input type='radio' name='" + name + "' value='" + value + "'" + (checked ? " checked" : "") + ">"
                + "<span class='radio'></span><span><b>" + title + "</b><small>" + sub + "</small></span></label>";
    }

    private static final String FOOT = "<div class='foot'>Secure &middot; Simple &middot; Connected</div>";

    /** Screen 1: Welcome. */
    static Resp welcome(Req r) {
        if (r.citizen() != null) return Resp.redirect("/home");
        if (r.staff() != null) return Resp.redirect("/staff");
        return shell("welcome", brand()
                + "<p class='tag'>One ID. All Services. A Better Lesotho</p><div class='grow'></div>"
                + "<a class='btn' href='/start'>Get Started</a>"
                + "<p class='login'>Already have an account? <a href='/start'>Login</a></p>");
    }

    /** Screen 2: choose your role. Citizen goes on to screen 3, Department Official goes to the staff login. */
    static Resp role(Req r) {
        String role = r.get("role");
        if (role.equals("official")) return Resp.redirect("/staff/departments");
        if (role.equals("citizen")) return Resp.redirect("/start/account");
        return shell("", brand()
                + "<h2 class='t'>Login / Sign Up</h2><p class='s'>Choose your role to continue</p>"
                + "<form method='get' action='/start'><div class='card'>"
                + option("role", "citizen", "Citizen", "your personal services", true)
                + option("role", "official", "Department Official", "Access department dashboard", false)
                + "</div><div class='grow'></div><button class='btn'>continue</button>" + FOOT + "</form>");
    }

    /** Screen 3: YES goes to the citizen login, NO goes to registration. */
    static Resp account(Req r) {
        String has = r.get("has");
        if (has.equals("yes")) return Resp.redirect("/login");
        if (has.equals("no")) return Resp.redirect("/register");
        return shell("", "<div class='top'><a href='/start' aria-label='Back'>&lt;</a><h2>Already have an account?</h2></div>"
                + "<p class='q'>Do you already have an account?</p>"
                + "<form method='get' action='/start/account'><div class='card'>"
                + option("has", "yes", "YES", "Login to your account", true)
                + option("has", "no", "NO", "Create a new account", false)
                + "</div><div class='grow'></div><button class='btn'>Continue</button>" + FOOT + "</form>");
    }

    /** Serves logo.png / background.jpg from the classpath (/static/...) or from a "static" folder in the working directory. */
    static Resp asset(String name, String mime) {
        byte[] data = null;
        try (InputStream in = LandingPage.class.getResourceAsStream("/static/" + name)) {
            if (in != null) data = in.readAllBytes();
        } catch (IOException ignored) { }
        if (data == null) {
            Path p = Paths.get("static", name);
            try { if (Files.isRegularFile(p)) data = Files.readAllBytes(p); } catch (IOException ignored) { }
        }
        if (data == null) return Resp.notFound();
        Resp out = new Resp();
        out.bytes = data;
        out.type = mime;
        return out;
    }
}