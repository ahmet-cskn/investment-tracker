import { http, HttpResponse } from 'msw'

/**
 * In-memory stand-in for investment-service. Like the real backend it pads amounts to 18 decimal places
 * and returns them as JSON numbers, so the UI's amount handling is exercised realistically.
 */
export function createFakeBackend(initial = []) {
  const rows = new Map(initial.map((row) => [row.id, { ...row }]))
  let nextId = initial.length + 1

  const json = (row) =>
    `{"id":${JSON.stringify(row.id)},"name":${JSON.stringify(row.name)},"amount":${Number(row.amount).toFixed(18)}}`
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
