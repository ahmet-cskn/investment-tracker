// A <input type="datetime-local"> has no timezone: the browser's Date constructor treats a value like
// "2026-01-15T10:00" as local time, which is exactly the wall-clock time the user picked. That makes the
// round trip to/from the API's UTC ISO strings ("2026-01-15T10:00:00Z") plain Date arithmetic, no library.

/** Converts an ISO-8601 timestamp (as returned by the API) to a `datetime-local` input's value. */
export function toDateTimeLocalValue(isoString) {
  const date = new Date(isoString)
  const pad = (n) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

/** Converts a `datetime-local` input's value to an ISO-8601 UTC timestamp for the API. */
export function fromDateTimeLocalValue(value) {
  return new Date(value).toISOString()
}

/** Formats an ISO-8601 timestamp for display in the visitor's own locale and timezone. */
export function formatTimestamp(isoString) {
  return new Date(isoString).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
