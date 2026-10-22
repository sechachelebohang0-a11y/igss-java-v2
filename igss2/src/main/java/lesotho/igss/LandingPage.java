package lesotho.igss;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The entry screens of the system.
 *   /               Welcome page (full website style)
 *   /start          Login / Sign Up (choose your role)
 *   /start/account  Already have an account?
 * Images are optional: put logo.png and background.jpg in a "static" folder next to the project
 * (or in src/main/resources/static). Without them the page shows a placeholder and a gradient.
 */
final class LandingPage {
    private LandingPage() {}

    // ------------------------------------------------------------------ phone-style screens (role, account)
    private static final String CSS = """
        *{box-sizing:border-box}
        html,body{margin:0}
        body{background:#dfe6ee;font-family:'Segoe UI',Arial,Helvetica,sans-serif;color:#475569}
        a{text-decoration:none;color:inherit}
        .phone{max-width:420px;margin:0 auto;min-height:100vh;display:flex;flex-direction:column;padding:26px 24px 18px;background:#eef7ff}
        .brand{display:flex;align-items:center;gap:10px}
        .logo{width:96px;height:96px;flex:none;display:flex;align-items:center;justify-content:center}
        .logo img{width:100%;height:100%;object-fit:contain}
        .ph{width:96px;height:96px;border:2px dashed #00209f;color:#00209f;border-radius:8px;display:flex;align-items:center;justify-content:center;text-align:center;font-size:11px;line-height:1.2}
        .brand h1{margin:0;font-size:15px;line-height:1.25;color:#00209f}
        .grow{flex:1;min-height:60px}
        .btn{display:block;width:100%;border:0;border-radius:8px;background:#1a4fb4;color:#fff;font:700 15px 'Segoe UI',Arial,sans-serif;padding:15px;text-align:center;cursor:pointer}
        .btn:hover{background:#143f91}
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

    // ------------------------------------------------------------------ the welcome website page
    private static final String WCSS = """
        *{box-sizing:border-box}
        html{scroll-behavior:smooth}
        html,body{margin:0}
        body{font-family:'Segoe UI',Arial,Helvetica,sans-serif;color:#33466e;background:#fff}
        a{text-decoration:none;color:inherit}
        svg{fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
        .nav{position:sticky;top:0;z-index:50;background:#fff;box-shadow:0 1px 8px #0a2a8f22;display:flex;align-items:center;gap:20px;padding:10px 6vw}
        .nbrand{display:flex;align-items:center;gap:12px;flex:none}
        .nlogo{width:58px;height:58px;display:flex;align-items:center;justify-content:center}
        .nlogo img{width:100%;height:100%;object-fit:contain}
        .nlogo .ph{width:58px;height:58px;border:2px dashed #1a4fb4;color:#1a4fb4;border-radius:8px;display:flex;align-items:center;justify-content:center;text-align:center;font-size:9px;line-height:1.1}
        .nbrand b{display:block;color:#0a2a8f;font-size:20px;line-height:1.1}
        .nbrand span{display:block;color:#0a2a8f;font-weight:600;font-size:14px}
        .menu{display:flex;gap:30px;margin:0 auto;font-size:15px}
        .menu a{padding:8px 0;color:#5f6f8c;border-bottom:3px solid transparent}
        .menu a.on{color:#1a4fb4;font-weight:700;border-color:#1a4fb4}
        .menu a:hover{color:#1a4fb4}
        .acts{display:flex;align-items:center;gap:12px;flex:none}
        .b1,.b2{border-radius:10px;padding:10px 24px;font-weight:700;font-size:15px;border:2px solid #1a4fb4}
        .b1{color:#1a4fb4;background:#fff}.b1:hover{background:#eef4ff}
        .b2{color:#fff;background:#1a4fb4}.b2:hover{background:#143f94}
        .uic{width:26px;height:26px;color:#0a2a8f}
        .hero{position:relative;min-height:470px;color:#fff;padding:54px 6vw 40px;display:flex;flex-direction:column;justify-content:space-between;gap:30px;overflow:hidden;
          background:linear-gradient(90deg,rgba(6,28,90,.62),rgba(6,28,90,.12) 70%),url('/background.jpg') center/cover no-repeat,linear-gradient(#5d8fc2 0%,#9cc3dd 40%,#4f7a95 62%,#5b7f3a 85%,#3d5a2a 100%)}
        .tag{display:flex;flex-wrap:wrap;gap:12px;align-items:center;font-weight:600;font-size:17px;text-shadow:0 1px 4px #0008}
        .tag i{font-style:normal;opacity:.9}
        .hero h1{margin:14px 0 8px;font-size:clamp(30px,5vw,54px);line-height:1.05;text-shadow:0 2px 8px #0008}
        .hero h1 .l{display:block;font-weight:300}
        .hero h1 .h{display:block;font-weight:800}
        .hero p.lead{margin:0;font-size:clamp(15px,1.6vw,19px);line-height:1.45;max-width:560px;text-shadow:0 1px 4px #0009}
        .hbtns{display:flex;flex-wrap:wrap;gap:12px;margin-top:20px}
        .hbtns a{border-radius:10px;padding:12px 22px;font-weight:700;font-size:15px}
        .h1b{background:#fff;color:#0a2a8f}.h1b:hover{background:#eef4ff}
        .h2b{border:2px solid #fff;color:#fff}.h2b:hover{background:#ffffff22}
        .feats{display:flex;flex-wrap:wrap;gap:22px 34px}
        .ft{display:flex;align-items:center;gap:12px}
        .ft .ic{width:46px;height:46px;border-radius:12px;background:#fff;color:#1a4fb4;display:flex;align-items:center;justify-content:center;flex:none}
        .ft .ic svg{width:26px;height:26px}
        .ft b{display:block;font-size:15px}
        .ft small{display:block;font-size:12.5px;opacity:.95}
        .motto{position:absolute;right:5vw;bottom:34px;font:italic 700 clamp(16px,2vw,26px)/1.25 'Segoe Script','Brush Script MT',cursive;text-align:left;transform:rotate(-8deg);text-shadow:0 2px 6px #000a;max-width:240px}
        .sec{padding:44px 6vw}
        .intro{display:grid;grid-template-columns:minmax(260px,1fr) minmax(260px,330px) minmax(260px,430px);gap:26px;align-items:start;max-width:1250px;margin:0 auto}
        .intro h2{margin:0 0 10px;font-size:32px;line-height:1.1;color:#0a2a8f}
        .intro p{margin:0 0 22px;line-height:1.6;font-size:15.5px}
        .cardw{background:#fff;border:1px solid #dfe8f7;border-radius:14px;box-shadow:0 4px 18px #1a4fb41c;padding:22px;display:flex;gap:16px;align-items:center}
        .cardw .ci{width:62px;height:62px;border-radius:50%;background:#4b4fc4;color:#fff;display:flex;align-items:center;justify-content:center;flex:none}
        .cardw .ci svg{width:32px;height:32px}
        .cardw .ci.lt{background:#e6eefc;color:#1a4fb4;width:50px;height:50px}
        .cardw b{display:block;color:#0a2a8f;font-size:16px;margin-bottom:6px}
        .cardw span{display:block;font-size:13.5px;line-height:1.5;color:#5f6f8c}
        .cardw .go{margin-left:auto;width:32px;height:32px;border-radius:50%;background:#e6eefc;color:#1a4fb4;display:flex;align-items:center;justify-content:center;flex:none;font-weight:800}
        .cardw a.lnk{display:inline-block;margin-top:8px;color:#1a4fb4;font-weight:700;font-size:14px}
        .pic{border-radius:14px;overflow:hidden;min-height:325px;background:#e6eefc;display:flex;align-items:center;justify-content:center;color:#5f6f8c;font-size:14px;text-align:center}
        .pic img{display:block;width:100%;height:100%;min-height:325px;object-fit:cover}
        .flag{border-radius:14px;overflow:hidden;min-height:325px;display:flex;flex-direction:column;justify-content:flex-end;color:#fff;background:linear-gradient(#86b6e8,#3f7bbf 55%,#0a2a6f)}
        .flag svg.f{display:block;width:100%;height:auto;flex:none;stroke:none;fill:initial}
        .flag .cap{padding:16px 20px 22px;background:linear-gradient(transparent,#0a2a6f 38%)}
        .flag .cap b{display:block;font-size:26px}
        .flag .cap span{display:block;font-size:15px;line-height:1.4;margin-top:4px}
        .flag .cap i{display:block;width:120px;height:4px;border-radius:4px;background:linear-gradient(90deg,#fff,#2f6fdc);margin-top:12px}
        .col{display:flex;flex-direction:column;gap:20px}
        .about{text-align:center;background:#f1f5fd}
        .about h2{margin:0 0 6px;font-size:28px;color:#0a2a8f;text-align:center}
        .about p{max-width:760px;margin:10px auto 0;line-height:1.7;font-size:16px}
        .foot{background:#0a2a5f;color:#fff;padding:26px 6vw;display:flex;flex-wrap:wrap;align-items:center;gap:22px 40px}
        .foot .fb{display:flex;align-items:center;gap:12px}
        .foot .flogo{width:54px;height:54px;display:flex;align-items:center;justify-content:center}
        .foot .flogo img{width:100%;height:100%;object-fit:contain}
        .foot .flogo .ph{width:54px;height:54px;border:2px dashed #fff;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:9px;text-align:center}
        .foot b{display:block;font-size:17px}.foot small{display:block;font-size:13px;opacity:.9}
        .foot nav{display:flex;flex-wrap:wrap;gap:10px 28px;font-size:13px;margin:0 auto}
        .foot nav a:hover{text-decoration:underline}
        .foot .gov{border-left:1px solid #ffffff55;padding-left:28px;font-style:italic;font-size:12.5px;line-height:1.7}
        @media(max-width:1000px){.intro{grid-template-columns:1fr 1fr}.intro>div:first-child{grid-column:1/-1}.motto{display:none}}
        @media(max-width:760px){.menu{display:none}.nav{justify-content:space-between}.nbrand span{font-size:12px}.nbrand b{font-size:17px}.b1,.b2{padding:8px 14px;font-size:14px}.uic{display:none}
          .intro{grid-template-columns:1fr}.hero{padding-top:36px}.foot .gov{border:0;padding:0}.foot nav{margin:0}}
        """;

    private static String logoImg() {
        return "<img src='/logo.png' alt='Lesotho coat of arms' onerror=\"this.parentNode.innerHTML='<span class=ph>add logo.png</span>'\">";
    }

    /** The Lesotho flag drawn in code (blue, white, green stripes and the Basotho hat), so no flag image is needed. */
    private static String flagSvg() {
        return "<svg class='f' viewBox='0 0 300 190' role='img' aria-label='Flag of Lesotho'>"
                + "<rect x='24' y='4' width='5' height='186' fill='#c8c8c8'/>"
                + "<rect x='29' y='14' width='250' height='54' fill='#0b4fb8'/>"
                + "<rect x='29' y='68' width='250' height='64' fill='#fff'/>"
                + "<rect x='29' y='132' width='250' height='48' fill='#0c8a3f'/>"
                + "<path d='M154 78 L169 78 L184 112 L139 112 Z' fill='#111'/>"
                + "<rect x='136' y='110' width='51' height='5' rx='2' fill='#111'/>"
                + "<circle cx='161.5' cy='76' r='3.2' fill='#111'/></svg>";
    }

    private static String feature(String icon, String title, String sub) {
        return "<div class='ft'><div class='ic'>" + Html.icon(icon) + "</div><div><b>" + title + "</b><small>" + sub + "</small></div></div>";
    }

    /** Screen 1: the welcome website page. */
    static Resp welcome(Req r) {
        if (r.citizen() != null) return Resp.redirect("/home");
        if (r.staff() != null) return Resp.redirect("/staff");

        String html = "<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>Lesotho Integrated Government Services System</title><style>" + WCSS + "</style></head><body>"

                // top bar
                + "<header class='nav'><a class='nbrand' href='/'><div class='nlogo'>" + logoImg() + "</div>"
                + "<div><b>Lesotho</b><span>Integrated Government Services System</span></div></a>"
                + "<nav class='menu'><a class='on' href='/'>Home</a><a href='#about'>About Us</a><a href='#help'>Help &amp; Support</a></nav>"
                + "<div class='acts'><a class='b1' href='/start'>Login</a><a class='b2' href='/register'>Sign Up</a>"
                + "<a href='/start' aria-label='Account'>" + Html.icon("user").replace("<svg ", "<svg class='uic' ") + "</a></div></header>"

                // hero
                + "<section class='hero'><div>"
                + "<div class='tag'><span>One Platform</span><i>&bull;</i><span>Many Services</span><i>&bull;</i><span>A Better Lesotho</span></div>"
                + "<h1><span class='l'>Lesotho Integrated</span><span class='h'>Government Services System</span></h1>"
                + "<p class='lead'>Access government services in one place. Fast, secure and convenient for every citizen, business and government official.</p>"
                + "<div class='hbtns'><a class='h1b' href='/start'>Get Started</a><a class='h2b' href='/public'>Apply without an account</a></div></div>"
                + "<div class='feats'>"
                + feature("shield", "Secure", "Your data is protected")
                + feature("cal", "Convenient", "Access anytime, anywhere")
                + feature("user", "For Everyone", "Citizens, businesses &amp; officials")
                + feature("bank", "Connected Departments", "Better service delivery")
                + "</div><div class='motto'>A connected government for a stronger Lesotho</div></section>"

                // your government, one system
                + "<section class='sec'><div class='intro'>"
                + "<div><h2>Your Government, One System</h2>"
                + "<p>The Lesotho Integrated Government Services System brings together key government departments to make services more accessible, transparent and efficient for all.</p>"
                + "<div class='cardw' id='help'><div class='ci lt'>" + Html.icon("user") + "</div><div><b>Need help?</b>"
                + "<span>Not sure where to start? Apply without an account, or visit your nearest Home Affairs office.</span>"
                + "<a class='lnk' href='/public'>Get Support &rarr;</a></div></div></div>"
                + "<div class='pic'><img src='/feature.jpg' alt='' onerror=\"this.parentNode.innerHTML='<span>Add your picture as static/feature.jpg</span>'\"></div>"
                + "<div class='col'><a class='cardw' href='/public/track'><div class='ci'>" + Html.icon("bank") + "</div>"
                + "<div><b>Help &amp; Support</b><span>Find answers, get assistance, and check how your application is going.</span></div><div class='go'>&rarr;</div></a></div>"
                + "</div></section>"

                // about
                + "<section class='sec about' id='about'><h2>About Us</h2>"
                + "<p>One ID, one profile and all services. Home Affairs confirms who you are once, and every department can rely on it. You can apply, pay, book a time and follow your application from your phone or computer.</p></section>"

                // footer
                + "<footer class='foot'><div class='fb'><div class='flogo'>" + logoImg() + "</div><div><b>Lesotho</b><small>Integrated Government Services System</small></div></div>"
                + "<nav><a href='/'>Home</a><a href='#about'>About Us</a><a href='/public'>Apply without an account</a>"
                + "<a href='/public/track'>Check my application</a><a href='/staff/departments'>Department Official</a></nav>"
                + "<div class='gov'>Lesotho Government<br>Together for a better tomorrow</div></footer>"
                + "</body></html>";
        return Resp.html(html);
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