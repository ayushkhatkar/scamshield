// ScamShield Cloud Functions (Firebase Functions v2, Node 20)
//
// 1. notifyGuardians  - every new event under families/{code}/events is pushed to the
//                       family's GUARDIAN members via FCM.
// 2. aggregateReports - every community report increments reports/{number} counters so the
//                       app (or your backend) can serve an up-to-date blocklist.
//
// Deploy:  cd firebase && npm --prefix functions install && firebase deploy --only functions

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 10 }); // Mumbai

exports.notifyGuardians = onDocumentCreated("families/{code}/events/{eventId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  const code = event.params.code;

  const members = await admin
    .firestore()
    .collection("families")
    .doc(code)
    .collection("members")
    .where("role", "==", "GUARDIAN")
    .get();

  const tokens = members.docs
    .map((d) => d.get("fcmToken"))
    .filter((t) => typeof t === "string" && t.length > 0);
  if (tokens.length === 0) return;

  const title = `${data.memberName || "Family member"}: ${data.title || "Alert"}`;
  const body = (data.detail || "").slice(0, 200);

  const response = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: {
      type: String(data.type || ""),
      code,
      score: String(data.score ?? 0),
      level: String(data.level || ""),
      title,
      detail: body,
    },
    android: { priority: "high", notification: { channelId: "family" } },
  });

  // Clean up tokens that are no longer valid.
  const stale = [];
  response.responses.forEach((r, i) => {
    if (!r.success && r.error && /registration-token-not-registered/.test(r.error.code)) {
      stale.push(tokens[i]);
    }
  });
  if (stale.length) {
    const batch = admin.firestore().batch();
    members.docs.forEach((d) => {
      if (stale.includes(d.get("fcmToken"))) batch.update(d.ref, { fcmToken: admin.firestore.FieldValue.delete() });
    });
    await batch.commit();
  }
});

exports.aggregateReports = onDocumentCreated("reports/{reportId}", async (event) => {
  const data = event.data?.data();
  if (!data || typeof data.number !== "string") return;
  const key = data.number.replace(/[^+\d]/g, "");
  if (!key) return;
  const ref = admin.firestore().collection("scam_numbers").doc(key);
  await ref.set(
    {
      number: key,
      reports: admin.firestore.FieldValue.increment(1),
      label: data.label || "Scam",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
});
