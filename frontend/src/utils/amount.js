// Amounts are NUMERIC(38,18) in the backend, which a JavaScript number cannot represent exactly.
// The UI therefore keeps amounts as decimal strings and never converts them to numbers.

export const MAX_INTEGER_DIGITS = 20
export const MAX_FRACTION_DIGITS = 18

export const PLAIN_DECIMAL = /^\d+(\.\d+)?$/

/**
 * Turns an amount string into a plain decimal without padding: "5.000000000000000000" -> "5".
 * Also expands exponent notation, which the backend uses for tiny values: "1E-18" -> "0.000000000000000001".
 * Strings that are not numeric are returned unchanged.
 */
export function formatAmount(raw) {
  const value = String(raw).trim()
  const match = /^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(value)
  if (!match) return value

  const [, sign, integerPart, fractionPart = '', exponent = '0'] = match
  let digits = integerPart + fractionPart
  let pointIndex = integerPart.length + Number(exponent)

  if (pointIndex <= 0) {
    digits = '0'.repeat(1 - pointIndex) + digits
    pointIndex = 1
  } else if (pointIndex > digits.length) {
    digits += '0'.repeat(pointIndex - digits.length)
  }

  const integer = digits.slice(0, pointIndex).replace(/^0+(?=\d)/, '')
  const fraction = digits.slice(pointIndex).replace(/0+$/, '')
  return sign + integer + (fraction ? `.${fraction}` : '')
}

/** Returns an error message for an invalid amount input, or null if it is valid. */
export function validateAmount(input) {
  const value = input.trim()
  if (!value) return 'Amount is required'
  if (!PLAIN_DECIMAL.test(value)) return 'Enter a number such as 5 or 3.5'

  const [integer, fraction = ''] = formatAmount(value).split('.')
  if (/^0+$/.test(integer) && !fraction) return 'Amount must be greater than 0'
  if (integer.length > MAX_INTEGER_DIGITS) return 'Amount is too large'
  if (fraction.length > MAX_FRACTION_DIGITS) return `Use at most ${MAX_FRACTION_DIGITS} decimal places`
  return null
}

/** Returns an error message for an invalid name input, or null if it is valid. */
export function validateName(input) {
  const value = input.trim()
  if (!value) return 'Name is required'
  if (value.length > 255) return 'Name must be at most 255 characters'
  return null
}
