package lesotho.igss;

import lesotho.igss.Models.Application;
import lesotho.igss.Models.Citizen;
import lesotho.igss.Models.Dept;
import lesotho.igss.Models.Doc;
import lesotho.igss.Models.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static lesotho.igss.FormKit.*;

/**
 * Paper-style application forms for every service in the citizen dashboard (all departments).
 *   /apply?dept=XX&service=...   GET shows the form, POST checks it and sends the application
 *   /form/view?id=...            the citizen's own printable copy of the form they sent
 * The details Home Affairs already holds are printed at the top (Part A) and cannot be changed here.
 * The answers are kept in the form_answers table; staff see them inside the application card (answersHtml).
 */
final class ServiceForms {
    private ServiceForms() {}

    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final class Form {
        final String code, intro;
        final List<Sec> secs;
        Form(String code, String intro, List<Sec> secs) { this.code = code; this.intro = intro; this.secs = secs; }
    }

    private static final Map<String, Form> FORMS = new LinkedHashMap<>();

    private static void reg(String service, String code, String intro, Sec... secs) { FORMS.put(service, new Form(code, intro, List.of(secs))); }

    // ------------------------------------------------------------------ pieces used by many forms
    private static Sec residence(String title) {
        return new Sec(title, txt("village", "Village / Town", true), txt("chief", "Chief / Ward / Area", false),
                selList("district", "District", true, DISTRICTS), area("postal", "Postal address (P.O. Box)", false));
    }

    private static Fld[] police() {
        return new Fld[]{yn("police", "Did you report this to the police?", true),
                txt("police_station", "Police station (if Yes)", false).when("police", "Yes"),
                txt("police_ob", "Police report (OB) number (if Yes)", false).when("police", "Yes")};
    }

    private static final String[] LICENCE_CLASSES = {"Motorcycle", "Light motor vehicle (car)", "Heavy motor vehicle (truck)", "Bus / public transport"};

    private static Sec licenceHealth() {
        return new Sec("Health declaration", yn("glasses", "Do you wear glasses or contact lenses?", true), yn("disability", "Do you have a disability or medical condition that affects driving?", true),
                area("condition", "If Yes, describe it", false).when("disability", "Yes"));
    }

    private static Sec banking(String title, boolean needed) {
        return new Sec(title, selList("bank", "Bank", needed, BANKS), txt("acc_name", "Account holder's name", needed), idnum("acc_no", "Account number", needed), txt("branch", "Branch", false));
    }

