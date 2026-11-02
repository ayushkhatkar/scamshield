# ScamShield — scam protection for you and your parents (Android, Kotlin)

ScamShield stops the scams that actually drain Indian bank accounts: fake-KYC links, "scan to receive
refund" UPI QRs, digital-arrest calls, task-job and investment groups, predatory loan apps and
remote-control "customer care" tools. Everything core runs **on-device**; cloud pieces are optional.

```
ScamShield/
├── engine/      Pure-Kotlin detection engine (no Android deps) — unit-tested, SDK-ready
├── app/         Jetpack Compose app (Hilt, Room, DataStore, WorkManager, Play Billing, Firebase)
├── firebase/    Cloud Function (guardian push + report aggregation) and Firestore rules
├── README.md    this file
└── BUSINESS.md  monetization, pricing, Play-policy and launch plan
```

## Features

| Feature | How it works | Tier |
|---|---|---|
| **Link check** | Heuristics (brand impersonation, free hosting, punycode, `@` tricks, APK links, risky TLDs, shorteners) + optional Google Safe Browsing | Free (10/day) / unlimited |
| **QR check** | Google code scanner → UPI deep-link parser: shows *who* gets paid, *how much*, flags "refund/cashback" payee names, collect requests and AutoPay mandates; also Wi-Fi, tel:, URL QRs | Free / unlimited |
| **Message check** | 18 Indian scam playbooks (OTP, digital arrest, KYC block, electricity, lottery/KBC, task jobs, trading tips, loans, family emergency, reward points, AnyDesk, wrong-transfer, sextortion, courier fee…) with Hinglish phrases | Free / unlimited |
| **"Check with ScamShield"** | Appears in the text-selection toolbar of every app (PROCESS_TEXT), in the share sheet, and as "Open with" for links | All |
| **Call Shield** | `CallScreeningService` via the official *Caller ID & spam* role: blocks reported numbers, high-risk country codes (+92, +84…), optional 140-series; warns on suspicious; never reads SMS/call log | All |
| **Check a number / report** | TRAI numbering rules + community DB; one tap report → blocked locally + shared | All |
| **App audit** | PackageManager + engine: fake banking apps (label ≠ real package), predatory loan apps (contacts/SMS/location), sideloaded finance apps, accessibility/overlay abuse, remote-control tools, OTP-reading apps | Manual: Free · Daily + alerts: Pro |
| **Family mode** | Guardian creates a 6-char code; a parent's phone joins as *Protected*. Blocked calls, dangerous scans and risky apps post events → FCM push to guardians (Cloud Function) with a 15-min polling fallback | Family |
| **Breach monitor** | Have I Been Pwned lookup for the user's e-mail | Pro |
| **Cloud AI analyzer** | Hook to your backend (`/v1/analyze`) for an LLM second opinion on messages | Pro |
| **Safety Score** | Call Shield on, screen lock, risky apps, USB debugging, notifications, family linked | All |
| **Report routes** | 1930 helpline, cybercrime.gov.in, Sanchar Saathi (Chakshu), RBI Sachet, shareable warning text | All |
| **Verified Business Caller ID** | Numbers in the `VERIFIED` registry show a green badge instead of a warning | B2B |

## Build

Requirements: Android Studio Ladybug or newer, JDK 17, Android SDK 35.

```bash
cp local.properties.example local.properties   # set sdk.dir; keys are optional
./gradlew :engine:test                           # 26 engine unit tests
./gradlew :app:assembleDebug
```

Gradle 8.13 wrapper, AGP 8.10, Kotlin 2.1.21, Compose BOM 2025.05.01, Hilt 2.56, Room 2.7.
If Android Studio suggests newer versions on first sync, accept them — nothing here depends on an exact version.

### Optional keys (`local.properties`)

