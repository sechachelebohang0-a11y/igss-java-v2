package lesotho.igss;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last step before a page is sent: small text fixes, the Sesotho translation, the accessibility bar
 * (listen, voice help, speak, bigger text, language buttons) and the rule for name and ID number boxes.
 * Voice is English only. In Sesotho mode the voice buttons are hidden, because no Sesotho voice is available.
 * The Sesotho wording is a first draft. Please have a Sesotho speaker check it, and add more lines with d(...) below.
 */
final class Display {
    private Display() {}

    /** While the page is shown in Sesotho, print every text that has no translation yet (once). Set to false when you are done. */
    private static final boolean LOG_MISSING = true;
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    /** Plain text fixes applied to every page in every language. */
    private static final String[][] TWEAKS = {
            {"a certified copy", "a copy"},
            {"certified copy", "copy"},
            {"<div class='idphoto'>No photo on file</div>",
                    "<a class='idphoto' href='/photo' style='text-decoration:underline;padding:6px'>No photo yet.<br>Tap here to add your photo</a>"}
    };

    private static final Map<String, String> DICT = new HashMap<>();
    private static final List<String[]> PREFIX = new ArrayList<>();
    private static final List<Object[]> RULES = new ArrayList<>();
    private static void d(String en, String st) { DICT.put(en, st); }
    private static void rule(String regex, Function<Matcher, String> f) { RULES.add(new Object[]{Pattern.compile(regex), f}); }

