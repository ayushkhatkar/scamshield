package com.scamshield.engine.message

/**
 * Scam playbooks seen in India, as data. Each pattern is a set of phrases; a message that hits
 * [minHits] of them earns [weight]. Hindi/Hinglish spellings are included because most scam
 * SMS and WhatsApp messages are written that way.
 *
 * Keep this in sync with the "Scam of the week" content you push from the backend.
 */
data class ScamPattern(
    val code: String,
    val title: String,
    val explanation: String,
    val phrases: List<String>,
    val weight: Int,
    val minHits: Int = 1
)

object ScamPatterns {

    val ALL: List<ScamPattern> = listOf(
        ScamPattern(
            "OTP_REQUEST", "Asks for an OTP or PIN",
            "No bank, courier, government office or company will ever ask you to share an OTP, PIN, CVV or password. The moment someone asks, it is a scam.",
            listOf("share otp", "send otp", "tell otp", "otp bata", "otp batao", "otp share", "one time password",
                "your otp", "enter otp", "otp received", "atm pin", "upi pin", "mpin", "cvv", "card number", "password", "passcode"),
            weight = 40
        ),
        ScamPattern(
            "DIGITAL_ARREST", "'Digital arrest' / police / courier threat",
            "Police, CBI, customs and courier companies never call to say a parcel in your name contains drugs or that you are under 'digital arrest'. Real investigations are never settled over a video call or by transferring money.",
            listOf("digital arrest", "cbi", "narcotics", "ncb", "money laundering", "arrest warrant", "police station",
                "customs", "parcel", "courier", "fedex", "dhl", "contains drugs", "illegal items", "passport",
                "ed officer", "enforcement directorate", "cyber cell", "supreme court", "skype", "video call for verification",
                "trai", "your number will be blocked", "sim will be blocked", "number will be disconnected", "telecom department"),
            weight = 35
        ),
        ScamPattern(
            "KYC_BLOCK", "KYC / account-block pressure",
            "Banks and wallets never block accounts for KYC over SMS or WhatsApp links. Update KYC only inside the official app or at the branch.",
            listOf("kyc", "e-kyc", "ekyc", "account will be blocked", "account blocked", "account suspended",
                "account will be suspended", "account has been blocked", "update your pan", "pan card",
                "aadhaar link", "aadhar link", "link your aadhaar", "re-kyc", "rekyc", "kyc pending", "kyc expired",
                "account hold", "account freeze", "wallet blocked", "wallet suspended"),
            weight = 30
        ),
        ScamPattern(
            "ELECTRICITY", "Electricity disconnection threat",
            "Electricity boards do not disconnect power over WhatsApp for an 'unupdated bill'. Check your bill only in the official app of your supplier.",
            listOf("electricity", "power will be disconnected", "power cut tonight", "bill not updated",
                "bijli", "electricity officer", "connection will be cut", "disconnected tonight", "bill update"),
            weight = 30
        ),
        ScamPattern(
            "LOTTERY", "Lottery / lucky draw / KBC prize",
            "You cannot win a lottery you never entered. 'Pay a small fee to release the prize' is the oldest scam there is.",
            listOf("lottery", "kbc", "kaun banega", "you have won", "you won", "lucky draw", "lucky winner", "prize money",
                "congratulations you", "selected as winner", "jackpot", "lakh ka inam", "inam", "crore prize",
                "claim your prize", "processing fee", "release the prize"),
            weight = 35
        ),
        ScamPattern(
            "TASK_JOB", "Part-time / task job scam",
            "'Earn daily by liking videos / rating hotels / doing tasks' schemes pay a little at first, then make you deposit money to 'unlock' earnings. The deposit is never returned.",
            listOf("part time job", "part-time job", "work from home", "earn daily", "daily earning", "earn rs",
                "earn ₹", "like youtube videos", "liking youtube", "liking videos", "like and subscribe", "rate hotels",
                "review products", "simple task", "complete tasks", "task completion", "commission", "join telegram",
                "telegram group", "hr department", "prepaid task", "merchant task", "welfare task", "we are hiring",
                "no experience required", "unlock earnings", "daily salary", "per task", "per day income"),
            weight = 35
        ),
        ScamPattern(
            "INVESTMENT", "Guaranteed-return investment / trading tips",
            "Guaranteed or very high returns are illegal promises. 'VIP trading groups', fake stock apps and crypto doubling schemes let you withdraw once, then take everything.",
            listOf("guaranteed return", "guaranteed returns", "guaranteed profit", "double your money", "triple your money",
                "100% profit", "daily profit", "trading tips", "stock tips", "vip group", "vip trading", "ipo allotment",
                "crypto investment", "bitcoin investment", "forex", "sure shot", "sureshot", "risk free", "risk-free",
                "assured return", "monthly return", "investment plan", "block trade", "institutional account"),
            weight = 30
        ),
        ScamPattern(
            "LOAN", "Instant loan bait",
            "Instant, no-document loans from unknown apps lead to illegal harassment, contact-list blackmail and 1000%+ interest. Borrow only from RBI-regulated lenders.",
            listOf("instant loan", "loan approved", "pre-approved loan", "no documents", "without documents",
                "loan in 5 minutes", "loan in 10 minutes", "personal loan offer", "low interest loan", "loan app",
                "urgent loan", "cash loan", "credit limit increased"),
            weight = 25
        ),
        ScamPattern(
            "FAMILY_EMERGENCY", "Family emergency / 'your son is in trouble'",
            "Scammers call parents claiming a child is arrested, hospitalised or kidnapped and demand money immediately. Hang up and call your family member directly.",
            listOf("your son", "your daughter", "your child", "beta hospital", "accident", "hospital", "kidnapped",
                "kidnap", "arrested", "in police custody", "bail", "send money immediately", "send money urgently",
                "transfer money now", "emergency", "urgent money", "i am in trouble", "mummy", "papa"),
            weight = 25,
            minHits = 2
        ),
        ScamPattern(
            "IMPERSONATION", "Pretends to be a bank, government or official body",
            "Official bodies communicate through registered sender IDs and official apps, never from personal numbers or WhatsApp.",
            listOf("rbi", "reserve bank", "sbi", "hdfc", "icici", "axis bank", "income tax", "it department",
                "refund of", "tax refund", "epfo", "pf withdrawal", "gst", "uidai", "sebi", "nhai", "fastag",
                "sim card", "jio", "airtel", "vi ", "bsnl", "post office", "india post", "irctc", "pm kisan",
                "pm yojana", "scholarship", "subsidy", "government scheme", "ration card", "voter id", "driving licence"),
            weight = 25
        ),
        ScamPattern(
            "REWARD_POINTS", "Reward points / cashback expiring",
            "'Your reward points expire today, click to redeem' is a phishing template used for every Indian bank. Check points only inside the official app.",
            listOf("reward points", "reward point", "points expire", "points will expire", "expiring today",
                "redeem now", "redeem your", "cashback of", "cashback credited", "cashback pending", "scratch card",
                "bonus points", "redeem before"),
            weight = 25
        ),
        ScamPattern(
            "REMOTE_ACCESS", "Asks to install a screen-sharing app",
            "AnyDesk, TeamViewer and similar apps let the caller see and control your phone, including your banking app. No real support team needs this.",
            listOf("anydesk", "any desk", "teamviewer", "team viewer", "quick support", "quicksupport", "screen share",
                "screen sharing", "remote access", "install this app", "download this app", "apk", "share your screen"),
            weight = 40
        ),
        ScamPattern(
            "WRONG_TRANSFER", "'Money sent by mistake, please return it'",
            "A classic: a fake 'credited' SMS followed by a request to return money. Check your real bank balance first; the credit usually never happened.",
            listOf("sent by mistake", "wrongly transferred", "wrong transfer", "by mistake", "please return",
                "return the money", "refund the amount", "galti se", "wapas", "send it back", "transferred accidentally"),
            weight = 30
        ),
        ScamPattern(
            "SEXTORTION", "Video-call blackmail",
            "Blackmail after a video call is a known gang operation. Do not pay; paying only brings more demands. Block, keep evidence and report at cybercrime.gov.in.",
            listOf("your video", "viral", "will be uploaded", "private video", "youtube upload", "i will send to your contacts",
                "nude", "screen recording", "morphed", "delete the video", "pay or else"),
            weight = 35
        ),
        ScamPattern(
            "URGENCY", "Artificial urgency",
            "Scams need you to act before you think. Genuine organisations give you time and official channels.",
            listOf("immediately", "within 24 hours", "within 2 hours", "urgent", "urgently", "last chance", "today only",
                "act now", "right now", "final notice", "final warning", "last warning", "expires today", "limited time",
                "do it now", "abhi", "turant", "jaldi"),
            weight = 15
        ),
        ScamPattern(
            "PAYMENT_ASK", "Asks you to pay or transfer",
            "Any message that ends with 'pay this amount' deserves a phone call to a number you already trust.",
            listOf("pay rs", "pay ₹", "pay inr", "transfer rs", "transfer ₹", "send rs", "send ₹", "upi id", "gpay number",
                "phonepe number", "paytm number", "scan the qr", "scan this qr", "scan qr", "registration fee", "security deposit",
                "activation fee", "verification fee", "processing charge", "advance payment", "token amount", "deposit"),
            weight = 20
        ),
        ScamPattern(
            "CREDENTIAL_LINK_BAIT", "Click-to-fix / login link bait",
            "Links that ask you to log in, verify or update are built to steal passwords. Open the official app instead.",
            listOf("click here", "click the link", "click below", "login here", "verify now", "update now", "verify your",
                "confirm your identity", "secure your account", "unusual activity", "suspicious login", "sign in to continue",
                "your account has been", "we noticed", "dear customer", "dear user", "valued customer"),
            weight = 15
        ),
        ScamPattern(
            "DELIVERY_FEE", "Parcel on hold / small fee to redeliver",
            "Courier companies never ask for a small 'customs' or 'redelivery' fee via a link. These pages steal card details.",
            listOf("parcel on hold", "package on hold", "redelivery", "re-delivery", "delivery failed", "unable to deliver",
                "incorrect address", "pay the customs", "customs fee", "delivery fee", "shipment pending", "tracking number"),
            weight = 25
        )
    )
}
