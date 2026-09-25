import { parseJsonKeepingDecimals, request } from './httpClient.js'

/**
 * Client for the investment-service portfolio REST API: what the user currently holds of each
 * investment (its initial amount plus the sum of its transaction changes). Read-only and computed by
 * the backend on every request.
 *
 * @typedef {{ name: string, investmentType: string | null, amount: string, worth: string }} PortfolioEntry
 *   `amount` and `worth` are decimal strings (e.g. "6" or "-2.5"), never numbers, to avoid losing precision.
 */

const BASE_URL = '/api/portfolio'
const DECIMAL_FIELDS = ['amount', 'worth']

/** @returns {Promise<PortfolioEntry[]>} */
export async function listPortfolio() {
  const text = await request(BASE_URL)
  return parseJsonKeepingDecimals(text, DECIMAL_FIELDS)
}
