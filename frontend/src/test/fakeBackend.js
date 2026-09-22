import { http, HttpResponse } from 'msw'

// Mirrors the investment_catalog seed data (services/investment-service .../004-create-investment-catalog-table.yaml).
// Like the real backend, investmentType/worth are derived from name, never stored or accepted as input;
// a name outside this catalog gets no type or worth, same as an entry a real backend would reject.
const CATALOG = {
  Gold: 'Precious Metal',
  Silver: 'Precious Metal',
  Bitcoin: 'Cryptocurrency',
  Ethereum: 'Cryptocurrency',
  'S&P500': 'Stock',
}

/**
 * In-memory stand-in for investment-service. Like the real backend it pads amounts to 18 decimal places
 * and returns them as JSON numbers, so the UI's amount handling is exercised realistically.
 */
export function createFakeBackend(initial = []) {
  const rows = new Map(initial.map((row) => [row.id, { ...row }]))
  let nextId = initial.length + 1

  const json = (row) => {
    const investmentType = CATALOG[row.name] ?? null
    const worth = investmentType ? '1.000000000000000000' : null
    return `{"id":${JSON.stringify(row.id)},"name":${JSON.stringify(row.name)},"amount":${Number(row.amount).toFixed(18)},"investmentType":${JSON.stringify(investmentType)},"worth":${worth ?? 'null'}}`
  }
  const jsonResponse = (body, status = 200) =>
    new HttpResponse(body, { status, headers: { 'Content-Type': 'application/json' } })
  const notFound = (id) =>
    HttpResponse.json(
      { title: 'Investment not found', status: 404, detail: `Investment with id ${id} not found` },
      { status: 404 },
    )

  const handlers = [
    http.get('/api/investments', () => jsonResponse(`[${[...rows.values()].map(json).join(',')}]`)),

    http.post('/api/investments', async ({ request }) => {
      const { name, amount } = await request.json()
      const row = { id: `id-${nextId++}`, name, amount }
      rows.set(row.id, row)
      return jsonResponse(json(row), 201)
    }),

    http.put('/api/investments/:id', async ({ params, request }) => {
      if (!rows.has(params.id)) return notFound(params.id)
      const { name, amount } = await request.json()
      const row = { id: params.id, name, amount }
      rows.set(row.id, row)
      return jsonResponse(json(row))
    }),

    http.delete('/api/investments/:id', ({ params }) => {
      if (!rows.delete(params.id)) return notFound(params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  ]

  return { handlers, rows }
}
