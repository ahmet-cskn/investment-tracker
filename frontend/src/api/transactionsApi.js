import { SIGNED_PLAIN_DECIMAL } from '../utils/amount.js'
import { parseJsonKeepingDecimals, request } from './httpClient.js'

/**
 * Client for the investment-service transactions REST API.
 *
 * @typedef {{ id: string, name: string, investmentType: string, change: string, date: string, worth: string | null }} Transaction
 *   `change` and `worth` are decimal strings (e.g. "-1.5"), never numbers, to avoid losing precision; `change`
 *   may be negative or zero. `date` is a plain "YYYY-MM-DD" string. `investmentType` and `worth` are derived
 *   server-side and are not settable here: `worth` is the change's value in USD on `date` (the day's price
 *   times the change), or null when no price could be obtained.
 * @typedef {{ name: string, change: string, date: string }} TransactionInput
 */

export { ApiError } from './httpClient.js'

const BASE_URL = '/api/transactions'
const DECIMAL_FIELDS = ['change', 'worth']

function serializeTransaction({ name, change, date }) {
  if (!SIGNED_PLAIN_DECIMAL.test(change)) {
    throw new TypeError(`Invalid change: ${change}`)
  }
  return `{"name":${JSON.stringify(name)},"change":${change},"date":${JSON.stringify(date)}}`
}

async function requestJson(path, options) {
  const text = await request(BASE_URL + path, options)
  return text === null ? null : parseJsonKeepingDecimals(text, DECIMAL_FIELDS)
}

/** @returns {Promise<Transaction[]>} */
export function listTransactions() {
  return requestJson('')
}

// async: serializeTransaction can throw synchronously (an invalid change), and this turns that into a
// rejected promise, matching every other function here and what callers (mutateAsync) expect
/** @param {TransactionInput} input @returns {Promise<Transaction>} */
export async function createTransaction(input) {
  return requestJson('', { method: 'POST', body: serializeTransaction(input) })
}

/** @param {{ id: string } & TransactionInput} transaction @returns {Promise<Transaction>} */
export async function updateTransaction({ id, ...input }) {
  return requestJson(`/${encodeURIComponent(id)}`, { method: 'PUT', body: serializeTransaction(input) })
}

/** @param {string} id @returns {Promise<null>} */
export function deleteTransaction(id) {
  return requestJson(`/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
