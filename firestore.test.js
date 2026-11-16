const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_123";
const BOB_UID = "bob_attacker_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validShortPayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    title: "20s AI Productivity Hack",
    topic: "Three keyboard shortcuts that save an hour a day",
    style: "Viral Hook",
    durationSec: 20,
    voiceProfile: "Studio Pro",
    captionStyle: "Neon Pop",
    backdropTheme: "Cyber Aurora",
    voiceoverScript: "Stop wasting hours clicking menus. Here are three shortcuts that double your speed in twenty seconds.",
    scenes: [
      {
        index: 0,
        startMs: 0,
        endMs: 4000,
        caption: "Stop wasting hours clicking menus.",
        emphasisWord: "hours",
        visualCue: "Fast zoom on desk setup"
      }
    ],
    securityVerified: true,
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller: cannot list or create shorts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/shorts`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/shorts`).doc("short_1").set(validShortPayload(ALICE_UID))
  );
});

test("Owner: can create, read, list, update, and delete valid short project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/shorts`).doc("short_1");
  await assertSucceeds(ref.set(validShortPayload(ALICE_UID)));
  await assertSucceeds(ref.get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/shorts`).get());
  await assertSucceeds(ref.update({ title: "Updated 20s Short", updatedAt: new Date() }));
  await assertSucceeds(ref.delete());
});

test("Cross-user isolation: Bob cannot read, list, write, or delete Alice's shorts", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection(`users/${ALICE_UID}/shorts`).doc("short_secret").set(validShortPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/shorts`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/shorts`).doc("short_secret").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/shorts`).doc("short_secret").update({ title: "Hacked" }));
  await assertFails(bobDb.collection(`users/${ALICE_UID}/shorts`).doc("short_secret").delete());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/shorts`).doc("short_new").set(validShortPayload(BOB_UID)));
});

test("Shadow update & identity spoofing: rejects ghost fields and forged userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/shorts`).doc("short_ghost").set(
      validShortPayload(ALICE_UID, { isAdmin: true })
    )
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/shorts`).doc("short_spoof").set(
      validShortPayload(BOB_UID)
    )
  );
});

test("Denial-of-Wallet & ID poisoning: rejects oversized strings, invalid enum, and invalid doc ID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/shorts`).doc("bad$id!").set(validShortPayload(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/shorts`).doc("short_big").set(
      validShortPayload(ALICE_UID, { title: "x".repeat(150) })
    )
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/shorts`).doc("short_enum").set(
      validShortPayload(ALICE_UID, { style: "HackedStyle" })
    )
  );
});
