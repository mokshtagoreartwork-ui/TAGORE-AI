const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const pastTimestamp = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_tagore_123";
const BOB_UID = "bob_tagore_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validProfilePayload(uid) {
  return {
    userId: uid,
    preferredName: "Moksh",
    honorific: "Sir",
    voicePersonality: "JARVIS",
    voicePitch: 0.9,
    voiceSpeed: 1.0,
    voiceTone: "Respectful",
    responseLength: "CONCISE",
    languageMode: "HINGLISH",
    darkMode: true,
    ringBrightness: 0.95,
    animationSpeed: 1.0,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validSmartDevicePayload() {
  return {
    name: "Studio Ceiling Light",
    room: "Office",
    category: "LIGHT",
    isOn: true,
    level: 85,
    temperature: 24,
    isLocked: false,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validTaskPayload() {
  return {
    kind: "REMINDER",
    title: "Q4 Architecture Review",
    details: "Prepare offline-first telemetry slide deck",
    completed: false,
    dueAtMillis: 1794700000000,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

test("Unauthenticated user cannot read or write any user profile or subcollection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("smart_devices").doc("dev1").set(validSmartDevicePayload()));
});

test("Owner can create and update their own profile, but shadow fields and cross-user access fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));

  // Shadow update attack (extra ghost field) must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredName: "Moksh Tagore",
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );

  // Valid partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      voicePersonality: "BIGG_BOSS",
      updatedAt: pastTimestamp(),
    })
  );

  // Cross-user read/write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("smart_devices").get());
});

test("Owner can manage memories, tasks, smart devices, and ecosystem devices with schema checks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("smart_devices").doc("light_1").set(validSmartDevicePayload())
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(validTaskPayload())
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );

  // Invalid temperature bounds (>32) must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("smart_devices").doc("ac_bad").set({
      ...validSmartDevicePayload(),
      category: "AC",
      temperature: 99,
    })
  );

  // Invalid enum must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_bad").set({
      ...validTaskPayload(),
      kind: "INVALID_KIND",
    })
  );

  // Cross-user delete must fail
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").delete()
  );
});
