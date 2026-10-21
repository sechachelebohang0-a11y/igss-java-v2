package lesotho.igss;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extra Sesotho wording for the newest pages (police cases, lost documents, pension rule). First draft: please have a Sesotho speaker check it. */
final class Sesotho {
    private Sesotho() {}

    private static final Map<String, String> D = new HashMap<>();
    private static void d(String en, String st) { D.put(en, st); }

    static {
        d("Police Cases", "Linyeoe tsa Mapolesa"); d("Lost IDs & Passports", "Li-ID le Lipasepoto tse Lahlehileng"); d("Person Record", "Rekoto ea Motho");
        d("Open case", "Nyeoe e butsoe"); d("Under investigation", "E batlisisoa"); d("Suspect identified", "Ea belaelloang o tsebahetse");
        d("In court", "Kantlong"); d("Closed", "E koetsoe"); d("Reported", "E tlalehiloe"); d("Found", "E fumanoe"); d("Case", "Nyeoe");
        d("Search by ID number, name or fingerprint code. Pick the open case you are working on. Every look is logged.",
                "Batla ka nomoro ea ID, lebitso kapa khoutu ea monoana. Khetha nyeoe e butsoeng eo u e sebetsang. Ho sheba ka 'ngoe ho ngotsoe.");
        d("Choose an open case", "Khetha nyeoe e butsoeng"); d("Open record", "Bula rekoto");
        d("Fill in a name, an ID number or a fingerprint code.", "Kenya lebitso, nomoro ea ID kapa khoutu ea monoana.");
        d("Pick an open case. Open one under Police Cases if you have none.", "Khetha nyeoe e butsoeng. Bula e 'ngoe ho Linyeoe tsa Mapolesa haeba u se na eona.");
        d("No match in the Home Affairs register.", "Ha ho se tsamaellanang ho register ea Litaba tsa Ka Hare.");
        d("Record and follow criminal cases. Open a case, add updates and close it when it is finished.",
                "Ngola 'me u latele linyeoe tsa botlokotsebe. Bula nyeoe, kenya lintlafatso 'me u e koale ha e fela.");
        d("OPEN A NEW CASE", "BULA NYEOE E NCHA"); d("Case title", "Sehlooho sa nyeoe"); d("Details", "Lintlha");
        d("ID number of the person (optional)", "Nomoro ea ID ea motho (ha u rata)"); d("Create case", "Theha nyeoe");
        d("OPEN CASES", "LINYEOE TSE BUTSOENG"); d("CLOSED CASES", "LINYEOE TSE KOETSOENG"); d("No cases yet.", "Ha ho linyeoe hajoale.");
        d("Write a short case title.", "Ngola sehlooho se sekhutšoanyane sa nyeoe."); d("No citizen with that ID number in the register.", "Ha ho moahi ea nang le nomoro eo ea ID ho register.");
        d("Case not found.", "Nyeoe ha e fumanehe."); d("PERSON IN THIS CASE", "MOTHO NYEOENG ENA"); d("UPDATE THE CASE", "NTLAFATSA NYEOE"); d("CASE HISTORY", "HISTORI EA NYEOE");
        d("Open person record", "Bula rekoto ea motho"); d("No person is linked to this case yet.", "Ha ho motho ea hokelitsoeng nyeoeng ena.");
        d("Link person", "Hokela motho"); d("Update note", "Tlhokomeliso ea ntlafatso"); d("Save update", "Boloka ntlafatso");
        d("Write a note or change the status.", "Ngola tlhokomeliso kapa u fetole boemo."); d("No updates yet.", "Ha ho lintlafatso hajoale.");
        d("HOME AFFAIRS: ID DETAILS", "LITABA TSA KA HARE: LINTLHA TSA ID"); d("FINGERPRINTS", "MATŠOAO A MENOANA"); d("HOME ADDRESS", "ATERESE EA BOLULO");
        d("OTHER RECORDS FOR THIS CASE", "LIREKOTO TSE LING BAKENG SA NYEOE ENA"); d("LOST DOCUMENTS", "LITOKOMANE TSE LAHLEHILENG");
        d("TRAFFIC: VEHICLE AND DRIVER RECORDS", "LITERAKA: LIREKOTO TSA LIKOLOI LE BAKHANNI"); d("FINANCE: PAYMENTS", "LICHELETE: LITEFO");
        d("PENSION RECORD", "REKOTO EA PENSHENE"); d("PASSPORT RECORDS", "LIREKOTO TSA PASEPOTO");
        d("Traffic records", "Lirekoto tsa Literaka"); d("Pension record", "Rekoto ea penshene"); d("Passport records", "Lirekoto tsa pasepoto");
        d("Link this person to the case", "Hokela motho enoa nyeoeng ena");
        d("No fingerprints on file.", "Ha ho matšoao a menoana a bolokiloeng.");
        d("The address is only shown when Home Affairs approves it for this case.", "Aterese e bonts'oa feela ha Litaba tsa Ka Hare li e amohela bakeng sa nyeoe ena.");
        d("Waiting for Home Affairs to decide on the address request.", "Ho emetsoe hore Litaba tsa Ka Hare li nke qeto kopong ea aterese.");
        d("Home Affairs refused the address request.", "Litaba tsa Ka Hare li hanne kopo ea aterese.");
        d("Reason for needing the address", "Lebaka la ho hloka aterese"); d("Send request", "Romela kopo");
        d("Write the reason for needing the address. Home Affairs needs it.", "Ngola lebaka la ho hloka aterese. Litaba tsa Ka Hare li le hloka.");
        d("Address request sent to Home Affairs.", "Kopo ea aterese e rometsoe Litabeng tsa Ka Hare.");
        d("No vehicle or driver records.", "Ha ho lirekoto tsa likoloi kapa bakhanni."); d("No fee payments.", "Ha ho litefo tsa litšebeletso.");
        d("No passport records.", "Ha ho lirekoto tsa pasepoto."); d("No lost documents recorded.", "Ha ho litokomane tse lahlehileng tse ngotsoeng.");
        d("Record a lost or stolen ID card or passport. A case is opened and the citizen is told.",
                "Ngola karete ea ID kapa pasepoto e lahlehileng kapa e utsoitsoeng. Nyeoe e a buloa 'me moahi oa tsebiswa.");
        d("ID number of the owner", "Nomoro ea ID ea mong'a eona"); d("Which document?", "Ke tokomane efe?"); d("National ID card", "Karete ea ID ea naha"); d("Passport", "Pasepoto");
        d("Document number (if known)", "Nomoro ea tokomane (haeba e tsejoa)"); d("Date it was lost", "Letsatsi leo e lahlehileng ka lona");
        d("Place where it was lost", "Sebaka seo e lahlehileng ho sona"); d("Record lost document", "Ngola tokomane e lahlehileng");
        d("RECENT REPORTS", "LITLAHALO TSA MOHLA"); d("No reports yet.", "Ha ho litlaleho hajoale."); d("Mark found", "Tšoaea e fumanoe");
        d("How pensions work", "Lipenshene li sebetsa joang");
        d("People who work for the government do not receive a pension. People who work in the private sector receive a pension when they stop working.",
                "Batho ba sebeletsang mmuso ha ba fumane penshene. Batho ba sebeletsang lekaleng la poraefete ba fumana penshene ha ba tlohela ho sebetsa.");
        d("Finance Services handles fees, receipts, refunds and pension payments. It does not collect tax.",
                "Litšebeletso tsa Lichelete li sebetsana le litefo, liresiti, ho khutlisetsoa chelete le litefo tsa penshene. Ha li bokelle thakse.");
        d("You work for the government", "U sebeletsa mmuso"); d("Government employee (no pension)", "Mosebeletsi oa mmuso (ha ho penshene)");
        d("Monthly pension for private-sector workers who stopped working, aged 60 and above.", "Penshene ea khoeli le khoeli bakeng sa basebetsi ba lekala la poraefete ba tlohetseng mosebetsi, ba lilemo tse 60 le ho feta.");
        d("Claim your retirement benefits when you stop working in the private sector (age 60 and above).", "Kopa melemo ea hau ea ho tlohela mosebetsi ha u tlohela ho sebetsa lekaleng la poraefete (lilemo tse 60 le ho feta).");
        d("People who worked for the government do not receive a pension. Finance and Pensions keep this list, and they cannot pay these people a pension.",
                "Batho ba sebelelitseng mmuso ha ba fumane penshene. Lichelete le Lipenshene li boloka lethathamo lena, 'me ha li khone ho lefa batho bana penshene.");
        d("Mark as government worker", "Tšoaea e le mosebeletsi oa mmuso");
    }

