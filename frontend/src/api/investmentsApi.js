import { PLAIN_DECIMAL } from '../utils/amount.js'
import { parseJsonKeepingDecimals, request } from './httpClient.js'

/**
 * Client for the investment-service investments REST API.
 *
 * @typedef {{ id: string, name: string, amount: string, investmentType: string, worth: string }} Investment
 *   `amount` and `worth` are decimal strings (e.g. "3.5"), never numbers, to avoid losing precision.
 *   `investmentType` and `worth` are derived server-side from `name`; they are not settable here.
 * @typedef {{ name: string, amount: string }} InvestmentInput
 *   `amount` must be a plain decimal string such as "3.5".
 */

export { ApiError } from './httpClient.js'

const BASE_URL = '/api/investments'
const DECIMAL_FIELDS = ['amount', 'worth']

// The amount is written into the JSON as a number literal (not a string) without going through a double.
function serializeInvestment({ name, amount }) {
  if (!PLAIN_DECIMAL.test(amount)) {
    throw new TypeError(`Invalid amount: ${amount}`)
  }
  return `{"name":${JSON.stringify(name)},"amount":${amount}}`
}

async function requestJson(path, options) {
  const text = await request(BASE_URL + path, options)
  return text === null ? null : parseJsonKeepingDecimals(text, DECIMAL_FIELDS)
}

/** @returns {Promise<Investment[]>} */
export function listInvestments() {
  return requestJson('')
}

// async: serializeInvestment can throw synchronously (an invalid amount), and this turns that into a
// rejected promise, matching every other function here and what callers (mutateAsync) expect
/** @param {InvestmentInput} input @returns {Promise<Investment>} */
export async function createInvestment(input) {
  return requestJson('', { method: 'POST', body: serializeInvestment(input) })
}

/** @param {{ id: string } & InvestmentInput} investment @returns {Promise<Investment>} */
export async function updateInvestment({ id, ...input }) {
  return requestJson(`/${encodeURIComponent(id)}`, { method: 'PUT', body: serializeInvestment(input) })
}

/** @param {string} id @returns {Promise<null>} */
export function deleteInvestment(id) {
  return requestJson(`/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
