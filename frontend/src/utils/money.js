import { formatAmount, MAX_FRACTION_DIGITS } from './amount.js'

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

/**
 * Adds up the worths of portfolio entries, exactly: as scaled BigInts, never as floating point numbers.
 * Entries without a worth (no price could be obtained) are left out and counted in `unpriced`.
 * Returns the total as a decimal string, usable with formatUsd.
 */
export function sumWorth(entries) {
  let total = 0n
  let unpriced = 0
  for (const { worth } of entries) {
    if (worth == null) {
      unpriced++
      continue
    }
    const [integer, fraction = ''] = formatAmount(worth).replace('-', '').split('.')
    const magnitude = BigInt(integer + fraction.padEnd(MAX_FRACTION_DIGITS, '0'))
    total += worth.trim().startsWith('-') ? -magnitude : magnitude
  }
  const digits = (total < 0n ? -total : total).toString().padStart(MAX_FRACTION_DIGITS + 1, '0')
  const cut = digits.length - MAX_FRACTION_DIGITS
  return { total: `${total < 0n ? '-' : ''}${digits.slice(0, cut)}.${digits.slice(cut)}`, unpriced }
}