    static {
        // ---------------------------------------------------------------- HOME AFFAIRS
        reg("First-time ID Card", "HA/ID-1", "Use this form to apply for your first national identity card. You must be 16 or older and your birth must already be registered. There is no fee. Apply here on the system. Home Affairs checks the form, then tells you which office to collect the card from. Your fingerprints are taken when you collect it.",
                new Sec("Your birth details", txt("birth_place", "Place of birth (village or town)", true), selList("birth_district", "District of birth", true, DISTRICTS),
                        fd("nationality", "Nationality", "text", true, "Mosotho"), sel("marital", "Marital status", true, MARITAL.toArray(new String[0])),
                        txt("occupation", "Occupation", false)),
                new Sec("Your parents", txt("mother_name", "Mother's full name (with her maiden surname)", true), idnum("mother_id", "Mother's ID number", false),
                        txt("father_name", "Father's full name", false), idnum("father_id", "Father's ID number", false)),
                residence("Where you live"),
                new Sec("Informant (chief or councillor)", txt("chief_name", "Name of your chief or councillor", true), tel("chief_tel", "Their phone number", false),
                        txt("chief_area", "Area they serve", false)));

        reg("ID Replacement (lost or damaged)", "HA/ID-2", "Use this form to replace a national identity card that was lost, stolen or damaged. There is no fee.",
                new Sec("About the card", sel("reason", "What happened to the card?", true, "Lost", "Stolen", "Damaged"),
                        dt("lost_date", "Date it was lost, stolen or damaged", true), area("how", "Where and how did it happen?", true)),
                new Sec("Police report", police()),
                new Sec("Your old card", idnum("card_no", "Card number (printed on the card, if you remember it)", false), txt("mother_name", "Mother's full name", true), txt("father_name", "Father's full name", false)),
                residence("Where you live"),
                new Sec("Informant (chief or councillor)", txt("chief_name", "Name of your chief or councillor", true), tel("chief_tel", "Their phone number", false),
                        txt("chief_area", "Area they serve", false)));

        reg("ID Renewal", "HA/ID-3", "Use this form to renew a national identity card that is expiring. There is no fee.",
                new Sec("About the card", idnum("card_no", "Card number (printed on the card)", false), dtf("expiry", "Date the card expires", true),
                        sel("reason", "Reason for renewal", true, "Card has expired", "Card will expire soon", "My appearance has changed", "Card is worn out")),
                new Sec("Your parents", txt("mother_name", "Mother's full name", true), txt("father_name", "Father's full name", false)),
                residence("Where you live"));

        reg("ID Details Correction", "HA/ID-4", "Use this form when a detail on your identity card or in the register is wrong. There is no fee.",
                new Sec("The mistake", sel("what", "Which detail is wrong?", true, "Surname", "First names", "Date of birth", "Place of birth", "Gender", "Other"),
                        txt("wrong", "What the card or register says now", true), txt("right", "What it should say", true),
                        area("reason", "Why was it wrong? Say how the mistake happened", true)),
                new Sec("Proof of the right detail", sel("proof", "Which document shows the right detail?", true, "Birth certificate", "Passport", "Marriage certificate", "Court order", "Other"),
                        txt("proof_no", "Number on that document", false)),
                residence("Where you live"));

        reg("Birth Registration", "HA/BR-1", "Use this form to register the birth of a child born in Lesotho.",
                new Sec("The child", txt("child_first", "Child's first names", true), txt("child_surname", "Child's surname", true), dt("child_dob", "Date of birth", true),
                        tm("child_time", "Time of birth", false), sel("child_gender", "Sex", true, "Male", "Female"),
                        sel("place_type", "Where was the child born?", true, "Hospital or clinic", "At home", "Other"), txt("facility", "Name of the hospital, clinic or place", true),
                        txt("birth_village", "Village / Town of birth", true), selList("birth_district", "District of birth", true, DISTRICTS)),
                new Sec("The mother", txt("mother_name", "Mother's full name", true), idnum("mother_id", "Mother's ID number", false), dt("mother_dob", "Mother's date of birth", false),
                        fd("mother_nat", "Mother's nationality", "text", false, "Mosotho"), sel("mother_marital", "Mother's marital status", true, MARITAL.toArray(new String[0]))),
                new Sec("The father", txt("father_name", "Father's full name", false), idnum("father_id", "Father's ID number", false), fd("father_nat", "Father's nationality", "text", false, "Mosotho"),
                        yn("parents_married", "Were the parents married to each other?", true)),
                new Sec("Your connection", sel("relation", "Who are you to the child?", true, "Mother", "Father", "Grandparent", "Other relative", "Hospital or clinic official", "Guardian")));

        reg("Marriage Registration", "HA/MR-1", "Use this form to register a marriage. The applicant is one of the two people marrying. Both people and two witnesses must come to the Home Affairs office on the day.",
                new Sec("The groom", txt("groom_name", "Full name of the groom", true), idnum("groom_id", "ID number", true), dt("groom_dob", "Date of birth", true),
                        sel("groom_status", "Status before the marriage", true, "Single", "Widowed", "Divorced"), txt("groom_job", "Occupation", false), txt("groom_village", "Village / Town", true)),
                new Sec("The bride", txt("bride_name", "Full name of the bride", true), idnum("bride_id", "ID number", true), dt("bride_dob", "Date of birth", true),
                        sel("bride_status", "Status before the marriage", true, "Single", "Widowed", "Divorced"), txt("bride_job", "Occupation", false), txt("bride_village", "Village / Town", true)),
                new Sec("The marriage", dtf("wedding_date", "Date of the marriage", true), txt("wedding_place", "Place of the marriage", true), selList("wedding_district", "District", true, DISTRICTS),
                        sel("rite", "Type of marriage", true, "Civil", "Church (Christian rites)"),
                        sel("property", "Property", true, "In community of property", "Out of community of property")),
                new Sec("Witnesses", txt("w1_name", "Witness 1: full name", true), idnum("w1_id", "Witness 1: ID number", true),
                        txt("w2_name", "Witness 2: full name", true), idnum("w2_id", "Witness 2: ID number", true)));

        reg("Certificate of No Impediment", "HA/MR-2", "Use this form to ask for proof that you are free to marry. It is usually needed for a marriage outside Lesotho.",
                new Sec("Your status", sel("status", "Your marital status", true, "Single", "Widowed", "Divorced"),
                        txt("prev_spouse", "Name of your late or former spouse (if widowed or divorced)", false).when("status", "Widowed", "Divorced"),
                        dt("prev_end", "Date the last marriage ended (if widowed or divorced)", false).when("status", "Widowed", "Divorced")),
                new Sec("The person you will marry", txt("partner_name", "Full name", true), txt("partner_nat", "Nationality", true), txt("partner_doc", "ID or passport number", true)),
                new Sec("The marriage", txt("country", "Country where the marriage will take place", true), dtf("planned", "Planned date of the marriage", false),
                        area("purpose", "Who asked for this certificate (embassy, church or office)?", false)));

        reg("Death Registration", "HA/DR-1", "Use this form to register a death. Attach the death notification from the hospital, clinic or chief. There is no fee.",
                new Sec("The person who died", txt("dead_name", "Full name", true), idnum("dead_id", "ID number", false), dt("dead_dob", "Date of birth", false),
                        sel("dead_gender", "Sex", true, "Male", "Female"), dt("death_date", "Date of death", true), tm("death_time", "Time of death", false),
                        sel("death_place", "Where did the person die?", true, "Hospital or clinic", "At home", "Other"), txt("death_place_name", "Name of the hospital, clinic or place", true),
                        txt("death_village", "Village / Town", true), selList("death_district", "District", true, DISTRICTS),
                        area("cause", "Cause of death (from the medical certificate, if you have it)", false)),
                new Sec("Your connection", sel("relation", "Who are you to the person who died?", true, "Spouse", "Child", "Parent", "Brother or sister", "Other relative", "Chief or village official"),
                        dtf("burial_date", "Date of the burial", false), txt("burial_place", "Place of the burial", false)));

        reg("Change of Names", "HA/CN-1", "Use this form to change your legal names.",
                new Sec("Your new name", txt("new_first", "New first names", true), txt("new_surname", "New surname", true),
                        sel("reason", "Reason for the change", true, "Marriage", "Divorce", "Personal choice", "Spelling was wrong", "Other"),
                        area("reason_detail", "Tell us more about the reason", true), yn("changed_before", "Have you changed your name before?", true)),
                residence("Where you live"));

        // ---------------------------------------------------------------- TRAFFIC (Learner's Licence and Driving Licence (new) were removed)
        reg("Learner's Licence", "TR/LL-1", "Use this form to apply for a learner's licence. You must be 16 or older. After the learner's test, Traffic Services tells you the result and where to collect the licence.",
                new Sec("The licence you want", sel("class", "Class of vehicle", true, LICENCE_CLASSES), selList("test_centre", "Where do you want to take the learner's test?", true, DISTRICTS),
                        dtf("pref_date", "Preferred date for the test", false)),
                licenceHealth(), residence("Where you live"));

        reg("Driving Licence (new)", "TR/DL-1", "Use this form to apply for your first driving licence. You need an approved learner's licence. After the driving test, Traffic Services tells you the result and where to collect the licence.",
                new Sec("Your learner's licence", txt("learner_no", "Learner's licence number", true), sel("class", "Class of vehicle", true, LICENCE_CLASSES)),
                new Sec("The driving test", txt("school", "Driving school (if you went to one)", false), selList("test_centre", "Where do you want to take the driving test?", true, DISTRICTS),
                        dtf("pref_date", "Preferred date for the test", false)),
                residence("Where you live"));

        reg("Driving Licence Renewal", "TR/DL-2", "Use this form to renew a driving licence.",
                new Sec("Your licence", txt("licence_no", "Licence number", true), dtf("expiry", "Date it expires", true), sel("class", "Class of vehicle", true, LICENCE_CLASSES)),
                licenceHealth(), residence("Where you live"));

        reg("Licence Replacement", "TR/DL-3", "Use this form to replace a driving licence that was lost, stolen or damaged.",
                new Sec("Your licence", txt("licence_no", "Licence number (if you know it)", false), sel("reason", "What happened?", true, "Lost", "Stolen", "Damaged"),
                        dt("lost_date", "Date it happened", true), area("how", "Where and how did it happen?", true)),
                new Sec("Police report", police()));

        reg("Vehicle Registration", "TR/VR-1", "Use this form to register a vehicle in your name.",
                new Sec("The vehicle", txt("make", "Make (for example Toyota)", true), txt("model", "Model", true), year("year", "Year of manufacture", true), txt("colour", "Colour", true),
                        sel("body", "Type of vehicle", true, "Sedan", "Hatchback", "SUV", "Pick-up (bakkie)", "Truck", "Bus or minibus", "Motorcycle", "Other"),
                        sel("fuel", "Fuel", true, "Petrol", "Diesel", "Electric", "Hybrid"), txt("engine_no", "Engine number", true), txt("chassis_no", "Chassis number (VIN)", true),
                        sel("use", "Use of the vehicle", true, "Private", "Business", "Public transport")),
                new Sec("How you got it", txt("seller", "Name of the seller or dealer", true), dt("bought", "Date you bought it", true), num("price", "Price paid (Maloti)", false),
                        sel("origin", "Where does the vehicle come from?", true, "Bought in Lesotho", "Imported"), txt("customs", "Customs clearance number (if imported)", false).when("origin", "Imported")),
                residence("Where the vehicle is kept"));

        reg("Change of Ownership", "TR/VR-2", "Use this form to put a vehicle into your name. You are the new owner.",
                new Sec("The vehicle", txt("plate", "Registration number (number plate)", true), txt("make", "Make", true), txt("model", "Model", true), year("year", "Year of manufacture", true)),
                new Sec("The seller (current owner)", txt("seller", "Seller's full name", true), idnum("seller_id", "Seller's ID number", true), tel("seller_tel", "Seller's phone number", false)),
                new Sec("The sale", sel("how", "How did you get the vehicle?", true, "Bought", "Gift", "Inheritance"), dt("sale_date", "Date of the sale or hand-over", true), num("price", "Price paid (Maloti)", false)),
                residence("Where the vehicle will be kept"));

        reg("Roadworthy Certificate", "TR/RW-1", "Use this form to book an inspection for a roadworthy certificate.",
                new Sec("The vehicle", txt("plate", "Registration number (number plate)", true), txt("make", "Make", true), txt("model", "Model", true), year("year", "Year of manufacture", true),
                        num("odometer", "Kilometres on the clock", false)),
                new Sec("The inspection", sel("purpose", "Why do you need the certificate?", true, "Registering the vehicle", "Renewing the disc", "Change of owner", "Other"),
                        selList("station", "Inspection station", true, DISTRICTS), dtf("pref_date", "Preferred date", false)));

        reg("D Permit (public transport)", "TR/DP-1", "Use this form to apply for a permit to carry passengers for payment.",
                new Sec("Your licence", txt("licence_no", "Driving licence number", true), sel("class", "Class of licence", true, LICENCE_CLASSES), num("years", "Years of driving experience", true)),
                new Sec("The vehicle", txt("plate", "Registration number (number plate)", true), txt("make", "Make", true), txt("model", "Model", true), num("seats", "Number of passenger seats", true)),
                new Sec("The service", area("route", "Route or area where you will work", true), txt("rank", "Taxi rank or base", false),
                        yn("offence", "Have you ever been convicted of a traffic offence?", true), area("offence_detail", "If Yes, give details", false).when("offence", "Yes")));

        // ---------------------------------------------------------------- FINANCE
        reg("Tax Clearance Certificate", "FI/TC-1", "Use this form to ask for proof that your taxes are in order.",
                new Sec("Your tax details", txt("tin", "Taxpayer number (if you have one)", false),
                        sel("work", "Your work", true, "Employed", "Self-employed", "Pensioner", "Not working"), txt("employer", "Employer or business name", false).when("work", "Employed", "Self-employed")),
                new Sec("The certificate", sel("purpose", "Why do you need it?", true, "Tender or contract", "Licence or permit", "Travel", "Bank or loan", "Other"),
                        year("from_year", "Period needed: from year", true), year("to_year", "Period needed: to year", true), txt("asked_by", "Who asked for it?", false)));

        reg("Refund Request", "FI/RF-1", "Use this form to ask for money back that you paid in error. There is no fee.",
                new Sec("The payment", txt("receipt", "Receipt number", true), dt("paid_date", "Date you paid", true), num("amount", "Amount paid (Maloti)", true), txt("paid_for", "What did you pay for?", true),
                        area("reason", "Why should the money be paid back?", true)),
                banking("Where to pay the refund", true));

        reg("Payment Receipt Copy", "FI/RC-1", "Use this form to get a copy of a receipt you lost. There is no fee.",
                new Sec("The payment", txt("receipt", "Receipt number (if you know it)", false), dt("paid_date", "Date you paid", true), num("amount", "Amount paid (Maloti)", true),
                        txt("paid_for", "What did you pay for?", true), sel("method", "How did you pay?", true, "M-Pesa", "EcoCash", "Bank card", "Bank transfer", "Cash at an office")));

        // ---------------------------------------------------------------- PENSIONS
        reg("Old-Age Grant Application", "PE/OA-1", "Use this form to apply for the monthly old-age grant. You must be aged " + Policy.PENSION_AGE + " or above.",
                new Sec("Confirm who you are", idnum("idconfirm", "Type your ID number again", true)),
                new Sec("Your household", sel("marital", "Marital status", true, MARITAL.toArray(new String[0])), txt("spouse", "Spouse's full name (if married)", false).when("marital", "Married"),
                        num("household", "How many people live in your home?", true), yn("other_grant", "Do you receive another pension or government grant?", true)),
                residence("Where you live"),
                new Sec("How should the grant be paid?", sel("pay_method", "Payment method", true, "Bank account", "Lesotho PostBank", "M-Pesa", "EcoCash", "Cash at the pay point"),
                        tel("mobile_no", "Your M-Pesa or EcoCash phone number", false).when("pay_method", "M-Pesa", "EcoCash"),
                        selList("bank", "Bank (if paid into an account)", false, BANKS).when("pay_method", "Bank account"), idnum("acc_no", "Account number (if paid into an account)", false).when("pay_method", "Bank account", "Lesotho PostBank")),
                new Sec("Next of kin", txt("kin_name", "Full name", true), txt("kin_rel", "Relationship to you", true), tel("kin_tel", "Phone number", false)));

        reg("Retirement Pension Claim", "PE/RB-1", "Use this form to claim your retirement pension. You must be aged " + Policy.PENSION_AGE + " or above.",
                new Sec("Confirm who you are", idnum("idconfirm", "Type your ID number again", true)),
                new Sec("Your work", txt("employer", "Last employer", true), txt("job", "Job title", true), dt("start", "Date you started", true), dt("end", "Date you stopped", true),
                        sel("reason", "Reason for leaving", true, "Retirement", "Ill health", "Redundancy", "Other"), num("salary", "Last monthly salary (Maloti)", false)),
                banking("Where to pay the pension", true));

        // ---------------------------------------------------------------- POLICE
        reg("Police Clearance Certificate", "PO/PC-1", "Use this form to ask for a certificate that shows your police record.",
                new Sec("About you", txt("birth_place", "Place of birth", true), txt("father", "Father's full name", false), txt("mother", "Mother's full name", false)),
                new Sec("The certificate", sel("purpose", "Why do you need it?", true, "Job", "Visa or immigration", "Study", "Travel", "Adoption", "Other"),
                        txt("for_whom", "Country or organisation that asked for it", true)),
                new Sec("Declaration of record", yn("abroad", "Have you lived outside Lesotho for more than 6 months in the last 5 years?", true),
                        txt("abroad_where", "If Yes, which countries?", false).when("abroad", "Yes"),
                        yn("convicted", "Have you ever been convicted of a crime?", true), area("conv_detail", "If Yes, give details", false).when("convicted", "Yes")));

        reg("Report a Robbery", "PO/RB-1", "Use this form to report a robbery. If someone is in danger now, call the police first. Keep the reference number. There is no fee.",
                new Sec("The robbery", sel("robbery_kind", "Type of robbery", true, "Armed robbery", "Street robbery (mugging)", "House robbery", "Shop or business robbery", "Vehicle hijacking", "Other"),
                        dt("when", "Date it happened", true), f("time", "Time it happened (if you know it)", "time", false), txt("place", "Place where it happened", true),
                        selList("station", "Nearest police station (district)", true, DISTRICTS)),
                new Sec("What happened", area("how", "Tell us what happened", true), yn("weapon", "Was a weapon used?", true), txt("weapon_what", "If Yes, what kind of weapon?", false).when("weapon", "Yes"),
                        yn("injured", "Were you or anyone else hurt?", true), area("taken", "What was taken? (items and their value in Maloti)", true)),
                new Sec("The robber", yn("suspect", "Can you describe or name the robber?", true), area("suspect_desc", "Describe the robber (clothes, height, name, vehicle)", false).when("suspect", "Yes")));

        reg("Report an Incident", "PO/IN-1", "Use this form to report a crime or an incident that is not a robbery. Keep the reference number. There is no fee.",
                new Sec("The incident", sel("incident", "Type of incident", true, "Theft", "Break-in", "Assault", "Vehicle theft", "Stock theft", "Fraud", "Threats or harassment", "Missing person", "Other"),
                        dt("when", "Date it happened", true), txt("place", "Place where it happened", true), selList("station", "Nearest police station (district)", true, DISTRICTS)),
                new Sec("What happened", area("how", "Tell us what happened", true), area("witness", "Names of any witnesses (if you know them)", false)));

        reg("Report a Lost Document", "PO/LR-1", "Use this form to report a lost or stolen document. Keep the reference number. There is no fee.",
                new Sec("The document", sel("doc", "Which document?", true, "National ID card", "Passport", "Driving licence", "Vehicle registration book", "Other"),
                        txt("doc_no", "Document number (if you know it)", false), sel("kind", "Was it lost or stolen?", true, "Lost", "Stolen")),
                new Sec("What happened", dt("lost_date", "Date it happened", true), txt("place", "Place where it happened", true), area("how", "Tell us what happened", true),
                        selList("station", "Nearest police station (district)", true, DISTRICTS)));

        // ---------------------------------------------------------------- PASSPORT
        reg("New Passport", "PA/NP-1", "Use this form to apply for your first passport.",
                new Sec("About you", txt("birth_place", "Place of birth", true), sel("marital", "Marital status", true, MARITAL.toArray(new String[0])), txt("job", "Occupation", false),
                        num("height", "Height (cm)", false), sel("eyes", "Colour of eyes", false, "Brown", "Black", "Blue", "Green", "Grey", "Hazel")),
                new Sec("Your parents", txt("father", "Father's full name", true), txt("mother", "Mother's full name", true), txt("maiden", "Mother's maiden name", false)),
                new Sec("The passport", sel("pages", "Number of pages", true, "32 pages", "48 pages"), txt("destination", "Where do you plan to travel first?", false)),
                residence("Where you live"),
                new Sec("Guarantor (someone who has known you for at least 2 years)", txt("g_name", "Full name", true), idnum("g_id", "ID number", true), tel("g_tel", "Phone number", true),
                        txt("g_job", "Occupation", false), num("g_years", "Years they have known you", true)));

        reg("Passport Renewal", "PA/PR-1", "Use this form to renew a passport that is expiring or full.",
                new Sec("Your old passport", txt("old_no", "Passport number", true), dt("old_issue", "Date it was issued", true), dtf("old_expiry", "Date it expires", true),
                        sel("reason", "Reason for renewal", true, "Expired", "About to expire", "No pages left", "Damaged", "Details changed")),
                new Sec("The new passport", sel("pages", "Number of pages", true, "32 pages", "48 pages")),
                residence("Where you live"));

        reg("Lost or Stolen Passport", "PA/LS-1", "Use this form to replace a passport that was lost or stolen. A police report is needed.",
                new Sec("The lost passport", txt("old_no", "Passport number (if you know it)", false), sel("kind", "Was it lost or stolen?", true, "Lost", "Stolen"),
                        dt("lost_date", "Date it happened", true), txt("place", "Place where it happened", true), area("how", "Tell us what happened", true)),
                new Sec("Police report", txt("police_station", "Police station where you reported it", true), txt("police_ob", "Police report (OB) number", true), dt("police_date", "Date you reported it", true)),
                new Sec("The new passport", sel("pages", "Number of pages", true, "32 pages", "48 pages")),
                residence("Where you live"));

        reg("Emergency Travel Document", "PA/ET-1", "Use this form when you must travel urgently and cannot get a normal passport in time.",
                new Sec("The journey", txt("destination", "Where are you going?", true), dtf("travel_date", "Date you must travel", true), dtf("return_date", "Date you will come back", false),
                        sel("reason", "Why are you travelling?", true, "Medical", "Funeral", "Business", "Study", "Other"), area("reason_detail", "Tell us more. Why is it urgent?", true)),
                new Sec("Your passport", sel("passport", "Why do you need an emergency document?", true, "I have no passport", "My passport was lost or stolen", "My passport has expired")),
                new Sec("Someone we can contact", txt("kin_name", "Full name", true), tel("kin_tel", "Phone number", true)));
    }