| Key | Enables | Where |
|---|---|---|
| `SAFE_BROWSING_API_KEY` | Google blocklist check on links | console.cloud.google.com → Safe Browsing API (free tier is non-commercial; use **Web Risk** once you charge) |
| `HIBP_API_KEY` | Breach monitor | haveibeenpwned.com/API/Key (~US$3.50/mo) |
| `SCAMSHIELD_API_BASE_URL` | Your backend: curated blocklist, verified businesses, reports, cloud analyzer | see `app/.../data/remote/Apis.kt` for the 4 endpoints |

### Family mode (Firebase, optional)

1. Create a Firebase project, add the Android app (`com.scamshield.app`), download `google-services.json` into `app/`.
   The Gradle file applies the plugin only when the file exists, so the build never breaks without it.
2. Enable **Authentication → Anonymous** and **Firestore**.
3. `cd firebase && npm --prefix functions install && firebase deploy --only functions,firestore:rules`
   (region `asia-south1`; edit `functions/index.js` to change).
4. Without step 3, guardians still get alerts through the 15-minute polling worker.

### Play Billing

Create three subscriptions in Play Console with these product IDs (see `BillingManager`):
`scamshield_pro_monthly`, `scamshield_family_monthly`, `scamshield_family_yearly`.
Prices in the paywall fall back to ₹99 / ₹149 / ₹999 until Play returns real `ProductDetails`.
Before launch, add server-side purchase verification (a Cloud Function calling the Play Developer API) and store the entitlement there.

## Architecture

- `engine/` — `ScamShieldEngine` facade → `UrlAnalyzer`, `UpiQrAnalyzer`, `MessageAnalyzer` (+ `ScamPatterns` data), `PhoneNumberAnalyzer`, `AppRiskScorer`. Every result is a `RiskResult(score 0-100, level, summary, recommendation, reasons[], facts{})`. Scoring thresholds live in `RiskScale` (20 / 45 / 70).
- `app/data/` — Room (`scans`, `scam_numbers`, `call_events`, `app_risks`), DataStore settings + free-tier quota, Retrofit APIs, repositories (`ScanRepository`, `ScamNumberRepository`, `AppAuditRepository`, `FamilyRepository`, `BreachRepository`).
- `app/callshield/` — `ScamCallScreeningService` + `CallShieldManager` (policy from settings).
- `app/work/` — `ScamDbSyncWorker` (12h), `AppAuditWorker` (24h, Pro), `FamilyPollWorker` (15m).
- `app/billing/` — `BillingManager` (Play Billing 7).
- `app/ui/` — Compose screens: Home, Scan (+ result), Calls, Apps, Family, Paywall, Breach, Settings. Hilt ViewModels, `StateFlow` everywhere.

### Adding a new scam pattern
Edit `engine/.../message/ScamPatterns.kt` (phrases + weight), add a test in `EngineTest.kt`, run `./gradlew :engine:test`.
Brand/domain lists live in `KnownDomains.kt`; official package hints in `AppRiskScorer.kt`.

## Play Store policy checklist

- **No SMS / call-log permissions** — detection uses share/selection/QR input and the Call Screening role, which Play allows.
- **QUERY_ALL_PACKAGES** — declare the "device security / app audit" use case in Play Console → App content → Sensitive permissions.
- **Prominent disclosure** — the Family screen and Settings explain what is shared (alert summaries only). Add an in-app disclosure dialog before the first family link if you add more data.
- **Data safety form** — scans and audits are on-device; family events, reports and FCM tokens go to Firestore; optional link checks go to Google Safe Browsing.
- Replace the placeholder privacy-policy / contact-sales URLs in `SettingsScreen.kt` and `PaywallScreen.kt`.

## Roadmap ideas
Hindi/Tamil/Telugu UI, on-device ML classifier for messages (TFLite) trained on reported samples, screenshot OCR ("check this screenshot"), accessibility-based WhatsApp link warnings with explicit opt-in, senior "large text" mode, scam-insurance partner offer after a blocked attack.

## Licence
Proprietary — you own this code. The engine can be published separately as the paid SDK (see BUSINESS.md).