    private static final Pattern BLOCK = Pattern.compile("(?is)<(style|script)\\b.*?</\\1>");
    private static final Pattern TEXT = Pattern.compile(">([^<>]+)<");
    private static final String[] FRONT = {"&lsaquo; ", "&#10003; ", "! "};

    private static String esc(String s) { return s.replace("&", "&amp;").replace("'", "&#39;"); }

    private static String core(String t) {
        if (t.endsWith(" - IGSS Lesotho")) {
            String in = core(t.substring(0, t.length() - " - IGSS Lesotho".length()));
            return in == null ? null : in + " - IGSS Lesotho";
        }
        for (String p : FRONT) if (t.startsWith(p)) { String in = core(t.substring(p.length())); return in == null ? null : p + in; }
        String out = D.get(t.replace("&amp;", "&").replace("&#39;", "'"));
        return out == null ? null : esc(out);
    }

    private static String segment(String html) {
        Matcher m = TEXT.matcher(html);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String text = m.group(1), t = text.strip();
            String o = t.isEmpty() ? null : core(t);
            out.append(html, last, m.start(1));
            if (o == null) out.append(text);
            else out.append(text, 0, text.length() - text.stripLeading().length()).append(o).append(text.substring(text.stripTrailing().length()));
            last = m.end(1);
        }
        return out.append(html.substring(last)).toString();
    }

    /** Runs after Display. Translates what Display left in English, leaving style and script blocks alone. */
    static String apply(String html, String lang) {
        if (!lang.equals("st")) return html;
        Matcher b = BLOCK.matcher(html);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (b.find()) {
            out.append(segment(html.substring(last, b.start()))).append(b.group());
            last = b.end();
        }
        return out.append(segment(html.substring(last))).toString();
    }
}