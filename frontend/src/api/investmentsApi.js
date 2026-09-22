import { PLAIN_DECIMAL } from '../utils/amount.js'

/**
 * Client for the investment-service REST API.
 *
 * @typedef {{ id: string, name: string, amount: string, investmentType: string, worth: string }} Investment
 *   `amount` and `worth` are decimal strings (e.g. "3.5"), never numbers, to avoid losing precision.
 *   `investmentType` and `worth` are derived server-side from `name`; they are not settable here.
 * @typedef {{ name: string, amount: string }} InvestmentInput
 *   `amount` must be a plain decimal string such as "3.5".
 * @typedef {{ field: string, message: string }} FieldError
 */

const BASE_URL = '/api/investments'

export class ApiError extends Error {
  /**
   * @param {string} message
   * @param {{ status?: number, fieldErrors?: FieldError[] }} [details]
   *   `status` is 0 when the server could not be reached at all.
   */
  constructor(message, { status = 0, fieldErrors = [] } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

// JSON.parse would turn decimals into doubles and silently round them, so they are quoted first.
// worth is fixed at 1 today but numeric(38,18) like amount, so it gets the same treatment up front.
const DECIMAL_VALUE = /("(?:amount|worth)"\s*:\s*)(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g

function parseJsonKeepingAmounts(text) {
  return JSON.parse(text.replace(DECIMAL_VALUE, '$1"$2"'))
}

// The amount is written into the JSON as a number literal (not a string) without going through a double.
function serializeInvestment({ name, amount }) {
  if (!PLAIN_DECIMAL.test(amount)) {
    throw new TypeError(`Invalid amount: ${amount}`)
  }
  return `{"name":${JSON.stringify(name)},"amount":${amount}}`
}

function toApiError(status, text) {
  let problem = {}
  try {
    problem = JSON.parse(text)
  } catch {
    // Not a JSON body (e.g. an error page from a proxy); fall back to the generic message
  }
  return new ApiError(problem.detail || problem.title || `Request failed (status ${status})`, {
    status,
    fieldErrors: Array.isArray(problem.errors) ? problem.errors : [],
  })
}

async function request(path = '', { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(BASE_URL + path, { method, headers, body })
  } catch {
    throw new ApiError('Could not reach the server. Is the backend running?')
  }

  if (response.status === 204) return null
  const text = await response.text()
  if (!response.ok) throw toApiError(response.status, text)
  return parseJsonKeepingAmounts(text)
}

/** @returns {Promise<Investment[]>} */
export function listInvestments() {
  return request()
}

/** @param {InvestmentInput} input @returns {Promise<Investment>} */
export async function createInvestment(input) {
  return request('', { method: 'POST', body: serializeInvestment(input) })
}

/** @param {{ id: string } & InvestmentInput} investment @returns {Promise<Investment>} */
export async function updateInvestment({ id, ...input }) {
  return request(`/${encodeURIComponent(id)}`, { method: 'PUT', body: serializeInvestment(input) })
}

/** @param {string} id @returns {Promise<null>} */
export function deleteInvestment(id) {
  return request(`/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