    /** Creates the table for the answers. Call once at start-up, after Store.init. */
    static void init() {
        Db.exec("create table if not exists form_answers(app_id integer,ord integer,section text,label text,value text)");
    }

    // ------------------------------------------------------------------ page look: a paper form
    private static final String CSS = """
        *{box-sizing:border-box}
        [hidden]{display:none!important}
        html,body{margin:0}
        body{background:#dfe6ee;font-family:'Segoe UI',Arial,Helvetica,sans-serif;color:#1f2937}
        a{text-decoration:none;color:inherit}
        .wrap{max-width:760px;margin:0 auto;padding:14px 12px 28px}
        .top a{color:#00209f;font-weight:800;font-size:14px;display:inline-block;padding:4px 0 10px}
        .sheet{background:#fff;border:1px solid #6b7280;padding:22px 20px 18px;box-shadow:0 2px 8px #0002}
        .head{text-align:center;border-bottom:3px double #1f2937;padding-bottom:12px;margin-bottom:14px}
        .crest{font:700 13px Georgia,serif;letter-spacing:.2em;color:#00209f}
        .motto{font:italic 12px Georgia,serif;color:#475569;margin:2px 0 6px}
        .dept{font-weight:800;font-size:13px;text-transform:uppercase;letter-spacing:.04em}
        h1{font:700 19px Georgia,serif;margin:8px 0 4px;text-transform:uppercase}
        .meta{font-size:11.5px;color:#475569}
        p.in{font-size:13px;line-height:1.45;margin:0 0 14px}
        .part{border:1.5px solid #1f2937;margin:0 0 14px;padding:0}
        .part h2{margin:0;background:#e8edf7;border-bottom:1.5px solid #1f2937;padding:6px 10px;font-size:13px;text-transform:uppercase;letter-spacing:.03em;color:#00209f}
        .pb{padding:4px 12px 6px;display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));column-gap:16px}
        .pb .full{grid-column:1/-1}
        .known{padding:8px 12px 10px;font-size:13px}
        .known table{width:100%;border-collapse:collapse}
        .known td{border-bottom:1px dotted #9ca3af;padding:5px 4px;vertical-align:top}
        .known td:first-child{width:38%;color:#475569;font-size:12px}
        .idrow{display:flex;gap:14px;align-items:flex-start}
        .idrow table{flex:1}
        .idphoto{width:96px;height:120px;object-fit:cover;border:1.5px solid #1f2937;flex:none;background:#f3f4f6;display:flex;align-items:center;justify-content:center;font-size:11px;color:#6b7280;text-align:center}
        label.f{display:flex;flex-direction:column;gap:4px;font-size:12px;font-weight:700;color:#1f2937;margin:8px 0 6px}
        label.f i{color:#c0262d;font-style:normal}
        input,select,textarea{width:100%;border:0;border-bottom:1.5px solid #1f2937;border-radius:0;padding:8px 4px;font:15px 'Segoe UI',Arial,sans-serif;background:#f8fafc;color:#0a2a8f}
        input:focus,select:focus,textarea:focus{outline:2px solid #1a4fb4;background:#fff}
        input[type=file]{padding:8px 4px;font-size:13px;background:#fff}
        small{color:#5f6f8c;font-size:11.5px;font-weight:400;line-height:1.35}
        .btn{display:block;width:100%;border:0;border-radius:10px;background:#1a4fb4;color:#fff;font:700 15px 'Segoe UI',Arial,sans-serif;padding:15px;text-align:center;cursor:pointer;margin-top:6px}
        .btn:hover{background:#143f91}
        .sm{border:1.5px solid #1a4fb4;background:#fff;color:#1a4fb4;border-radius:18px;padding:8px 14px;font:700 13px 'Segoe UI',Arial,sans-serif;cursor:pointer;display:inline-block}
        .err,.ok{padding:10px 14px;font-size:13px;font-weight:700;margin:0 0 14px;border:2px solid}
        .ok{background:#e6f4ea;color:#14532d;border-color:#14532d}
        .err{background:#fff;color:#9b1c1c;border-color:#9b1c1c}
        .err ul{margin:6px 0 0;padding-left:18px;font-weight:600}
        .chk{display:flex;gap:10px;align-items:flex-start;font-size:13px;margin:10px 0 6px}
        .chk input{width:auto;margin-top:3px}
        .ph{width:140px;height:176px;border:2px dashed #9db4e6;background:#f6f9ff;display:flex;align-items:center;justify-content:center;overflow:hidden;margin:8px 0;font-size:12px;text-align:center;color:#5f6f8c}
        .ph img{width:100%;height:100%;object-fit:cover}
        video{width:100%;max-width:320px;background:#000;transform:scaleX(-1);margin:8px 0;display:block}
        .row{display:flex;gap:8px;flex-wrap:wrap;margin:4px 0}
        .official{border:1.5px dashed #6b7280;padding:8px 12px;font-size:11.5px;color:#6b7280;margin-top:6px}
        .official div{border-bottom:1px solid #d1d5db;height:20px}
        .ans td:first-child{width:42%}
        .acts{display:flex;gap:10px;flex-wrap:wrap;margin:12px 0 0}
        .acts a,.acts button{flex:1;min-width:130px;text-align:center}
        .foot{text-align:center;color:#00209f;font-weight:700;font-size:11.5px;margin:18px 0 2px}
        @media print{.noprint{display:none!important}body{background:#fff}.sheet{border:0;box-shadow:none;padding:0}.wrap{padding:0}}
        """;

