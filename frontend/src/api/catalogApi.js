import { request } from './httpClient.js'

/**
 * Client for the investment-service catalog REST API: the fixed list of known investment names and
 * their types, used to populate the transaction modal's investment dropdown.
 *
 * @typedef {{ name: string, investmentType: string }} CatalogEntry
 */

const BASE_URL = '/api/catalog'

/** @returns {Promise<CatalogEntry[]>} */
export async function listCatalog() {
  const text = await request(BASE_URL)
  return JSON.parse(text)
}
