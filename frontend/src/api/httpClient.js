/**
 * Shared HTTP plumbing for the investment-service REST API, used by investmentsApi.js, transactionsApi.js
 * and catalogApi.js so each one only has to define its own URL, request shape and response fields.
 *
 * @typedef {{ field: string, message: string }} FieldError
 */

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

/** Sends a request and returns the raw response body (`null` for a 204), or throws an ApiError. */
export async function request(url, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(url, { method, headers, body })
  } catch {
    throw new ApiError('Could not reach the server. Is the backend running?')
  }

  if (response.status === 204) return null
  const text = await response.text()
  if (!response.ok) throw toApiError(response.status, text)
  return text
}

/**
 * Parses a JSON response while keeping the given fields as decimal strings, never numbers, since
 * they are NUMERIC(38,18) columns in the backend that a JavaScript number cannot represent exactly.
 */
export function parseJsonKeepingDecimals(text, fieldNames) {
  const pattern = new RegExp(`("(?:${fieldNames.join('|')})"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)`, 'g')
  return JSON.parse(text.replace(pattern, '$1"$2"'))
}