    private static Resp shell(String title, String body, boolean script) {
        return Resp.html("<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + Html.esc(title) + " - IGSS Lesotho</title><style>" + CSS + "</style></head><body><div class='wrap'>" + body
                + "<div class='foot noprint'>Secure &middot; Simple &middot; Connected</div></div>"
                + (script ? "<script>" + CAMERA_JS + "</script>" : "") + "</body></html>");
    }

    private static String head(Policy.Service sv, Form fm) {
        return "<div class='head'><div class='crest'>KINGDOM OF LESOTHO</div><div class='motto'>Khotso &middot; Pula &middot; Nala</div>"
                + "<div class='dept'>" + Html.esc(sv.dept.label) + "</div><h1>" + Html.esc(sv.name) + "</h1>"
                + "<div class='meta'>" + (sv.dept == lesotho.igss.Models.Dept.PO ? "Report form " : "Application form ") + Html.esc(fm.code) + " &middot; Fee: " + (sv.fee == 0 ? "<b>none (free)</b>" : "M " + sv.fee) + "</div></div>";
    }

    /** The details Home Affairs holds, with the citizen's photo (or a note when there is none). */
    private static String known(Citizen me) {
        Doc ph = Store.docOf(me.id, "Passport photo");
        String pic = ph != null && ph.stored != null && ph.mime != null && ph.mime.startsWith("image/")
                ? "<img class='idphoto' src='/file?id=" + ph.id + "' alt='Your photo'>"
                : "<div class='idphoto'>No photo on file</div>";
        return "<div class='known'><div class='idrow'><table>"
                + "<tr><td>Full name</td><td><b>" + Html.esc(me.name) + "</b></td></tr>"
                + "<tr><td>ID number</td><td>" + Html.esc(me.id) + "</td></tr>"
                + "<tr><td>Date of birth</td><td>" + me.dob.format(LONG) + "</td></tr>"
                + "<tr><td>Sex</td><td>" + Html.esc(me.gender == null ? "" : me.gender) + "</td></tr>"
                + "<tr><td>Address on the register</td><td>" + Html.esc(me.address == null ? "" : me.address) + "</td></tr>"
                + "</table>" + pic + "</div><small>These details come from Home Affairs. They are filled in for you and cannot be changed here.</small></div>";
    }

