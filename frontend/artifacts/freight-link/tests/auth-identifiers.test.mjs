import assert from "node:assert/strict";
import test from "node:test";
import * as identifiers from "../src/lib/auth-identifiers.js";

test("normalizes Ethiopian local phone formats used by registration", () => {
  assert.equal(identifiers.normalizeEthiopianPhone("911 234 567"), "+251911234567");
  assert.equal(identifiers.normalizeEthiopianPhone("0911-234-567"), "+251911234567");
  assert.equal(identifiers.normalizeEthiopianPhone("251911234567"), "+251911234567");
  assert.equal(identifiers.normalizeEthiopianPhone("+251 911 234 567"), "+251911234567");
});

test("preserves email login identifiers while normalizing phone login identifiers", () => {
  assert.equal(identifiers.getLoginIdentifier("User@example.com", false), "User@example.com");
  assert.equal(identifiers.getLoginIdentifier("0911 234 567", true), "+251911234567");
});

test("rejects identifiers that do not satisfy the backend phone rule", () => {
  assert.equal(identifiers.isValidPhoneIdentifier("911234567"), true);
  assert.equal(identifiers.isValidPhoneIdentifier("not-a-phone"), false);
  assert.equal(identifiers.isValidPhoneIdentifier("12345"), false);
});
