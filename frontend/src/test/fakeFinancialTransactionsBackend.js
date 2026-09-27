import { http, HttpResponse } from 'msw'

/** In-memory stand-in for the financial-transaction REST API, newest first like the real backend. */
export function createFakeFinancialTransactionsBackend(initial = []) {
  const rows = new Map(initial.map((row) => [row.id, { ...row }]))
  let nextId = initial.length + 1

  const json = (row) =>
    `{"id":${JSON.stringify(row.id)},"name":${JSON.stringify(row.name)},"change":${Number(row.change).toFixed(18)},"date":${JSON.stringify(row.date)}}`
  const jsonResponse = (body, status = 200) =>
    new HttpResponse(body, { status, headers: { 'Content-Type': 'application/json' } })
  const notFound = (id) =>
    HttpResponse.json(
      { title: 'Financial transaction not found', status: 404, detail: `Financial transaction with id ${id} not found` },
      { status: 404 },
    )

  const sortedNewestFirst = () =>
    [...rows.values()].sort(
      (a, b) => b.date.localeCompare(a.date) || a.name.localeCompare(b.name) || a.id.localeCompare(b.id),
    )

  const handlers = [
    http.get('/api/financial-transactions', () => jsonResponse(`[${sortedNewestFirst().map(json).join(',')}]`)),

    http.post('/api/financial-transactions', async ({ request }) => {
      const { name, change, date } = await request.json()
      const row = { id: `ftx-${nextId++}`, name, change, date }
      rows.set(row.id, row)
      return jsonResponse(json(row), 201)
    }),

    http.put('/api/financial-transactions/:id', async ({ params, request }) => {
      if (!rows.has(params.id)) return notFound(params.id)
      const { name, change, date } = await request.json()
      const row = { id: params.id, name, change, date }
      rows.set(row.id, row)
      return jsonResponse(json(row))
    }),

    http.delete('/api/financial-transactions/:id', ({ params }) => {
      if (!rows.delete(params.id)) return notFound(params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  ]

  return { handlers, rows }
}
