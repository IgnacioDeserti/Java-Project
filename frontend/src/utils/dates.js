/**
 * The API sends due dates as plain calendar days ("2026-12-24"). `new Date("2026-12-24")`
 * would parse that as UTC midnight, which renders as the *previous* day for anyone west
 * of Greenwich (including all of the Americas) — so parse the parts explicitly and build
 * a local date instead.
 */
export function parseLocalDate(isoDay) {
  if (!isoDay) return null;
  const [year, month, day] = isoDay.split("-").map(Number);
  if (!year || !month || !day) return null;
  return new Date(year, month - 1, day);
}

function startOfToday() {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

/** "overdue" | "soon" (today or the next two days) | "normal" */
export function dueDateState(isoDay) {
  const due = parseLocalDate(isoDay);
  if (!due) return "normal";

  const today = startOfToday();
  const daysAway = Math.round((due - today) / 86400000);

  if (daysAway < 0) return "overdue";
  if (daysAway <= 2) return "soon";
  return "normal";
}

/** Short, human label: "Today", "Tomorrow", "Yesterday", "Dec 24", or "Dec 24, 2027". */
export function formatDueDate(isoDay) {
  const due = parseLocalDate(isoDay);
  if (!due) return "";

  const today = startOfToday();
  const daysAway = Math.round((due - today) / 86400000);

  if (daysAway === 0) return "Today";
  if (daysAway === 1) return "Tomorrow";
  if (daysAway === -1) return "Yesterday";

  const sameYear = due.getFullYear() === today.getFullYear();
  return due.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    ...(sameYear ? {} : { year: "numeric" }),
  });
}