    private static List<Fld> contactFields(Citizen me) {
        return List.of(tel("phone", "Phone number", true).withDefault(me.phone == null ? "" : me.phone), email("email", "Email address", false).withDefault(me.email == null ? "" : me.email));
    }

    private static char letter(int i) { return (char) ('A' + i); }

    private static List<String> attachments(Policy.Service sv) {
        List<String> l = new ArrayList<>();
        for (String d : sv.docs) if (!d.equals("Passport photo")) l.add(d);
        return l;
    }

    private static boolean wantsPhoto(Policy.Service sv) { return sv.docs.contains("Passport photo"); }

    private static Resp needLogin() { return Resp.redirect("/login"); }

    // ------------------------------------------------------------------ the form
    /** Values the system already knows: the old passport on a renewal. */
    /** Details that do not change from one form to the next. They are filled in from the register or from the citizen's earlier forms. */
    private static final java.util.Set<String> STABLE = java.util.Set.of("village", "chief", "district", "postal", "birth_place", "birth_district", "nationality", "marital",
            "occupation", "mother_name", "mother_id", "mother_dob", "father_name", "father_id", "mother", "father", "chief_name", "chief_tel", "chief_area", "licence_no", "class",
            "birth_village", "mother_nat", "father_nat");

    private static String prefill(Citizen me, Policy.Service sv, Fld fld) {
        String field = fld.name;
        if (sv.name.equals("Passport Renewal") || sv.name.equals("Lost or Stolen Passport")) {
            String v = passportPrefill(me, sv, field);
            if (!v.isEmpty()) return v;
        }
        if (!STABLE.contains(field)) return "";
        String v = null;
        try {
            v = Db.one("select a.value from form_answers a join applications p on p.id=a.app_id where p.citizen_id=? and a.label=? and a.value<>'' order by p.id desc, a.ord desc limit 1",
                    rs -> rs.getString(1), me.id, fld.label);
        } catch (RuntimeException e) { v = null; } // never stop a form from opening because a suggestion could not be read
        if (v != null && (fld.opts.isEmpty() || fld.opts.contains(v))) return v;
        if (field.equals("district") && me.address != null)
            for (String d : DISTRICTS) if (me.address.toLowerCase().contains(d.toLowerCase())) return d;
        return "";
    }

