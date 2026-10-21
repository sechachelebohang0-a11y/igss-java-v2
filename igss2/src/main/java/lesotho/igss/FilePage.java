package lesotho.igss;

import lesotho.igss.Models.*;

import java.io.IOException;
import java.nio.file.Files;

/** Sends an uploaded document or photo, but only to its owner or to an officer who is allowed to see it. */
final class FilePage {
    private FilePage() {}

    static Resp serve(Req r) {
        Doc d;
        try { d = Store.doc(Integer.parseInt(r.get("id"))); } catch (Exception e) { d = null; }
        if (d == null) return Resp.notFound();
        Citizen me = r.citizen();
        Official o = r.staff();
        boolean owner = me != null && me.id.equals(d.citizenId);
        if (!owner) {
            // Police may open fingerprints for an investigation. It is logged, but the citizen is not sent a message.
            boolean policeFingerprints = d.type.equals("Fingerprints") && o != null && o.dept == Dept.PO;
            if (o == null || (!policeFingerprints && !Policy.canView(o, d))) return Resp.error(403, "You are not allowed to open this file.");
            boolean photoByPolicy = d.type.equals("Passport photo") && Policy.allowed(o.dept).contains(Policy.Field.PHOTO);
            if (policeFingerprints) {
                Store.logAccess(d.citizenId, o.dept, o.name, "Fingerprints", "Police investigation");
            } else if (!photoByPolicy) {
                Store.logAccess(d.citizenId, o.dept, o.name, "Document: " + d.type, "Opened while processing an application");
                Store.notify(d.citizenId, o.dept.label + " opened your " + d.type + " while processing your application.");
            }
        }
        if (d.stored == null) return Resp.error(404, "This document is a paper record. No file is stored.");
        try {
            Resp out = new Resp();
            out.bytes = Files.readAllBytes(Store.UPLOADS.resolve(d.stored));
            out.type = d.mime;
            return out;
        } catch (IOException e) { return Resp.notFound(); }
    }
}