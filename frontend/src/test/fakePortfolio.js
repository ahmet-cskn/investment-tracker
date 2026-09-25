import { http, HttpResponse } from 'msw'
import { catalogTypeByName } from './fakeCatalog.js'

/**
 * Stand-in for GET /api/portfolio that computes from the other two fakes' in-memory rows on every request,
 * like the real backend: initial amount plus the sum of the transaction changes, per name, sorted by name.
 * Uses plain numbers, so tests should stick to values that add exactly (integers, halves).
 */
export function fakePortfolioHandler(investmentsBackend, transactionsBackend) {
  return http.get('/api/portfolio', () => {
    const totals = new Map()
    for (const row of investmentsBackend.rows.values()) {
      totals.set(row.name, (totals.get(row.name) ?? 0) + Number(row.amount))
    }
    for (const row of transactionsBackend.rows.values()) {
      totals.set(row.name, (totals.get(row.name) ?? 0) + Number(row.change))
    }

    const entries = [...totals.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([name, total]) => {
        const investmentType = catalogTypeByName[name] ?? null
        return (
          `{"name":${JSON.stringify(name)},"investmentType":${JSON.stringify(investmentType)},` +
          `"amount":${total.toFixed(18)},"worth":1.000000000000000000}`
        )
      })

    return new HttpResponse(`[${entries.join(',')}]`, { headers: { 'Content-Type': 'application/json' } })
  })
}
