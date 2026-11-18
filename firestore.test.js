const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_operator_1";
const BOB_UID = "bob_operator_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validBillPayload(uid, overrides = {}) {
  return {
    userId: uid,
    billNumber: "MND-20261118-001",
    customerName: "Cash Buyer",
    itemName: "Potato / Aloo",
    weights: [55, 54, 50],
    totalBags: 3,
    totalWeightKg: 159,
    ratePerMann: 2400,
    finalCost: 9540,
    createdAt: new Date(Date.now() - 1000),
    ...overrides,
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

test("Unauthenticated user cannot read or write bills", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("bills").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("bills").doc("bill_1").set(validBillPayload(ALICE_UID)));
});

test("Authenticated owner can create, query with userId filter, and delete own bill", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("bills").doc("bill_alice_1").set(validBillPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("bills").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("bills").doc("bill_alice_1").delete()
  );
});

test("Unfiltered query and cross-user access are denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("bills").doc("bill_bob_1").set(validBillPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("bills").get());
  await assertFails(aliceDb.collection("bills").doc("bill_bob_1").get());
  await assertFails(aliceDb.collection("bills").doc("bill_bob_1").delete());
  await assertFails(
    aliceDb.collection("bills").doc("bill_spoof").set(validBillPayload(BOB_UID))
  );
});

test("Shadow fields, extra id field, future timestamp, and invalid bounds are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("bills").doc("bill_bad_id_field").set(
      validBillPayload(ALICE_UID, { id: "bill_bad_id_field" })
    )
  );
  await assertFails(
    aliceDb.collection("bills").doc("bill_shadow").set(
      validBillPayload(ALICE_UID, { isAdmin: true })
    )
  );
  await assertFails(
    aliceDb.collection("bills").doc("bill_future").set(
      validBillPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );
  await assertFails(
    aliceDb.collection("bills").doc("bill_empty_bags").set(
      validBillPayload(ALICE_UID, { weights: [], totalBags: 0 })
    )
  );
});
