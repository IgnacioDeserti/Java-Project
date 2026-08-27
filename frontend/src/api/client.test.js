import { describe, expect, it } from "vitest";
import { errorMessage, isEmailNotVerified } from "./client.js";

describe("errorMessage", () => {
  it("prefers the backend's { error } body", () => {
    const err = { response: { data: { error: "Email already registered" } } };
    expect(errorMessage(err)).toBe("Email already registered");
  });

  it("falls back to the Axios error message when there is no response body", () => {
    const err = { message: "Network Error" };
    expect(errorMessage(err)).toBe("Network Error");
  });

  it("falls back to the provided default when nothing else is available", () => {
    expect(errorMessage({}, "Something went wrong")).toBe("Something went wrong");
  });
});

describe("isEmailNotVerified", () => {
  it("recognizes the EMAIL_NOT_VERIFIED error code", () => {
    const err = { response: { data: { code: "EMAIL_NOT_VERIFIED" } } };
    expect(isEmailNotVerified(err)).toBe(true);
  });

  it("returns false for any other error", () => {
    const err = { response: { data: { error: "Invalid credentials" } } };
    expect(isEmailNotVerified(err)).toBe(false);
  });

  it("returns false when there is no response at all", () => {
    expect(isEmailNotVerified(new Error("boom"))).toBe(false);
  });
});
