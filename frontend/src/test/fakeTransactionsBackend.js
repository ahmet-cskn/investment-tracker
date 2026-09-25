import { http, HttpResponse } from 'msw'
import { catalogTypeByName } from './fakeCatalog.js'

// A price per unit for each investment, so the fake can work a worth out like the real backend does
// (price times change, and price times amount for the portfolio). Tests override entries through `prices`; null means "no price could be obtained".
const DEFAULT_PRICES = { Bitcoin: 50000, Ethereum: 3000, Gold: 100, 'S&P500': 500, Silver: 2 }

/**
 * In-memory stand-in for the transaction-history REST API. Like the real backend it derives
 * investmentType from the catalog and worth from a price, and returns transactions newest first.
 */
export function createFakeTransactionsBackend(initial = [], { prices = {} } = {}) {
  const priceOf = (name) => ({ ...DEFAULT_PRICES, ...prices })[name] ?? null
  const rows = new Map(initial.map((row) => [row.id, { ...row }]))
  let nextId = initial.length + 1

  const json = (row) => {
    const investmentType = catalogTypeByName[row.name] ?? null
    const price = priceOf(row.name)
    const worth = price === null ? 'null' : (Number(row.change) * price).toFixed(18)
    return `{"id":${JSON.stringify(row.id)},"name":${JSON.stringify(row.name)},"investmentType":${JSON.stringify(investmentType)},"change":${Number(row.change).toFixed(18)},"date":${JSON.stringify(row.date)},"worth":${worth}}`
  }
  const jsonResponse = (body, status = 200) =>
    new HttpResponse(body, { status, headers: { 'Content-Type': 'application/json' } })
  const notFound = (id) =>
    HttpResponse.json(
      { title: 'Transaction not found', status: 404, detail: `Transaction with id ${id} not found` },
      { status: 404 },
    )
  const unknownName = (name) =>
    HttpResponse.json(
      {
        title: 'Invalid investment name',
        status: 400,
        detail: `Unknown investment name: ${name}`,
        errors: [{ field: 'name', message: `Unknown investment name: ${name}` }],
      },
      { status: 400 },
    )

  // Like the real backend: newest first, with name and id breaking ties on the same day
  const sortedNewestFirst = () =>
    [...rows.values()].sort(
      (a, b) => b.date.localeCompare(a.date) || a.name.localeCompare(b.name) || a.id.localeCompare(b.id),
    )

  const handlers = [
    http.get('/api/transactions', () => jsonResponse(`[${sortedNewestFirst().map(json).join(',')}]`)),

    http.post('/api/transactions', async ({ request }) => {
      const { name, change, date } = await request.json()
      if (!(name in catalogTypeByName)) return unknownName(name)
      const row = { id: `tx-${nextId++}`, name, change, date }
      rows.set(row.id, row)
      return jsonResponse(json(row), 201)
    }),

    http.put('/api/transactions/:id', async ({ params, request }) => {
      if (!rows.has(params.id)) return notFound(params.id)
      const { name, change, date } = await request.json()
      if (!(name in catalogTypeByName)) return unknownName(name)
      const row = { id: params.id, name, change, date }
      rows.set(row.id, row)
      return jsonResponse(json(row))
    }),

    http.delete('/api/transactions/:id', ({ params }) => {
      if (!rows.delete(params.id)) return notFound(params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  ]

  return { handlers, rows, priceOf }
}