    private static String passportPrefill(Citizen me, Policy.Service sv, String field) {
        String[] p = Store.currentPassport(me.id);
        if (p == null) return "";
        switch (field) { case "old_no": return p[0]; case "old_issue": return sv.name.equals("Passport Renewal") ? p[1] : ""; case "old_expiry": return sv.name.equals("Passport Renewal") ? p[2] : ""; default: return ""; }
    }
    private static String passportCard(Citizen me) {
        String[] p = Store.currentPassport(me.id);
        if (p == null) return "<div class='err' style='background:#fff7e6;border-color:#f0c36d;color:#7a5200'>No passport is recorded for you in the register. Type the number of your old passport below. If you never had one, apply for a New Passport instead.</div>";
        LocalDate exp = LocalDate.parse(p[2]);
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), exp);
        String state = days < 0 ? "<b style='color:#c0262d'>It expired on " + exp.format(LONG) + ".</b>"
                : days <= 183 ? "<b style='color:#7a5200'>It expires on " + exp.format(LONG) + " (" + days + " days left). You can renew it now.</b>"
                : "<b style='color:#0a7a3d'>It is still valid until " + exp.format(LONG) + ".</b> Passports are renewed in the last 6 months before they expire, so this application may be turned down.";
        return "<div class='part'><h2>Your old passport</h2><div class='pb'><div class='full'><p style='margin:6px 0'>Passport number <b>" + Html.esc(p[0]) + "</b><br>Issued on " + LocalDate.parse(p[1]).format(LONG) + "<br>" + state + "</p></div></div></div>";
    }
    private static Resp render(Citizen me, Policy.Service sv, Form fm, Req r, List<String> errors) {
        String back = "/services?dept=" + sv.dept.name();
        boolean photo = wantsPhoto(sv);
        boolean photoOnFile = photo && Store.docOf(me.id, "Passport photo") != null;
        StringBuilder sb = new StringBuilder("<div class='top noprint'><a href='" + back + "'>&lsaquo; Back to " + Html.esc(sv.dept.label) + "</a>"
                + "<a href='" + CitizenPages.bookHref(sv.dept, sv.name) + "' style='margin-left:18px'>Book an appointment</a></div><div class='sheet'>");
        sb.append(head(sv, fm)).append("<p class='in'>").append(Html.esc(fm.intro)).append(" Fields marked <b style='color:#c0262d'>*</b> must be filled in. Use the box that fits best.</p>");
        if (sv.pension) {
            int age = Policy.age(me, LocalDate.now());
            if (age < Policy.PENSION_AGE) sb.append("<div class='err'>You are ").append(age).append(". Pensions open at ").append(Policy.PENSION_AGE).append(". If you send this form it will be turned down automatically.</div>");
        }
        sb.append(Html.banner(r.get("m")));
        if (sv.name.equals("Passport Renewal")) sb.append(passportCard(me));
        if (!errors.isEmpty()) {
            sb.append("<div class='err'>Please fix the following:<ul>");
            for (String e : errors) sb.append("<li>").append(Html.esc(e)).append("</li>");
            sb.append("</ul>").append(photo || !attachments(sv).isEmpty() ? "<div style='margin-top:6px'>Choose your photo and documents again.</div>" : "").append("</div>");
        }
        sb.append("<form id='appform' method='post' action='/apply' enctype='multipart/form-data' autocomplete='off'>")
                .append("<input type='hidden' name='dept' value='").append(sv.dept.name()).append("'><input type='hidden' name='service' value='").append(Html.esc(sv.name)).append("'>");

        int part = 0;
        sb.append("<div class='part'><h2>Part ").append(letter(part++)).append(" &ndash; Applicant</h2>").append(known(me)).append("<div class='pb'>");
        for (Fld f : contactFields(me)) sb.append(input(f, r.p.containsKey(f.name) ? r.get(f.name) : ""));
        sb.append("</div></div>");

        for (Sec s : fm.secs) {
            sb.append("<div class='part'><h2>Part ").append(letter(part++)).append(" &ndash; ").append(Html.esc(s.title)).append("</h2><div class='pb'>");
            for (Fld f : s.fields) {
                String val = r.p.containsKey(f.name) ? r.get(f.name) : prefill(me, sv, f);
                sb.append(f.type.equals("area") ? "<div class='full'>" + input(f, val) + "</div>" : input(f, val));
            }
            sb.append("</div></div>");
        }

        if (photo) {
            sb.append("<div class='part'><h2>Part ").append(letter(part++)).append(" &ndash; Photograph ").append(photoOnFile ? "" : "<span style='color:#c0262d'>*</span>").append("</h2><div class='pb'><div class='full'>")
                    .append(photoInner().replace("id='photo'", "id='photo' data-need='" + (photoOnFile ? "0" : "1") + "'"))
                    .append(photoOnFile ? "<small>A photo is already on file and will be used. Add a new one only if you want to replace it.</small>" : "")
                    .append("</div></div></div>");
        }
        List<String> atts = attachments(sv);
        if (!atts.isEmpty()) {
            sb.append("<div class='part'><h2>Part ").append(letter(part++)).append(" &ndash; Documents to attach</h2><div class='pb'>");
            for (int i = 0; i < atts.size(); i++) {
                boolean onFile = Store.docOf(me.id, atts.get(i)) != null;
                sb.append("<label class='f'><span>").append(Html.esc(atts.get(i))).append(onFile ? "" : " <i>*</i>").append("</span><input type='file' name='doc").append(i)
                        .append("' accept='image/jpeg,image/png,application/pdf'><small>").append(onFile ? "Already on file. You can leave this empty to use it, or choose a new one." : "JPG, PNG or PDF, up to 5 MB.").append("</small></label>");
            }
            sb.append("</div></div>");
        }

        sb.append("<div class='part'><h2>Part ").append(letter(part)).append(" &ndash; Declaration</h2><div class='pb'><div class='full'>")
                .append("<p style='font-size:13px;margin:10px 0 2px'>I declare that everything on this form is true and complete. I know that false information can lead to this application being turned down.</p>")
                .append(input(f("signature", "Signature: type your full name as it appears at the top of this form", "text", true), r.p.containsKey("signature") ? r.get("signature") : ""))
                .append("<div style='font-size:12px;color:#475569;margin:0 0 6px'>Date: ").append(LocalDate.now().format(LONG)).append("</div>")
                .append("<label class='chk'><input type='checkbox' name='declare' value='yes'").append("yes".equals(r.get("declare")) ? " checked" : "").append("><span>I agree with the declaration above.</span></label>")
                .append("</div></div></div>");

        sb.append("<div class='official'>FOR OFFICIAL USE ONLY<div></div><div></div></div>")
                .append("<button class='btn noprint'>" + (sv.dept == lesotho.igss.Models.Dept.PO ? "Submit report" : "Submit application") + "</button></form></div>");
        Resp out = shell(sv.name, sb.toString(), photo);
        if (!errors.isEmpty()) out.code = 400;
        return out;
    }

    /** GET shows the form, POST checks it and sends the application. */
    static Resp apply(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        Dept d = CitizenPages.dept(r.get("dept"));
        Policy.Service sv = Policy.service(d, r.get("service"));
        Form fm = sv == null ? null : FORMS.get(sv.name);
        if (sv == null || fm == null) return Resp.to("/home", "err:Unknown service.");
        String back = "/services?dept=" + d.name();
        if (!r.post()) {
            if (!me.accountVerified) return Resp.to(back, "err:Home Affairs must confirm your account first.");
            return render(me, sv, fm, r, List.of());
        }

        if (!me.accountVerified) return Resp.to(back, "err:Home Affairs must confirm your account first.");
        if (sv.pension) {
            int age = Policy.age(me, LocalDate.now());
            if (age < Policy.PENSION_AGE) {
                Application a = Store.addApp(me.id, d, sv.name, Status.REJECTED, 0, "Rejected automatically: age " + age + ". Pension applications open at " + Policy.PENSION_AGE + ".");
                Store.notify(me.id, "Your " + sv.name + " was rejected: you are " + age + " and pensions open at " + Policy.PENSION_AGE + ". Reference " + a.reference + ".");
                return Resp.to("/app?id=" + a.id, "err:Rejected. Your ID shows you are " + age + " years old. You can apply from " + Policy.turnsPensionAge(me) + ".");
            }
            if (!me.alive) return Resp.to(back, "err:Home Affairs records this person as deceased.");
            if (Store.enrolled(me.id) && sv.name.equals(Registry.OLD_AGE)) return Resp.to("/pension", "err:You are already receiving the old-age grant.");
            if (Store.pensionEnrolled(me.id) && sv.name.equals(Registry.RETIRE)) return Resp.to("/pension", "err:You are already receiving the retirement pension.");
        }

        List<Fld> all = new ArrayList<>(contactFields(me));
        for (Sec s : fm.secs) all.addAll(s.fields);
        Map<String, String> vals = new LinkedHashMap<>();
        for (Fld f : all) { String v = clean(r.get(f.name), f.type.equals("area")); r.p.put(f.name, v); vals.put(f.name, v); }
        List<String> errs = new ArrayList<>();
        for (Fld f : all) { String e = fieldError(f, vals.get(f.name), vals); if (e != null) errs.add(e); }

        if (sv.pension && !Store.norm(vals.getOrDefault("idconfirm", "")).equals(me.id)) errs.add("Type your own ID number. It must match your account.");
        if (Store.hasOpen(me.id, sv.name)) errs.add(sv.dept == lesotho.igss.Models.Dept.PO ? "You already sent this report and Police have not finished with it." : "You already have an open " + sv.name + " application.");

        Map<String, Part> up = new LinkedHashMap<>();
        if (wantsPhoto(sv)) {
            Part p = r.files.get("photo");
            if (p == null) { if (Store.docOf(me.id, "Passport photo") == null) errs.add("Please add your photo. You can upload one or take one with the camera."); }
            else { String why = fileError(p, false); if (why != null) errs.add("Photo " + why); else up.put("Passport photo", p); }
        }
        List<String> atts = attachments(sv);
        for (int i = 0; i < atts.size(); i++) {
            Part p = r.files.get("doc" + i);
            if (p == null) { if (Store.docOf(me.id, atts.get(i)) == null) errs.add(atts.get(i) + " is required."); }
            else { String why = fileError(p, true); if (why != null) errs.add(atts.get(i) + " " + why); else up.put(atts.get(i), p); }
        }

        String sig = clean(r.get("signature"), false);
        r.p.put("signature", sig);
        if (sig.isEmpty()) errs.add("Type your full name as your signature.");
        else if (!signatureMatches(sig, me.name)) errs.add("Your signature must be your full name as shown at the top of the form (" + me.name + ").");
        if (!"yes".equals(r.get("declare"))) errs.add("Please tick the declaration at the bottom.");
        if (!errs.isEmpty()) return render(me, sv, fm, r, errs);

        // keep the files first, so a disk problem leaves no half-saved application
        List<String[]> saved = new ArrayList<>(); // doc type, original name, stored name, mime
        try {
            Files.createDirectories(Store.UPLOADS);
            for (Map.Entry<String, Part> e : up.entrySet()) {
                String mime = sniff(e.getValue().data);
                String stored = UUID.randomUUID() + extension(mime);
                Files.write(Store.UPLOADS.resolve(stored), e.getValue().data);
                saved.add(new String[]{e.getKey(), safeName(e.getValue().filename), stored, mime});
            }
        } catch (IOException ex) {
            for (String[] s : saved) try { Files.deleteIfExists(Store.UPLOADS.resolve(s[2])); } catch (IOException ignored) { }
            errs.add("We could not save your files. Please try again.");
            return render(me, sv, fm, r, errs);
        }
        for (String[] s : saved) Store.putDoc(me.id, s[0], s[1], s[2], s[3], "Uploaded");

        Application a = Store.addApp(me.id, d, sv.name, sv.fee > 0 ? Status.PAYMENT_DUE : Status.VERIFIED, sv.fee, "");
        int ord = 0;
        for (Fld f : contactFields(me)) ord = saveAnswer(a.id, ord, "Applicant", f.label, vals.get(f.name));
        for (Sec s : fm.secs) for (Fld f : s.fields) ord = saveAnswer(a.id, ord, s.title, f.label, vals.get(f.name));
        saveAnswer(a.id, ord, "Declaration", "Signed (typed name)", sig + " on " + LocalDate.now().format(LONG));
        Store.notify(me.id, "Your " + sv.name + (sv.dept == lesotho.igss.Models.Dept.PO ? " was received by Police." : " application was received.") + " Reference " + a.reference + (sv.fee > 0 ? ". Pay M " + sv.fee + " to continue." : ". There is no fee."));
        return Resp.to("/form/view?id=" + a.id, sv.fee > 0 ? "ok:Application received. Pay the fee to continue." : sv.dept == lesotho.igss.Models.Dept.PO ? "ok:Report sent to Police. Keep the reference number." : "ok:Application sent. There is no fee for this service.");
    }

    /** Every word of the first and last name on the register must be in what was typed. */
    static boolean signatureMatches(String typed, String registered) {
        List<String> t = words(typed), n = words(registered);
        if (n.isEmpty()) return false;
        return t.contains(n.get(0)) && t.contains(n.get(n.size() - 1));
    }

    private static List<String> words(String s) {
        List<String> l = new ArrayList<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}']+")) if (!w.isEmpty()) l.add(w);
        return l;
    }

    private static int saveAnswer(int appId, int ord, String section, String label, String value) {
        if (value == null || value.isEmpty()) return ord;
        Db.exec("insert into form_answers(app_id,ord,section,label,value) values(?,?,?,?,?)", appId, ord, section, label, value);
        return ord + 1;
    }

    // ------------------------------------------------------------------ the saved copy
    private static List<String[]> answers(int appId) {
        return Db.list("select section,label,value from form_answers where app_id=? order by ord", r -> new String[]{r.getString("section"), r.getString("label"), r.getString("value")}, appId);
    }

    /** The citizen's own printable copy of a form that was sent. */
    static Resp view(Req r) {
        Citizen me = r.citizen();
        if (me == null) return needLogin();
        Application a = null;
        try { a = Store.app(Integer.parseInt(r.get("id"))); } catch (Exception ignored) { }
        if (a == null || !a.citizenId.equals(me.id)) return Resp.to("/apps", "err:Application not found.");
        Policy.Service sv = Policy.service(a.dept, a.service);
        Form fm = FORMS.get(a.service);
        List<String[]> ans = answers(a.id);
        StringBuilder sb = new StringBuilder("<div class='top noprint'><a href='/apps'>&lsaquo; My Applications</a></div><div class='sheet'>");
        if (sv != null && fm != null) sb.append(head(sv, fm));
        else sb.append("<div class='head'><div class='crest'>KINGDOM OF LESOTHO</div><h1>").append(Html.esc(a.service)).append("</h1></div>");
        sb.append(Html.banner(r.get("m")));
        sb.append("<p class='in'>Reference <b>").append(Html.esc(a.reference)).append("</b> &middot; Status: <b>").append(Html.esc(a.status.label)).append("</b>")
                .append(a.fee > 0 ? " &middot; Fee M " + a.fee + (a.paid ? " paid, receipt " + Html.esc(a.receipt) : " not paid yet") : " &middot; No fee").append("</p>");
        int part = 0;
        sb.append("<div class='part'><h2>Part ").append(letter(part++)).append(" &ndash; Applicant</h2>").append(known(me)).append("<div class='known ans'><table>");
        List<String[]> rest = new ArrayList<>();
        for (String[] x : ans) { if (x[0].equals("Applicant")) sb.append("<tr><td>").append(Html.esc(x[1])).append("</td><td>").append(Html.esc(x[2])).append("</td></tr>"); else rest.add(x); }
        sb.append("</table></div></div>");
        String sec = null;
        for (String[] x : rest) {
            if (!x[0].equals(sec)) {
                if (sec != null) sb.append("</table></div></div>");
                sec = x[0];
                sb.append("<div class='part'><h2>").append(x[0].equals("Declaration") ? "Declaration" : "Part " + letter(part++) + " &ndash; " + Html.esc(x[0])).append("</h2><div class='known ans'><table>");
            }
            sb.append("<tr><td>").append(Html.esc(x[1])).append("</td><td style='white-space:pre-wrap'>").append(Html.esc(x[2])).append("</td></tr>");
        }
        if (sec != null) sb.append("</table></div></div>");
        sb.append("<div class='official'>FOR OFFICIAL USE ONLY<div></div><div></div></div>");
        sb.append("<div class='acts noprint'>")
                .append(a.status == Status.PAYMENT_DUE ? "<a class='btn' href='/pay?id=" + a.id + "'>Pay M " + a.fee + " now</a>" : "")
                .append("<a class='sm' href='/app?id=").append(a.id).append("'>Track this application</a>")
                .append("<a class='sm' href='").append(CitizenPages.bookHref(a.dept, a.service)).append("'>Book an appointment</a>")
                .append("<button class='sm' type='button' onclick='window.print()'>Print this form</button></div></div>");
        return shell(a.service, sb.toString(), false);
    }

    /** A small link to the saved form, for the application page. Empty when this application has no form. */
    static String viewLink(int appId) {
        return Db.count("select count(*) from form_answers where app_id=?", appId) > 0
                ? "<a class='sm' style='text-align:center' href='/form/view?id=" + appId + "'>View the form I filled in</a>" : "";
    }

    /** What the applicant wrote, for the staff card. Empty when there is no form. */
    static String answersHtml(int appId) {
        List<String[]> ans = answers(appId);
        if (ans.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<details style='margin-top:10px'><summary style='cursor:pointer;font-weight:700'>Application form</summary>");
        String sec = null;
        for (String[] x : ans) {
            if (!x[0].equals(sec)) { sec = x[0]; sb.append("<div class='lb'>").append(Html.esc(sec.toUpperCase(Locale.ROOT))).append("</div>"); }
            sb.append("<div class='mu'><b>").append(Html.esc(x[1])).append(":</b> ").append(Html.esc(x[2])).append("</div>");
        }
        return sb.append("</details>").toString();
    }

    /** For tests: the fields of a service's form (null when it has none). */
    static List<Fld> fieldsFor(String service) {
        Form fm = FORMS.get(service);
        if (fm == null) return null;
        List<Fld> l = new ArrayList<>();
        for (Sec s : fm.secs) l.addAll(s.fields);
        return l;
    }
}