    static {
        PREFIX.add(new String[]{"Welcome back, ", "Rea u amohela hape, "});
        PREFIX.add(new String[]{"Welcome, ", "Rea u amohela, "});

        rule("^Age (\\d+)$", m -> "Lilemo " + m.group(1));
        rule("^Pay M (\\d+) now$", m -> "Lefa M " + m.group(1) + " hona joale");
        rule("^Pay M (\\d+) for (.+)$", m -> "Lefa M " + m.group(1) + " bakeng sa " + inner(m.group(2)));
        rule("^Pay M (\\d+)$", m -> "Lefa M " + m.group(1));
        rule("^Part ([A-Z]) &ndash; (.+)$", m -> "Karolo " + m.group(1) + " &ndash; " + inner(m.group(2)));
        rule("^Application form (.+)$", m -> "Foromo ea kopo " + m.group(1));

        // ---- common
        d("Back", "Khutlela morao"); d("Home", "Lehae"); d("Dashboard", "Leqephe la pele"); d("Login", "Kena"); d("Log out", "Tsoa");
        d("Sign Up", "Ngolisa"); d("Continue", "Tsoela pele"); d("continue", "Tsoela pele"); d("Get Started", "Qala");
        d("Yes", "E"); d("No", "Che"); d("YES", "E"); d("NO", "CHE"); d("Save", "Boloka"); d("Cancel", "Khansela");
        d("Search", "Batla"); d("Upload", "Kenya"); d("Open", "Bula"); d("View", "Sheba"); d("Free", "Mahala");
        d("Choose...", "Khetha..."); d("Choose", "Khetha"); d("Other", "E 'ngoe"); d("Done", "E phethiloe"); d("Next", "E latelang");
        d("In progress", "E ntse e tsoela pele"); d("Remove", "Tlosa"); d("Approve", "Amohela"); d("Refuse", "Hana");
        d("Verify", "Netefatsa"); d("Update status", "Ntlafatsa boemo"); d("Send request", "Romela kopo"); d("Call next", "Bitsa ea latelang");
        d("Check", "Hlahloba"); d("Print this page", "Hatisa leqephe lena"); d("Print this form", "Hatisa foromo ena");
        d("Secure &middot; Simple &middot; Connected", "Polokeho &middot; Bonolo &middot; Kopanelo");
        d("Something went wrong", "Ho bile le bothata"); d("Please go back and try again.", "Ka kopo khutlela morao 'me u leke hape.");
        d("Names can only have letters. ID numbers can only have digits. Go back and fix it.",
                "Mabitso a ka ba le litlhaku feela. Linomoro tsa ID li ka ba le linomoro feela. Khutlela morao 'me u lokise.");

        // ---- statuses
        d("Verified", "E netefalitsoe"); d("Pending", "E emetse"); d("Approved", "E amohetsoe"); d("Rejected", "E hannoe");
        d("Completed", "E phethiloe"); d("Processing", "E ntse e sebetsoa"); d("Paid", "E lefiloe"); d("Booked", "E bokiloe");
        d("Cancelled", "E khanselitsoe"); d("Missing", "E haella"); d("Uploaded", "E kentsoe"); d("Alive", "O phela");
        d("Deceased", "O hlokahetse"); d("Needs info", "E hloka tlhahisoleseling"); d("Payment required", "Ho hlokahala tefo");
        d("Identity verified", "Boitsebiso bo netefalitsoe"); d("Ready to collect", "E loketse ho nkoa"); d("Logged", "E ngotsoe");
        d("Enrolled", "E ngolisitsoe"); d("Past", "E fetile"); d("Sent to Finance", "E rometsoe Lichelete"); d("Due today", "E lokela kajeno");
        d("Due soon", "E lokela haufinyane"); d("Upcoming", "E tlang"); d("Overdue", "E liehile"); d("Waiting for Home Affairs", "E emetse Litaba tsa Ka Hare");
        d("Verified by Home Affairs", "E netefalitsoe ke Litaba tsa Ka Hare"); d("Completed (collected)", "E phethiloe (e nkiloe)");

        // ---- departments and menus
        d("Home Affairs", "Litaba tsa Ka Hare"); d("Traffic Services", "Litšebeletso tsa Literaka"); d("Finance Services", "Litšebeletso tsa Lichelete");
        d("Pensions", "Lipenshene"); d("Police Services", "Litšebeletso tsa Mapolesa"); d("Passport Services", "Litšebeletso tsa Pasepoto");
        d("Traffic", "Literaka"); d("Finance", "Lichelete"); d("Police", "Mapolesa"); d("Passport", "Pasepoto");
        d("Citizen Login", "Ho kena ha Moahi"); d("Create Account", "Bula Akhaonto"); d("Department Official", "Ofisiri ea Lefapha");
        d("Citizen", "Moahi"); d("My Applications", "Likopo tsa ka"); d("My Documents", "Litokomane tsa ka");
        d("My Fingerprints", "Matšoao a menoana a ka"); d("My Photo", "Foto ea ka"); d("My Pension", "Penshene ea ka");
        d("Appointments & Queue", "Likopano le Mola"); d("Notifications", "Litsebiso"); d("Who Accessed My Data", "Ke mang ea sheba lintlha tsa ka");
        d("Profile", "Lintlha tsa ka"); d("Citizen Verification", "Netefatso ea Moahi"); d("Applications", "Likopo");
        d("Appointments / Queue", "Likopano / Mola"); d("Status & Reports", "Boemo le Litlaleho");
        d("Pending Verifications", "Netefatso e Emetseng"); d("Walk-in Applications", "Likopo tsa ba Tlileng ka Sebele");
        d("Document Verification", "Netefatso ea Litokomane"); d("Life Status", "Boemo ba Bophelo"); d("Police Address Requests", "Likopo tsa Aterese ea Mapolesa");
        d("Fees & Receipts", "Litefo le Liresiti"); d("Pension Payments", "Litefo tsa Penshene"); d("Government Workers", "Basebeletsi ba Mmuso");
        d("Pension Register", "Lethathamo la Penshene"); d("Criminal Search", "Patlisiso ea Botlokotsebe");

        // ---- landing page
        d("Departments", "Mafapha"); d("About Us", "Ka Rona"); d("Help & Support", "Thuso le Tšehetso");
        d("One Platform", "Sethala se Le Seng"); d("Many Services", "Litšebeletso Tse Ngata"); d("A Better Lesotho", "Lesotho le Betere");
        d("Lesotho Integrated", "Lesotho e Kopaneng"); d("Government Services System", "Tsamaiso ea Litšebeletso tsa Mmuso");
        d("Integrated Government Services System", "Tsamaiso e Kopaneng ea Litšebeletso tsa Mmuso");
        d("Access government services in one place. Fast, secure and convenient for every citizen, business and government official.",
                "Fumana litšebeletso tsa mmuso sebakeng se le seng. Ka potlako, ka polokeho le ka boiketlo bakeng sa moahi ka mong, khoebo le ofisiri ea mmuso.");
        d("Apply without an account", "Kopa ntle le akhaonto"); d("Secure", "E sireletsehile"); d("Your data is protected", "Lintlha tsa hau lia sireletsoa");
        d("Convenient", "E bonolo"); d("Access anytime, anywhere", "Fumana neng kapa neng, kae kapa kae"); d("For Everyone", "Ho Bohle");
        d("Citizens, businesses & officials", "Baahi, likhoebo le liofisiri"); d("Connected Departments", "Mafapha a Hokahantsoeng");
        d("Better service delivery", "Phano e betere ea litšebeletso");
        d("A connected government for a stronger Lesotho", "Mmuso o hokahaneng bakeng sa Lesotho le matla");
        d("Your Government, One System", "Mmuso oa Hau, Tsamaiso e le 'ngoe");
        d("The Lesotho Integrated Government Services System brings together key government departments to make services more accessible, transparent and efficient for all.",
                "Tsamaiso ena e Kopaneng ea Litšebeletso tsa Mmuso oa Lesotho e kopanya mafapha a bohlokoa a mmuso hore litšebeletso e be tse fumanehang habonolo, tse pepeneneng le tse sebetsang hantle bakeng sa bohle.");
        d("Need help?", "Na u hloka thuso?"); d("Get Support", "Fumana Thuso");
        d("Not sure where to start? Apply without an account, or visit your nearest Home Affairs office.",
                "Ha u tsebe hore na u qale kae? Kopa ntle le akhaonto, kapa u etele ofisi e haufi ea Litaba tsa Ka Hare.");
        d("United in Service, Building a Brighter Future", "Re kopane ts'ebelisong, re haha bokamoso bo khanyang");
        d("Find answers, get assistance, and check how your application is going.", "Fumana likarabo, u fumane thuso, 'me u sheba hore kopo ea hau e tsamaea joang.");
        d("One ID, one profile and all services. Home Affairs confirms who you are once, and every department can rely on it. You can apply, pay, book a time and follow your application from your phone or computer.",
                "ID e le 'ngoe, boitsebiso bo le bong le litšebeletso tsohle. Litaba tsa Ka Hare li netefatsa hore u mang hang, 'me lefapha le leng le le leng le ka itšetleha ka eona. U ka kopa, ua lefa, ua boka nako 'me ua latela kopo ea hau mohaleng kapa khomphuteng ea hau.");
        d("Check my application", "Sheba kopo ea ka"); d("Lesotho Government", "Mmuso oa Lesotho");
        d("Together for a better tomorrow", "Hammoho bakeng sa kamoso e betere");
        d("Lesotho Integrated Government Services System", "Tsamaiso e Kopaneng ea Litšebeletso tsa Mmuso oa Lesotho");
        d("One ID. All Services. A Better Lesotho", "ID e le 'ngoe. Litšebeletso tsohle. Lesotho le Betere");

        // ---- login, register, role screens
        d("Create Citizen Account", "Bula Akhaonto ea Moahi"); d("Citizen Login", "Ho kena ha Moahi");
        d("Home Affairs checks your details once. After that every department can confirm who you are.",
                "Litaba tsa Ka Hare li hlahloba lintlha tsa hau hang. Kamora moo lefapha le leng le le leng le ka netefatsa hore u mang.");
        d("Welcome back. Log in to use all government services with one profile.",
                "Rea u amohela hape. Kena ho sebelisa litšebeletso tsohle tsa mmuso ka boitsebiso bo le bong.");
        d("Full name", "Lebitso le felletseng"); d("ID number", "Nomoro ea ID"); d("Staff ID", "ID ea mosebeletsi");
        d("Date of birth", "Letsatsi la tsoalo"); d("Date of birth (yyyy-mm-dd)", "Letsatsi la tsoalo (yyyy-mm-dd)");
        d("Phone number", "Nomoro ea mohala"); d("Phone", "Mohala"); d("Phone (optional)", "Mohala (ha u rata)");
        d("Email", "Emeile"); d("Email address", "Aterese ea emeile"); d("Address", "Aterese");
        d("Password", "Nomoro ea lekunutu"); d("Gender", "Bong"); d("Sex", "Bong"); d("Male", "Monna"); d("Female", "Mosali");
        d("Wrong ID number or password.", "Nomoro ea ID kapa nomoro ea lekunutu ha e nepahale.");
        d("Wrong staff ID or password.", "ID ea mosebeletsi kapa nomoro ea lekunutu ha e nepahale.");
        d("No Home Affairs record found for that ID. Please visit your nearest Home Affairs office.",
                "Ha ho rekoto ea Litaba tsa Ka Hare e fumanoeng bakeng sa ID eo. Ka kopo etela ofisi e haufi ea Litaba tsa Ka Hare.");
        d("This ID already has an account. Please log in.", "ID ena e se e na le akhaonto. Ka kopo kena.");
        d("Enter your date of birth as yyyy-mm-dd.", "Kenya letsatsi la hau la tsoalo ka mokhoa oa yyyy-mm-dd.");
        d("The date of birth does not match the Home Affairs record.", "Letsatsi la tsoalo ha le tsamaellane le rekoto ea Litaba tsa Ka Hare.");
        d("Choose a password of at least 6 characters.", "Khetha nomoro ea lekunutu e nang le litlhaku tse 6 kapa ho feta.");
        d("Login / Sign Up", "Kena / Ngolisa"); d("Choose your role to continue", "Khetha seabo sa hau ho tsoela pele");
        d("your personal services", "litšebeletso tsa hau"); d("Access department dashboard", "Fihlella leqephe la lefapha");
        d("Already have an account?", "Na u se u na le akhaonto?"); d("Do you already have an account?", "Na u se u na le akhaonto?");
        d("Login to your account", "Kena akhaontong ea hau"); d("Create a new account", "Bula akhaonto e ncha");
        d("Department Official Login", "Ho kena ha Ofisiri ea Lefapha"); d("Staff only. Every action is logged.", "Basebeletsi feela. Ketso ka 'ngoe e ngotsoe.");
        d("Select Department", "Khetha Lefapha"); d("Choose the department you want to access", "Khetha lefapha leo u batlang ho le fihlella");
        d("Your account is waiting for Home Affairs to confirm your name. You cannot apply yet.", "Akhaonto ea hau e emetse hore Litaba tsa Ka Hare li netefatse lebitso la hau. Ha u so kope.");
        d("Your account is waiting for Home Affairs to confirm your name.", "Akhaonto ea hau e emetse hore Litaba tsa Ka Hare li netefatse lebitso la hau.");
        d("Welcome! Your identity was verified once by Home Affairs.", "Rea u amohela! Boitsebiso ba hau bo netefalitsoe hang ke Litaba tsa Ka Hare.");

        // ---- citizen pages
        d("RECENT UPDATES", "LITSEBISO TSA MOHLA"); d("PERSONAL DETAILS", "LINTLHA TSA HAU"); d("ACCOUNT", "AKHAONTO");
        d("MY APPOINTMENTS", "LIKOPANO TSA KA"); d("MY QUEUE NUMBERS", "LINOMORO TSA KA TSA MOLA");
        d("JOIN THE QUEUE NOW", "KENA MOLAONG HONA JOALE"); d("BOOK A TIME", "BOKA NAKO");
        d("EVERY DEPARTMENT RELIES ON THIS ONE RECORD", "LEFAPHA LE LENG LE LE LENG LE ITŠETLEHA KA REKOTO ENA E LE 'NGOE");
        d("Your verified profile. Home Affairs confirmed it once.", "Boitsebiso ba hau bo netefalitsoeng. Litaba tsa Ka Hare li bo netefalitse hang.");
        d("Life status (Home Affairs)", "Boemo ba bophelo (Litaba tsa Ka Hare)"); d("Account status", "Boemo ba akhaonto");
        d("Photo", "Foto"); d("Fingerprints", "Matšoao a menoana"); d("Pension", "Penshene"); d("Not on record", "Ha e ngotsoe"); d("Not provided", "Ha e fanoe");
        d("No photo yet", "Ha ho foto hajoale"); d("Not scanned yet", "Ha ea skenoa"); d("Not on the pension register", "Ha e lethathamong la penshene");
        d("Receiving a pension", "O fumana penshene");
        d("Add or change my photo", "Kenya kapa fetola foto ea ka"); d("Scan my fingerprints", "Skena menoana ea ka");
        d("Upload a document", "Kenya tokomane"); d("See who accessed my data", "Sheba hore na ke mang ea sheba lintlha tsa ka");
        d("Pick a service. Your details are filled in for you.", "Khetha tšebeletso. Lintlha tsa hau li se li tletse.");
        d("Tap an application to see where it is.", "Hinotha kopo ho bona hore e kae.");
        d("No applications yet.", "Ha ho likopo hajoale."); d("No appointments yet.", "Ha ho likopano hajoale."); d("No queue numbers yet.", "Ha ho linomoro tsa mola hajoale.");
        d("Updates from every department in one place.", "Litsebiso tsa mafapha 'ohle sebakeng se le seng.");
        d("Every look at your profile is written down.", "Ho sheba ka 'ngoe ha lintlha tsa hau ho ngotsoe.");
        d("Nobody has looked at your profile yet.", "Ha ho motho ea ntseng a sheba lintlha tsa hau.");
        d("Upload each document once. Every department can reuse it.", "Kenya tokomane ka 'ngoe hang feela. Lefapha le leng le le leng le ka e sebelisa hape.");
        d("UPLOAD OR REPLACE A DOCUMENT", "KENYA KAPA NKELA TOKOMANE SEBAKA"); d("What is it?", "Ke eng?");
        d("Choose a file from your computer (JPG, PNG or PDF, up to 5 MB)", "Khetha faele khomphuteng ea hau (JPG, PNG kapa PDF, ho fihla ho 5 MB)");
        d("paper record", "rekoto ea pampiri");
        d("Passport photo", "Foto ea pasepoto"); d("National ID copy", "Kopi ea ID ea naha"); d("Birth certificate", "Setifikeiti sa tsoalo");
        d("Proof of residence", "Bopaki ba bolulo"); d("Marriage certificate", "Setifikeiti sa lenyalo"); d("Medical certificate", "Setifikeiti sa bongaka");
        d("Vehicle papers", "Lipampiri tsa koloi"); d("Death notification", "Tsebiso ea lefu"); d("Bank details", "Lintlha tsa banka"); d("Old passport", "Pasepoto ea khale");
        d("Your profile picture goes in My Photo", "Setšoantšo sa hau sa lebitso se ea ho Foto ea ka");
        d("Your photo is used on your profile and on every form you send.", "Foto ea hau e sebelisoa lintlhang tsa hau le foromong e 'ngoe le e 'ngoe eo u e romellang.");
        d("ADD MY PHOTO", "KENYA FOTO EA KA"); d("REPLACE MY PHOTO", "NKELA FOTO EA KA SEBAKA"); d("Save photo", "Boloka foto");
        d("Upload a photo", "Kenya foto"); d("Take a photo", "Nka foto"); d("Capture", "Nka"); d("No photo yet", "Ha ho foto hajoale");
        d("Face the camera in good light, with a plain light background. No hat or sunglasses. JPG or PNG, up to 5 MB.",
                "Sheba khamera ka khanya e ntle, u e-na le morao o bonolo o khanyang. Ha ho katiba kapa liglase tsa letsatsi. JPG kapa PNG, ho fihla ho 5 MB.");
        d("Your photo is saved", "Foto ea hau e bolokiloe");
        d("Your fingerprints are kept by Home Affairs and tied to your ID number.", "Matšoao a menoana a hau a bolokoa ke Litaba tsa Ka Hare 'me a kopantsoe le nomoro ea hau ea ID.");
        d("Who can use them", "Ke bo mang ba ka a sebelisang");
        d("SCAN MY FINGERPRINT", "SKENA MONOANA OA KA"); d("SCAN AGAIN", "SKENA HAPE"); d("Scan my fingerprint", "Skena monoana oa ka");
        d("You have not scanned your fingerprints yet.", "Ha u so skene menoana ea hau.");
        d("Demo scanner: no real device is connected. A sample fingerprint pattern is saved so the rest of the system can be used and tested.",
                "Skena ea mohlala: ha ho sesebelisoa sa 'nete se hokahaneng. Paterone ea mohlala ea monoana e bolokoa hore tsamaiso e setseng e sebelisoe 'me e leko.");
        d("Upload a fingerprint image instead", "Kenya setšoantšo sa monoana sebakeng sa seo");
        d("Upload fingerprints", "Kenya matšoao a menoana"); d("Replace fingerprints", "Nkela matšoao a menoana sebaka");
        d("Your pension. Pensions are for citizens aged 60 and above.", "Penshene ea hau. Lipenshene ke tsa baahi ba lilemo tse 60 le ho feta.");
        d("You can apply", "U ka kopa"); d("Apply for a pension", "Kopa penshene"); d("Not yet", "Ha ho so");
        d("You receive a pension", "U fumana penshene"); d("MY PENSION PAYMENTS", "LITEFO TSA KA TSA PENSHENE");
        d("You worked in government", "U sebelitse mmusong"); d("Make a retirement benefit claim", "Etsa kopo ea melemo ea ho tlohela mosebetsi");
        d("Book an appointment at Pensions", "Boka kopano Lipenshene");
        d("Choose how you want to be served: join the line now, or book a time and skip the wait. To make an ID or a driving licence, book an appointment first.",
                "Khetha hore na u rata ho sebeletsoa joang: kena molaong hona joale, kapa u boke nako 'me u qobe ho ema. Ho etsa ID kapa laesense ea ho khanna, boka kopano pele.");
        d("You get a queue number. Come to the office and wait for your number to be called.", "U fumana nomoro ea mola. Tla ofising 'me u emele hore nomoro ea hau e bitsoe.");
        d("Offices are open Monday to Friday, 08:00 to 16:00.", "Liofisi lia buleha ho tloha Mantaha ho isa Labohlano, 08:00 ho isa 16:00.");
        d("Get my queue number", "Fumana nomoro ea ka ea mola"); d("Book appointment", "Boka kopano"); d("Book an appointment", "Boka kopano");
        d("Book an appointment to make an ID", "Boka kopano ho etsa ID"); d("Book an appointment to make a driving licence", "Boka kopano ho etsa laesense ea ho khanna");
        d("Book an appointment for this application", "Boka kopano bakeng sa kopo ena");
        d("Department", "Lefapha"); d("Service", "Tšebeletso"); d("Date", "Letsatsi"); d("Time", "Nako");
        d("Pick an idea or type your own", "Khetha mohopolo kapa u ngole oa hau");
        d("Make a national ID card", "Etsa karete ea ID ea naha"); d("Renew my ID card", "Ntlafatsa karete ea ka ea ID");
        d("Make a driving licence", "Etsa laesense ea ho khanna"); d("Renew my driving licence", "Ntlafatsa laesense ea ka ea ho khanna");
        d("Collect a document", "Nka tokomane"); d("General enquiry", "Potso ea kakaretso");
        d("Needs a booked appointment.", "E hloka kopano e bokiloeng.");

        // ---- applications and tracking
        d("Application Tracking", "Ho Latela Kopo"); d("You are here", "U mona"); d("Pay Fee", "Lefa Tefo"); d("Application", "Kopo");
        d("Collect it here", "E nke mona"); d("Note from the officer", "Molaetsa ho tsoa ho ofisiri"); d("View the form I filled in", "Sheba foromo eo ke e tlatsitseng");
        d("Application sent", "Kopo e rometsoe"); d("Fee paid", "Tefo e lefiloe"); d("Documents checked", "Litokomane li hlahlobiloe");
        d("Being processed", "E ntse e sebetsoa"); d("Ready to collect", "E loketse ho nkoa"); d("Collected", "E nkiloe");
        d("Rejected", "E hannoe"); d("The officer needs more information", "Ofisiri e hloka tlhahisoleseling e ngata");
        d("This is a prototype. No real money moves. Finance Services records the payment and issues the receipt.", "Ena ke mohlala. Ha ho chelete ea 'nete e fetisoang. Litšebeletso tsa Lichelete li ngola tefo 'me li fana ka resiti.");
        d("Pay with", "Lefa ka"); d("Mobile money", "Chelete ea mohala"); d("Bank card", "Karete ea banka"); d("Bank transfer", "Phetiso ea banka");
        d("Phone or card number", "Nomoro ea mohala kapa ea karete");
        d("Fingerprint scanned and saved.", "Monoana o skenoe 'me o bolokiloe.");

        // ---- services (names, descriptions, categories)
        d("NATIONAL ID", "ID EA NAHA"); d("BIRTH", "TSOALO"); d("MARRIAGE", "LENYALO"); d("DEATH", "LEFU"); d("PERSONAL RECORDS", "LIREKOTO TSA MOTHO");
        d("DRIVING", "HO KHANNA"); d("VEHICLES", "LIKOLOI"); d("PERMITS", "LIPHAROLO"); d("TAX", "THAKSE"); d("REFUNDS", "HO PHELISA CHELETE");
        d("PAYMENTS", "LITEFO"); d("PENSION", "PENSHENE"); d("REPORTS", "LITLAHALO"); d("PASSPORT", "PASEPOTO"); d("TRAVEL", "HO SOTHA");
        d("ID Replacement (lost or damaged)", "Ho nkela ID sebaka (e lahlehileng kapa e senyehileng)");
        d("Replace a lost, stolen or damaged ID card.", "Nkela karete ea ID e lahlehileng, e utsoitsoeng kapa e senyehileng sebaka.");
        d("ID Renewal", "Ho ntlafatsa ID"); d("Renew an ID card that is expiring.", "Ntlafatsa karete ea ID e haufi le ho fela.");
        d("ID Details Correction", "Ho lokisa lintlha tsa ID"); d("Correct a wrong name, date of birth or other detail.", "Lokisa lebitso le fosahetseng, letsatsi la tsoalo kapa lintlha tse ling.");
        d("Birth Registration", "Ho ngolisa tsoalo"); d("Register the birth of a child.", "Ngolisa tsoalo ea ngoana.");
        d("Marriage Registration", "Ho ngolisa lenyalo"); d("Register a civil marriage.", "Ngolisa lenyalo la mmuso.");
        d("Certificate of No Impediment", "Setifikeiti sa ho se be le sethibelo"); d("Proof that you are free to marry.", "Bopaki ba hore u lokolohile ho nyala.");
        d("Death Registration", "Ho ngolisa lefu"); d("Register a death in the family.", "Ngolisa lefu lelapeng.");
        d("Change of Names", "Phetoho ea lebitso"); d("Change your legal names.", "Fetola lebitso la hau la molao.");
        d("Driving Licence Renewal", "Ho ntlafatsa laesense ea ho khanna"); d("Renew your driving licence.", "Ntlafatsa laesense ea hau ea ho khanna.");
        d("Licence Replacement", "Ho nkela laesense sebaka"); d("Replace a lost or damaged licence.", "Nkela laesense e lahlehileng kapa e senyehileng sebaka.");
        d("Vehicle Registration", "Ho ngolisa koloi"); d("Register a vehicle in your name.", "Ngolisa koloi ka lebitso la hau.");
        d("Change of Ownership", "Phetoho ea beng ba koloi"); d("Change the owner of a vehicle.", "Fetola mong a koloi.");
        d("Roadworthy Certificate", "Setifikeiti sa ho loka tseleng"); d("Certificate that a vehicle is safe to drive.", "Setifikeiti sa hore koloi e bolokehile ho khannoa.");
        d("D Permit (public transport)", "Pharolo ea D (lipalangoang tsa sechaba)"); d("Permit to carry passengers for payment.", "Pharolo ea ho palama bapalami ka tefo.");
        d("Tax Clearance Certificate", "Setifikeiti sa ho hlakisoa ha thakse"); d("Proof that your taxes are in order.", "Bopaki ba hore lithakse tsa hau li nepahetse.");
        d("Refund Request", "Kopo ea ho khutlisetsoa chelete"); d("Ask for a refund of money paid in error.", "Kopa ho khutlisetsoa chelete e lefiloeng ka phoso.");
        d("Payment Receipt Copy", "Kopi ea resiti ea tefo"); d("Get a copy of a lost payment receipt.", "Fumana kopi ea resiti ea tefo e lahlehileng.");
        d("Old-Age Grant Application", "Kopo ea Thuso ea Botsofali"); d("Retirement Pension Claim", "Kopo ea Penshene ea ho Tlohela Mosebetsi");
        d("Report a Lost Document", "Tlaleha Tokomane e Lahlehileng"); d("Report a lost ID, licence or passport.", "Tlaleha ID, laesense kapa pasepoto e lahlehileng.");
        d("New Passport", "Pasepoto e Ncha"); d("Apply for your first passport.", "Kopa pasepoto ea hau ea pele.");
        d("Passport Renewal", "Ho ntlafatsa pasepoto"); d("Renew a passport that is expiring.", "Ntlafatsa pasepoto e haufi le ho fela.");
        d("Lost or Stolen Passport", "Pasepoto e Lahlehileng kapa e Utsoitsoeng"); d("Replace a lost or stolen passport.", "Nkela pasepoto e lahlehileng kapa e utsoitsoeng sebaka.");
        d("Emergency Travel Document", "Tokomane ea Tšohanyetso ea ho Sotha"); d("Urgent travel document.", "Tokomane ea potlako ea ho sotha.");
        d("First-time ID Card", "Karete ea ID ea Pele"); d("Birth Certificate Copy", "Kopi ea Setifikeiti sa Tsoalo");

        // ---- application form chrome and common fields
        d("KINGDOM OF LESOTHO", "BOMOROA BA LESOTHO"); d("FOR OFFICIAL USE ONLY", "BAKENG SA TSEBELISO EA LIOFISIRI FEELA");
        d("Applicant", "Moapari"); d("Declaration", "Boitlamo"); d("Documents to attach", "Litokomane tse tla kenngoa"); d("Photograph", "Foto");
        d("Where you live", "Moo u lulang"); d("Village / Town", "Motse / Toropo"); d("Chief / Ward / Area", "Morena / Moaparo / Sebaka");
        d("District", "Setereke"); d("Postal address (P.O. Box)", "Aterese ea poso (P.O. Box)");
        d("Please fix the following:", "Ka kopo lokisa tse latelang:"); d("Send application", "Romela kopo"); d("Submit report", "Romela tlaleho"); d("Submit application", "Romela kopo");
        d("I agree with the declaration above.", "Ke lumellana le boitlamo bo ka holimo.");
        d("I declare that everything on this form is true and complete. I know that false information can lead to this application being turned down.",
                "Ke bolela hore tsohle foromong ena ke 'nete 'me lia phethahala. Ke tseba hore tlhahisoleseling ea bohata e ka etsa hore kopo ena e hanoe.");
        d("Signature: type your full name as it appears at the top of this form", "Matsoho: ngola lebitso la hau le felletseng kamoo le hlahang kateng ka holimo ho foromo ena");
        d("Applicant: ", "Moapari: ");
        d("These details come from Home Affairs. They are filled in for you and cannot be changed here.", "Lintlha tsena li tsoa Litabeng tsa Ka Hare. Li se li tletse 'me ha li ka fetoloa mona.");
        d("Address on the register", "Aterese ho register"); d("Choose your photo and documents again.", "Khetha foto le litokomane tsa hau hape.");
        d("Already on file. You can leave this empty to use it, or choose a new one.", "E se e ntse e bolokiloe. U ka e tlohela e se na letho ho e sebelisa, kapa u khethe e ncha.");
        d("JPG, PNG or PDF, up to 5 MB.", "JPG, PNG kapa PDF, ho fihla ho 5 MB.");
        d("A photo is already on file and will be used. Add a new one only if you want to replace it.", "Foto e se e ntse e bolokiloe 'me e tla sebelisoa. Kenya e ncha feela haeba u batla ho e nkela sebaka.");
        d("Chief's area (chieftainship)", "Sebaka sa morena"); d("Reason", "Lebaka"); d("Surname", "Fane"); d("First names", "Mabitso a pele");
        d("Place of birth", "Sebaka sa tsoalo"); d("Occupation", "Mosebetsi"); d("Marital status", "Boemo ba lenyalo");
        d("Single", "Ha ke nyala"); d("Married", "Ke nyetsoe"); d("Widowed", "Mohlolo"); d("Divorced", "Ke hlalane"); d("Separated", "Re arohane");
        d("Mother's full name", "Lebitso le felletseng la mme"); d("Father's full name", "Lebitso le felletseng la ntate");
        d("Nationality", "Naha"); d("Lost", "E lahlehile"); d("Stolen", "E utsoitsoe"); d("Damaged", "E senyehile");
        d("Did you report this to the police?", "Na u tlalehile taba ena ho mapolesa?"); d("Police report", "Tlaleho ea mapolesa");
        d("Police station (if Yes)", "Seteishene sa mapolesa (haeba ke E)"); d("Police report (OB) number (if Yes)", "Nomoro ea tlaleho ea mapolesa (OB) (haeba ke E)");
        d("Bank", "Banka"); d("Account holder's name", "Lebitso la mong'a akhaonto"); d("Account number", "Nomoro ea akhaonto"); d("Branch", "Lekala");
        d("Next of kin", "Beng ka uena"); d("Relationship to you", "Kamano le uena"); d("Last employer", "Mohiri oa ho qetela"); d("Job title", "Lebitso la mosebetsi");

        // ---- staff and department pages
        d("Welcome", "Rea u amohela"); d("Applications for", "Likopo tsa");
        d("Find a citizen without an ID card or a visit. You only see the fields your department needs.", "Batla moahi ntle le karete ea ID kapa ho etela. U bona likarolo tseo lefapha la hau le li hlokang feela.");
        d("Verify identity", "Netefatsa boitsebiso"); d("Reason for access", "Lebaka la ho fihlella"); d("Name", "Lebitso");
        d("Fingerprint code (for example FP-1001)", "Khoutu ea monoana (mohlala FP-1001)"); d("THIS DEPARTMENT CAN SEE", "LEFAPHA LENA LE KA BONA"); d("NOT SHARED", "HA E BAKANNE");
        d("New status", "Boemo bo bocha"); d("Note to citizen (optional)", "Molaetsa ho moahi (ha u rata)"); d("Processing", "E ntse e sebetsoa");
        d("Collection office (used with Ready to collect)", "Ofisi ea ho nka (e sebelisoa le E loketse ho nkoa)"); d("DOCUMENTS (opening one is logged)", "LITOKOMANE (ho bula e 'ngoe ho ngotsoe)");
        d("Application form", "Foromo ea kopo"); d("WAITING IN THE QUEUE", "BA EMETSENG MOLAONG"); d("BOOKED APPOINTMENTS", "LIKOPANO TSE BOKILOENG");
        d("Nobody is waiting.", "Ha ho motho ea emetseng."); d("No upcoming appointments.", "Ha ho likopano tse tlang.");
        d("ON THE PENSION REGISTER", "LETHATHAMONG LA PENSHENE"); d("Send to Finance", "Romela Lichelete"); d("Invite to apply", "Memela ho kopa");
        d("GOVERNMENT WORKERS (GOVERNMENT PENSION, NO OLD-AGE PENSION)", "BASEBELETSI BA MMUSO (PENSHENE EA MMUSO, HA HO PENSHENE EA BOTSOFALI)");
        d("STOPPED (HOME AFFAIRS REPORTS DEATH)", "E KHETHILOE (LITABA TSA KA HARE LI TLALEHA LEFU)");
        d("Mark paid", "Tšoaea e lefiloe"); d("Mark as government worker", "Tšoaea e le mosebeletsi oa mmuso"); d("GOVERNMENT WORKERS", "BASEBELETSI BA MMUSO");
        d("Ministry or department (optional)", "Lekala kapa lefapha (ha u rata)"); d("Case number", "Nomoro ea nyeoe");
        d("Reason for needing the address", "Lebaka la ho hloka aterese"); d("ASK HOME AFFAIRS FOR THE ADDRESS", "KOPA LITABA TSA KA HARE ATERESE");
        d("MY ADDRESS REQUESTS", "LIKOPO TSA KA TSA ATERESE"); d("WAITING FOR YOUR DECISION", "LI EMETSE SECHABA SA HAU"); d("RECENT DECISIONS", "MABAKA A MOHLA");
        d("Confirm identity", "Netefatsa boitsebiso"); d("Register death", "Ngolisa lefu"); d("Documents, photos and fingerprints uploaded by citizens. Open one to check it, then verify it.", "Litokomane, lifoto le matšoao a menoana a kentsoeng ke baahi. Bula e 'ngoe ho e hlahloba, ebe oa e netefatsa.");
        d("No documents are waiting.", "Ha ho litokomane tse emetseng."); d("No accounts are waiting.", "Ha ho liakhaonto tse emetseng.");
        d("Service fees collected", "Litefo tsa litšebeletso tse bokelletsoeng"); d("Pensions paid out", "Lipenshene tse lefiloeng");
        d("No fees paid yet.", "Ha ho litefo tse lefiloeng."); d("Nobody is marked yet.", "Ha ho motho ea tšoailoeng."); d("No requests yet.", "Ha ho likopo hajoale.");
        d("No requests are waiting.", "Ha ho likopo tse emetseng.");

        // ---- walk-in (no account) pages
        d("Apply without an account", "Kopa ntle le akhaonto"); d("National ID card", "Karete ea ID ea naha"); d("Register a birth", "Ngolisa tsoalo");
        d("Birth certificate copy", "Kopi ea setifikeiti sa tsoalo"); d("Check my application", "Sheba kopo ea ka"); d("Application received", "Kopo e amohetsoe");
        d("Reference number", "Nomoro ea tšupiso"); d("Tracking code", "Khoutu ea ho latela"); d("Submitted", "E rometsoe");
        d("Enter the reference number and tracking code you were given when you applied.", "Kenya nomoro ea tšupiso le khoutu ea ho latela tseo u li filoeng ha u kopa.");
        d("Received", "E amohetsoe"); d("Note from Home Affairs:", "Molaetsa ho tsoa Litabeng tsa Ka Hare:");
    }

