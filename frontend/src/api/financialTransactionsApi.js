import { SIGNED_PLAIN_DECIMAL } from '../utils/amount.js'
import { parseJsonKeepingDecimals, request } from './httpClient.js'

/**
 * Client for the investment-service financial-transactions REST API: everyday income and spending,
 * unrelated to an investment.
 *
 * @typedef {{ id: string, name: string, change: string, date: string }} FinancialTransaction
 *   `name` is free text (e.g. "Groceries" or "Salary"). `change` is a decimal string (e.g. "-42.5"), never
 *   a number, to avoid losing precision; positive for income, negative for spending. `date` is a plain
 *   "YYYY-MM-DD" string.
 * @typedef {{ name: string, change: string, date: string }} FinancialTransactionInput
 */

export { ApiError } from './httpClient.js'

const BASE_URL = '/api/financial-transactions'
const DECIMAL_FIELDS = ['change']

function serializeFinancialTransaction({ name, change, date }) {
  if (!SIGNED_PLAIN_DECIMAL.test(change)) {
    throw new TypeError(`Invalid change: ${change}`)
  }
  return `{"name":${JSON.stringify(name)},"change":${change},"date":${JSON.stringify(date)}}`
}

async function requestJson(path, options) {
  const text = await request(BASE_URL + path, options)
  return text === null ? null : parseJsonKeepingDecimals(text, DECIMAL_FIELDS)
}

/** @returns {Promise<FinancialTransaction[]>} */
export function listFinancialTransactions() {
  return requestJson('')
}

// async: serializeFinancialTransaction can throw synchronously (an invalid change), turned into a
// rejected promise, matching every other function here and what callers (mutateAsync) expect
/** @param {FinancialTransactionInput} input @returns {Promise<FinancialTransaction>} */
export async function createFinancialTransaction(input) {
  return requestJson('', { method: 'POST', body: serializeFinancialTransaction(input) })
}

/** @param {{ id: string } & FinancialTransactionInput} transaction @returns {Promise<FinancialTransaction>} */
export async function updateFinancialTransaction({ id, ...input }) {
  return requestJson(`/${encodeURIComponent(id)}`, { method: 'PUT', body: serializeFinancialTransaction(input) })
}

/** @param {string} id @returns {Promise<null>} */
export function deleteFinancialTransaction(id) {
  return requestJson(`/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
