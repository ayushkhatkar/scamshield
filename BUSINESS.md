# ScamShield — monetization & go-to-market plan

## 1. Who pays, and why

| Buyer | Pain | What they pay for |
|---|---|---|
| Adult children (25–45, urban, salaried) | "My parents will fall for a scam call and lose their savings." | **Family plan** — protection on parents' phones + instant alerts |
| Individuals who got a scary call/SMS | "Is this real?" | **Pro** — unlimited checks, cloud AI explanation, breach monitor |
| Banks, NBFCs, fintechs, hospitals, courier/e-commerce | Customers don't pick up genuine calls; fraud losses and support cost | **Verified Caller ID** + **SDK licence** |
| Insurers / wealth platforms | Want a wellness-style add-on for policyholders | **White-label** app or co-branded family plan |

The free tier exists to create the scary moment (a DANGEROUS result) that converts.

## 2. Revenue lines (built into the code)

1. **Subscriptions (Play Billing)** — `BillingManager`
   - Pro ₹99/month: unlimited scans, cloud AI analyzer, daily app audit + alerts, breach monitor.
   - Family ₹149/month or ₹999/year: everything, up to 5 phones, guardian push alerts. 7-day trial.
   - Free: 10 scans/day (`SettingsRepository.FREE_DAILY_SCANS`), manual app audit, Call Shield basic.
   - Levers you can tune without a release: quota size, which features are gated (search for `isPremium`).
2. **Verified Business Caller ID (B2B SaaS)** — `ScamShieldApi.verifiedBusinesses` + `source = "VERIFIED"` in the DB.
   Businesses pay per number per month to show a green verified badge instead of a warning. Truecaller's
   business revenue is the proof point. Start with hospitals, schools, local banks and delivery fleets.
3. **SDK licensing** — the `engine` module has zero Android dependencies. Publish it as an AAR/JAR and
   charge per MAU to banks/UPI apps that want "Is this link/QR/message a scam?" inside their own app.
4. **Community data** — aggregated, anonymised scam-number and URL feeds sold to telcos, banks and security
   vendors (ensure your privacy policy covers it).
5. **Affiliate / partner offers** — after a blocked attack, offer cyber-insurance, a password manager, or a
   senior-citizen phone plan. One card on the result screen; no ads anywhere (trust is the product).
6. **White-label** — brand colours and strings are centralised (`ui/theme`, `res/values`), so a bank can ship
   "SBI Shield powered by ScamShield" for a licence fee.

## 3. Pricing logic
- ₹99 is below the psychological "one chai a day" line; ₹149 for a family is cheaper than one month of a
  parent's phone plan. Yearly at ₹999 anchors the monthly as expensive.
- Expect 2–4% free→paid conversion if the DANGEROUS screen is good; Family should be ≥60% of paid revenue
  because the buyer is emotionally motivated and churns less.
- Trial on Family only: it needs two phones set up, so the trial creates the habit.

## 4. Launch plan (first 90 days, India)
1. **Weeks 1–2:** Play Console setup (subscriptions, QUERY_ALL_PACKAGES declaration, Data safety), Firebase
   project, Safe Browsing/Web Risk key, privacy policy page. Closed testing with 20 families.
2. **Weeks 3–6:** Soft launch. Content engine: one "scam of the week" video (Hindi + English) showing the app
   catching it. Partner with 2–3 RWAs / senior-citizen groups; the guardian-child story sells itself.
3. **Weeks 7–12:** Verified Caller pilot with one hospital chain or NBFC (free for 3 months, case study).
   Add Hindi UI. Start the backend (`/v1/scam-numbers`, `/v1/analyze`) on Cloud Run.
4. **KPIs:** DANGEROUS results per 100 scans, Call Shield activation rate, family links per paying account,
   D30 retention of Family subscribers, reports submitted per MAU.

## 5. Costs (indicative)
- Firebase (Firestore + Functions + FCM): free tier covers the first ~10k MAU; then ~₹2–5k/month.
- Have I Been Pwned key: ~US$3.50/month. Web Risk API: pay-as-you-go, minor at launch.
- LLM calls for the Pro analyzer: ~₹0.05–0.20 per message; gate behind Pro and cache by hash.
- Play's cut: 15% on the first US$1M/year.

## 6. Compliance notes
- Play's **SMS/Call Log policy**: never request READ_SMS/READ_CALL_LOG. Call Shield uses the Call Screening role; message checks use share/selection/paste.
- **QUERY_ALL_PACKAGES**: allowed for device-security apps with a declaration; keep the audit visible and user-triggered.
- **DPDP Act (India)**: minimise data, publish retention periods, add "delete my data" (Settings → clear history already deletes local data; add a backend endpoint for Firestore).
- **RBI/SEBI claims**: never say "RBI approved". Say "checks against RBI's regulated-lender list" only once you actually import it.
- Keep the 1930 / cybercrime.gov.in routes prominent — reporting flows are a real public good and Play reviewers like them.

## 7. Defensibility
Google is adding scam detection to Messages and the Phone app. ScamShield's moat is not detection alone:
1. the **family layer** (guardian alerts, shared blocklist, "protect my parents" positioning),
2. **India-specific intelligence** (UPI QR semantics, loan-app patterns, TRAI numbering, Hinglish playbooks),
3. the **community + verified-business dataset**, which compounds with every user,
4. an **SDK** that lets banks ship the same protection inside their own apps.