    // ------------------------------------------------------------------ translation engine
    private static final Pattern TEXT = Pattern.compile(">([^<>]+)<");
    private static final Pattern BLOCK = Pattern.compile("(?is)<(style|script)\\b.*?</\\1>");
    private static final Pattern ATTR = Pattern.compile("(placeholder|aria-label|title|alt)='([^']*)'");
    private static final String[] PEEL_FRONT = {"&lsaquo; ", "&#10003; ", "! ", "&#128274; "};
    private static final String PEEL_BACK = " &rarr;";

    private static String escOut(String s) { return s.replace("&", "&amp;").replace("'", "&#39;"); }

    /** Translates a piece of text that the page already escaped. Returns the escaped Sesotho, or null when there is none. */
    private static String core(String t) {
        if (t.endsWith(" - IGSS Lesotho")) {
            String in = core(t.substring(0, t.length() - " - IGSS Lesotho".length()));
            return in == null ? null : in + " - IGSS Lesotho";
        }
        for (String p : PEEL_FRONT)
            if (t.startsWith(p)) { String in = core(t.substring(p.length())); return in == null ? null : p + in; }
        if (t.endsWith(PEEL_BACK)) { String in = core(t.substring(0, t.length() - PEEL_BACK.length())); return in == null ? null : in + PEEL_BACK; }
        String key = t.replace("&amp;", "&").replace("&#39;", "'");
        String out = DICT.get(key);
        if (out != null) return escOut(out);
        for (String[] p : PREFIX) if (key.startsWith(p[0])) return escOut(p[1]) + t.substring(p[0].length());
        for (Object[] r : RULES) {
            Matcher m = ((Pattern) r[0]).matcher(t);
            if (m.matches()) {
                @SuppressWarnings("unchecked") Function<Matcher, String> f = (Function<Matcher, String>) r[1];
                return f.apply(m);
            }
        }
        if (LOG_MISSING && key.length() >= 3 && key.length() <= 200 && key.matches(".*\\p{L}.*") && !key.matches(".*[{};=<>].*") && MISSING.add(key))
            System.out.println("[untranslated] " + key);
        return null;
    }

