import { describe, expect, it } from "vitest";
import { dueDateState, formatDueDate, parseLocalDate } from "./dates.js";

/** An ISO day string ("2026-08-27") offset from today, built in local time. */
function isoDaysFromToday(offset) {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

describe("parseLocalDate", () => {
  it("parses a calendar day into local midnight, not UTC midnight", () => {
    const parsed = parseLocalDate("2026-12-24");

    // The regression this guards: new Date("2026-12-24") is UTC midnight, which is still
    // the 23rd anywhere west of Greenwich — the date would render a day early.
    expect(parsed.getFullYear()).toBe(2026);
    expect(parsed.getMonth()).toBe(11); // December, zero-indexed
    expect(parsed.getDate()).toBe(24);
    expect(parsed.getHours()).toBe(0);
  });

  it("returns null for empty or malformed input", () => {
    expect(parseLocalDate("")).toBeNull();
    expect(parseLocalDate(null)).toBeNull();
    expect(parseLocalDate("not-a-date")).toBeNull();
  });
});

describe("dueDateState", () => {
  it("flags past dates as overdue", () => {
    expect(dueDateState(isoDaysFromToday(-1))).toBe("overdue");
    expect(dueDateState(isoDaysFromToday(-30))).toBe("overdue");
  });

  it("flags today and the next two days as soon", () => {
    expect(dueDateState(isoDaysFromToday(0))).toBe("soon");
    expect(dueDateState(isoDaysFromToday(1))).toBe("soon");
    expect(dueDateState(isoDaysFromToday(2))).toBe("soon");
  });

  it("leaves anything further out unstyled", () => {
    expect(dueDateState(isoDaysFromToday(3))).toBe("normal");
    expect(dueDateState(isoDaysFromToday(90))).toBe("normal");
  });

  it("treats a missing due date as normal rather than throwing", () => {
    expect(dueDateState(null)).toBe("normal");
  });
});

describe("formatDueDate", () => {
  it("uses relative wording for the days around today", () => {
    expect(formatDueDate(isoDaysFromToday(0))).toBe("Today");
    expect(formatDueDate(isoDaysFromToday(1))).toBe("Tomorrow");
    expect(formatDueDate(isoDaysFromToday(-1))).toBe("Yesterday");
  });

  it("falls back to a calendar date further out", () => {
    const formatted = formatDueDate(isoDaysFromToday(45));
    expect(formatted).not.toMatch(/Today|Tomorrow|Yesterday/);
    expect(formatted.length).toBeGreaterThan(0);
  });

  it("returns an empty string when there is no due date", () => {
    expect(formatDueDate(null)).toBe("");
  });
});
