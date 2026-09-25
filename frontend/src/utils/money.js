// Worths are USD amounts kept as decimal strings, like every other amount (see amount.js). Intl.NumberFormat
// reads a string as an exact decimal, so nothing is rounded through a floating point number on the way to
// cents: "0.005" is exactly half a cent and rounds up, which a double would not reliably do.

/**
 * Formats a USD amount given as a decimal string for display, e.g. "1234.5678" -> "$1,234.57".
 * A tiny negative that rounds to zero shows as "$0.00", not "-$0.00".
 * `locale` is the visitor's own unless given (the tests pass one, to be independent of the machine's).
 */
export function formatUsd(value, locale) {
  return new Intl.NumberFormat(locale, { style: 'currency', currency: 'USD', signDisplay: 'negative' }).format(value)
}