    private static String inner(String t) { String o = core(t); return o == null ? t : o; }

    private static String tr(String text) {
        String t = text.strip();
        if (t.isEmpty()) return text;
        String lead = text.substring(0, text.length() - text.stripLeading().length());
        String trail = text.substring(text.stripTrailing().length());
        String out = core(t);
        return out == null ? text : lead + out + trail;
    }

    private static String translateSegment(String html) {
        Matcher m = TEXT.matcher(html);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(html, last, m.start(1)).append(tr(m.group(1)));
            last = m.end(1);
        }
        out.append(html.substring(last));
        Matcher a = ATTR.matcher(out.toString());
        StringBuilder res = new StringBuilder();
        while (a.find()) {
            String o = core(a.group(2).strip());
            a.appendReplacement(res, Matcher.quoteReplacement(a.group(1) + "='" + (o == null ? a.group(2) : o) + "'"));
        }
        a.appendTail(res);
        return res.toString();
    }

    /** Translates the page but leaves every style and script block alone. */
    static String translate(String html) {
        Matcher b = BLOCK.matcher(html);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (b.find()) {
            out.append(translateSegment(html.substring(last, b.start()))).append(b.group());
            last = b.end();
        }
        return out.append(translateSegment(html.substring(last))).toString();
    }

    // ------------------------------------------------------------------ names and ID numbers
    private static final Pattern NAME_KEY = Pattern.compile("(^|_)(names?|surname|first|maiden|mother|father|spouse|seller)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKIP_KEY = Pattern.compile("place|facility", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_KEY = Pattern.compile("(^|_)id$|^idconfirm$|^idnumber$", Pattern.CASE_INSENSITIVE);

    /** Server-side check of every submitted form. Returns a message when a name has digits or symbols, or an ID number has anything but digits. */
    /** Pages where every ID number box holds a citizen's ID, which must have exactly 11 digits. */
    private static final java.util.Set<String> CITIZEN_ID_PAGES = java.util.Set.of("/register", "/login", "/staff/search", "/staff/govt", "/staff/police",
            "/staff/cases", "/staff/case", "/staff/lost", "/staff/flags");

    private static String boxName(String key) {
        if (ID_KEY.matcher(key).find()) return "ID number";
        String s = key.replace('_', ' ').trim();
        return s.isEmpty() ? "This box" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String badInput(Req r) {
        boolean strict = CITIZEN_ID_PAGES.contains(r.path);
        for (Map.Entry<String, String> e : r.p.entrySet()) {
            String k = e.getKey(), v = e.getValue() == null ? "" : e.getValue();
            if (ID_KEY.matcher(k).find()) {
                if (!v.matches("[0-9 \\-]*")) return "Wrong ID number. An ID number can only have digits (0 to 9), no letters or symbols. Example: 04123457678.";
                String d = v.replace(" ", "").replace("-", "");
                if (strict && !d.isEmpty() && d.length() != 11) return "Wrong ID number. A Lesotho ID number has exactly 11 digits. You typed " + d.length() + ". Example: 04123457678.";
            }
            if (NAME_KEY.matcher(k).find() && !SKIP_KEY.matcher(k).find() && !v.matches("[\\p{L} '\u2019.\\-]*"))
                return "Wrong " + boxName(k).toLowerCase() + ". A name can only have letters, no numbers or symbols like @ # 1 2 3. Go back and fix it.";
        }
        return null;
    }

    // ------------------------------------------------------------------ the bar at the bottom of every page
    /** Called by Main for every HTML page. */
    static String process(String html, String lang, String uri) {
        boolean st = lang.equals("st");
        String out = html;
        for (String[] t : TWEAKS) out = out.replace(t[0], t[1]);
        if (st) out = translate(out).replace("<html lang='en'>", "<html lang='st'>");
        return out.contains("</body>") ? out.replace("</body>", toolbar(st, uri) + "</body>") : out;
    }

    private static final String BAR_CSS = """
            #a11y{position:fixed;left:0;right:0;bottom:0;z-index:9999;background:#0a2a8f;display:flex;flex-wrap:wrap;gap:6px;justify-content:center;padding:8px 8px calc(8px + env(safe-area-inset-bottom))}
            #a11y button,#a11y a{background:#fff;color:#0a2a8f;border:0;border-radius:16px;padding:8px 13px;font:700 13px Arial,Helvetica,sans-serif;cursor:pointer;text-decoration:none}
            #a11y a.on{background:#2f6fdc;color:#fff}
            #a11y button:focus-visible,#a11y a:focus-visible{outline:3px solid #ffd54a}
            @media print{#a11y{display:none!important}}
            label.bad{color:#b3261e!important}
            label.bad input,label.bad select,label.bad textarea{border:2px solid #b3261e!important;color:#b3261e!important}
            .fe{display:flex;gap:8px;align-items:flex-start;color:#b3261e;font:600 13px/1.4 Arial,Helvetica,sans-serif;margin:-6px 2px 12px}
            .fe i{flex:none;width:18px;height:18px;border-radius:50%;background:#b3261e;color:#fff;font:800 12px/18px Arial,sans-serif;text-align:center;font-style:normal}
            """;

    /** Names take letters only, ID numbers take digits only. A wrong entry turns the box red with a message under it. */
    private static final String RESTRICT_JS = """
            (function(){
            var NAME=/(^|_)(names?|surname|first|maiden|mother|father|spouse|seller)$/i, SKIP=/place|facility/i, IDN=/(^|_)id$|^idconfirm$|^idnumber$/i;
            function wrap(el){ return el.closest('label')||el.parentNode; }
            function show(el,msg){
              var w=wrap(el), m=w.nextElementSibling;
              if(!m||!m.classList.contains('fe')){ m=document.createElement('div'); m.className='fe'; m.setAttribute('role','alert'); w.parentNode.insertBefore(m,w.nextSibling); }
              m.innerHTML='<i>!</i><span></span>'; m.lastChild.textContent=msg;
              w.classList.add('bad'); el.setAttribute('aria-invalid','true');
            }
            function clear(el){
              var w=wrap(el), m=w.nextElementSibling;
              if(m&&m.classList.contains('fe')) m.remove();
              w.classList.remove('bad'); el.removeAttribute('aria-invalid'); el._charErr=false;
            }
            function problem(el){
              var n=el.name||'', v=(el.value||'').trim();
              if(IDN.test(n)){ var d=v.replace(/[ -]/g,''); if(d&&d.length!==11) return 'Wrong ID number. A Lesotho ID number has exactly 11 digits. You typed '+d.length+'. Example: 04123457678.'; }
              if(n==='password'&&location.pathname==='/register'&&v&&v.length<6) return 'Choose a password of at least 6 characters.';
              if(n==='dob'&&v&&!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(v)) return 'Wrong date. Enter the date of birth as yyyy-mm-dd, for example 1999-01-31.';
              return '';
            }
            document.addEventListener('input',function(e){
              var el=e.target; if(!el||el.tagName!=='INPUT'||(el.type!=='text'&&el.type!=='password')) return;
              var n=el.name||'', v=el.value, w=v, msg='';
              if(IDN.test(n)){ w=v.replace(/[^0-9]/g,'').slice(0,11); if(v.replace(/[ -]/g,'').replace(/[0-9]/g,'')!=='') msg='Wrong ID number. An ID number can only have digits (0 to 9). Letters and symbols are not allowed.'; }
              else if(NAME.test(n)&&!SKIP.test(n)){ w=v.replace(/[^\\p{L} '\\u2019.\\-]/gu,''); if(w!==v) msg='Wrong name. A name can only have letters. Numbers and symbols like @ # 1 2 3 are not allowed.'; }
              if(w!==v) el.value=w;
              if(msg){ show(el,msg); el._charErr=true; }
              else if(!el._charErr){ var p=problem(el); if(!p) clear(el); else if(el.classList.contains('bad')||wrap(el).classList.contains('bad')) show(el,p); }
            });
            document.addEventListener('focusout',function(e){
              var el=e.target; if(!el||el.tagName!=='INPUT'||(el.type!=='text'&&el.type!=='password')) return;
              var p=problem(el); if(p) show(el,p); else clear(el);
            });
            document.addEventListener('submit',function(e){
              var f=e.target, first=null; if(!f||!f.querySelectorAll) return;
              f.querySelectorAll('input[type=text],input[type=password]').forEach(function(el){
                var p=problem(el); if(p){ show(el,p); if(!first) first=el; }
              });
              if(first){ e.preventDefault(); e.stopImmediatePropagation(); first.focus(); }
            },true);
            document.addEventListener('focusin',function(e){ var el=e.target;
              if(el&&el.tagName==='INPUT'&&el.type==='text'&&IDN.test(el.name||'')) el.setAttribute('inputmode','numeric'); });
            })();
            """;

    private static final String BIG_JS = """
            (function(){ var b=document.getElementById('a-big'), z=1;
              b.onclick=function(){ z=z>=1.4?1:z+0.2; document.body.style.zoom=z; }; })();
            """;

    /** English voice only: read the page, voice help for each box, and speaking into a box. */
    private static final String VOICE_JS = """
            (function(){
            var synth=window.speechSynthesis, SR=window.SpeechRecognition||window.webkitSpeechRecognition;
            var $=function(i){return document.getElementById(i);};
            function voice(){ if(!synth) return null; var vs=synth.getVoices(); for(var j=0;j<vs.length;j++){ if(vs[j].lang.toLowerCase().indexOf('en')===0) return vs[j]; } return null; }
            function chunks(t){ var parts=t.replace(/[\\r\\n]+/g,' ').replace(/ +/g,' ').match(/[^.!?]+[.!?]*/g)||[t]; var out=[],cur='';
              parts.forEach(function(p){ if((cur+p).length>170&&cur){out.push(cur);cur=p;}else{cur+=p;} }); if(cur) out.push(cur); return out; }
            function say(t){ if(!synth){alert('Your browser cannot speak. Try Chrome or Edge.');return;} synth.cancel(); var v=voice();
              chunks(t).forEach(function(c){ var u=new SpeechSynthesisUtterance(c); u.lang=v?v.lang:'en-GB'; if(v)u.voice=v; u.rate=0.95; synth.speak(u); }); }
            function pageText(){ var root=document.querySelector('.body')||document.querySelector('.sheet')||document.querySelector('.phone')||document.querySelector('.wrap')||document.body;
              var h=document.querySelector('h1'); return (h?h.innerText+'. ':'')+root.innerText; }
            var vh=false; try{ vh=localStorage.getItem('igss.vh')==='1'; }catch(e){}
            function paint(){ $('a-vh').textContent=vh?'Voice help: on':'Voice help: off'; $('a-vh').setAttribute('aria-pressed',vh); }
            $('a-vh').onclick=function(){ vh=!vh; try{ localStorage.setItem('igss.vh',vh?'1':'0'); }catch(e){} paint(); if(vh) say('Voice help: on'); };
            paint();
            var last=null;
            document.addEventListener('focusin',function(e){ var el=e.target; if(!el||!el.matches) return;
              if(el.matches('input:not([type=hidden]):not([type=file]):not([type=checkbox]):not([type=radio]),textarea')) last=el;
              if(vh&&el.matches('input,select,textarea,button,a')){ var lab=el.closest('label');
                var txt=(lab?lab.innerText:(el.innerText||el.getAttribute('aria-label')||el.value||'')).split('\\n')[0]; if(txt) say(txt); } });
            })();
            """;

    private static final String AUTOFILL_JS = """
            (function(){var M={mother_id:{mother_name:'name',mother_dob:'dob'},father_id:{father_name:'name'},inf_id:{inf_name:'name'}};
            Object.keys(M).forEach(function(k){var el=document.querySelector('[name='+k+']');if(!el)return;
              var go=function(){var v=el.value.replace(/[^0-9]/g,'');if(v.length!==11)return;
                fetch('/lookup?id='+v).then(function(x){return x.json();}).then(function(d){if(!d.name)return;
                  Object.keys(M[k]).forEach(function(t){var f=document.querySelector('[name='+t+']');
                    if(f&&(!f.value||f.dataset.auto==='1')){f.value=d[M[k][t]];f.dataset.auto='1';f.dispatchEvent(new Event('input',{bubbles:true}));}});}).catch(function(){});};
              el.addEventListener('input',go);el.addEventListener('change',go);});})();
            """;
    static String toolbar(boolean st, String uri) {
        String next = URLEncoder.encode(uri, StandardCharsets.UTF_8);
        StringBuilder b = new StringBuilder("<style>" + BAR_CSS + "</style><div style='height:72px'></div>"
                + "<div id='a11y' role='toolbar' aria-label='" + (st ? "Thuso" : "Help") + "'>");
        if (!st) b.append("<button type='button' id='a-vh' aria-pressed='false'></button>");
        b.append("<button type='button' id='a-big'>").append(st ? "Mongolo o moholo" : "Bigger text").append("</button>")
                .append("<a href='/lang?l=en&next=").append(next).append("'").append(st ? "" : " class='on'").append(">English</a>")
                .append("<a href='/lang?l=st&next=").append(next).append("'").append(st ? " class='on'" : "").append(">Sesotho</a></div>")
                .append("<script>").append(RESTRICT_JS).append(AUTOFILL_JS).append(BIG_JS).append(st ? "" : VOICE_JS).append("</script>");
        return b.toString();
    }
}