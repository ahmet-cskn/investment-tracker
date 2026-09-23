import { http, HttpResponse } from 'msw'
import { catalogTypeByName } from './fakeCatalog.js'

/**
 * In-memory stand-in for the transaction-history REST API. Like the real backend it derives
 * investmentType from the catalog and returns transactions newest first.
 */
export function createFakeTransactionsBackend(initial = []) {
  const rows = new Map(initial.map((row) => [row.id, { ...row }]))
  let nextId = initial.length + 1

  const json = (row) => {
    const investmentType = catalogTypeByName[row.name] ?? null
    return `{"id":${JSON.stringify(row.id)},"name":${JSON.stringify(row.name)},"investmentType":${JSON.stringify(investmentType)},"change":${Number(row.change).toFixed(18)},"timestamp":${JSON.stringify(row.timestamp)}}`
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

  const sortedNewestFirst = () => [...rows.values()].sort((a, b) => b.timestamp.localeCompare(a.timestamp))

  const handlers = [
    http.get('/api/transactions', () => jsonResponse(`[${sortedNewestFirst().map(json).join(',')}]`)),

    http.post('/api/transactions', async ({ request }) => {
      const { name, change, timestamp } = await request.json()
      if (!(name in catalogTypeByName)) return unknownName(name)
      const row = { id: `tx-${nextId++}`, name, change, timestamp }
      rows.set(row.id, row)
      return jsonResponse(json(row), 201)
    }),

    http.put('/api/transactions/:id', async ({ params, request }) => {
      if (!rows.has(params.id)) return notFound(params.id)
      const { name, change, timestamp } = await request.json()
      if (!(name in catalogTypeByName)) return unknownName(name)
      const row = { id: params.id, name, change, timestamp }
      rows.set(row.id, row)
      return jsonResponse(json(row))
    }),

    http.delete('/api/transactions/:id', ({ params }) => {
      if (!rows.delete(params.id)) return notFound(params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  ]

  return { handlers, rows }
}
