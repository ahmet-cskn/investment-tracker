// A transaction's date is a plain "YYYY-MM-DD" string: no time of day and no timezone. It is only ever
// passed around and sent to the API as that string. A Date is built solely to format one for display,
// and from its parts in local time: new Date("2026-01-15") would read the string as UTC midnight and show
// the previous day anywhere west of UTC.

/** Formats a "YYYY-MM-DD" date for display in the visitor's locale, e.g. "Jan 15, 2026". */
export function formatDate(isoDate) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, { dateStyle: 'medium' })
}
