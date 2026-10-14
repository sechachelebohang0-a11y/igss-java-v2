# Integrated Government Services System - Lesotho (Java + SQLite prototype)

One citizen profile, verified once by Home Affairs, used by Traffic, Finance, Pensions, Police and Passport Services.
Blue-only interface. Data is stored in a real SQLite database (`igss.db`). Uploaded pictures and documents are stored in the `uploads` folder.

## Run it
Needs Java 17 or newer (JDK 21 recommended).

* Windows: double-click `run.bat`    * Mac / Linux: `sh run.sh`
* The first run downloads the SQLite driver into `lib/` (internet needed once).
* IntelliJ: open the folder as a Maven project (it downloads the driver for you), then run `lesotho.igss.Main`.

Open http://localhost:8080. To start again with fresh sample data, stop the program and delete `igss.db` and the `uploads` folder.

## Demo logins
| Who | Login | Password |
|---|---|---|
| Thabo (26) | ID 9901015678082 | thabo123 |
| Mpho (38) | ID 8804121234081 | mpho123 |
| Nthabeleng (61, can claim a pension) | ID 5605205052084 | nthabeleng123 |
| New citizen to register | ID 9505055057081, name Lerato Mabote, born 1995-05-05 | choose one |
| Officials: Home Affairs, Traffic, Finance, Pensions, Police, Passport | HA001, TR001, FI001, PE001, PO001, PA001 | pass123 |

Fingerprint codes (type them in the Fingerprint box): FP-1001 Thabo, FP-1002 Mpho, FP-2001 Mokete, FP-2002 Nthabeleng.

## What connects to what
1. **Home Affairs is the source of truth.** Citizens register against the Home Affairs register (ID + date of birth). A name that does not match goes to Home Affairs "Pending Verifications".
2. **Upload once, reuse everywhere.** A citizen uploads a document from their computer once. Home Affairs verifies it. Each service lists the documents it needs and reuses what is on file.
3. **Finance links to every department.** Services with a fee start as "Payment required". The citizen pays, Finance records the payment and receipt, and only then can the department process the application.
4. **Pensions use Home Affairs data.** A pension application asks for the ID number and checks the age from the Home Affairs record. Under 60 is rejected automatically. Approval puts the person on the pension register. Pensions sends monthly payments to Finance, and Finance marks them paid.
5. **Deaths flow to everyone.** When Home Affairs registers a death, pension payments stop, open applications are rejected, and no department can approve anything for that person.
6. **Minimum necessary access.** Every department search shows only the fields that department needs, and a reason is required. Each search is saved in the access log and the citizen sees it under "Who Accessed My Data".
7. **Citizens only see their own information.** Applications, documents, payments, notifications and files are checked against the logged-in citizen. Officers can only open documents their department is allowed to see, and opening one is logged.

## Search boxes (every department)
Name, ID number, phone number and fingerprint code are four separate boxes. Fill in one or several. Every filled box must match.

## Code map (src/main/java/lesotho/igss)
| File | Job |
|---|---|
| `Models.java` | Data classes |
| `Db.java` | Small SQLite/JDBC helper |
| `Store.java` | All SQL: tables, queries, sample data |
| `Policy.java` | Rules: field access per department, the service catalogue, document rules, pension age 60 |
| `Html.java` | Page building blocks (blue theme) |
| `CitizenPages.java` | Citizen pages |
| `StaffPages.java` | Department-employee pages |
| `FilePage.java` | Secure file download |
| `Web.java`, `Main.java` | Web server, routes, sessions, file upload parsing |

## Limits of this prototype
* Fees, pension amount and payday are sample values. No real money, fingerprint device or camera is used.
* Passwords are hashed with a salt, but there is no HTTPS or protection against form forgery (CSRF). A real system needs both.
