import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server.js'
import {
  ApiError,
  createInvestment,
  deleteInvestment,
  listInvestments,
  updateInvestment,
} from './investmentsApi.js'

const jsonText = (text, status = 200) =>
  new HttpResponse(text, { status, headers: { 'Content-Type': 'application/json' } })

describe('listInvestments', () => {
  it('returns amounts as strings so no precision is lost', async () => {
    server.use(
      http.get('/api/investments', () =>
        jsonText('[{"id":"a","name":"Gold","amount":5.000000000000000000},{"id":"b","name":"ETH","amount":1E-18},{"id":"c","name":"x","amount":0.123456789012345678}]'),
      ),
    )

    await expect(listInvestments()).resolves.toEqual([
      { id: 'a', name: 'Gold', amount: '5.000000000000000000' },
      { id: 'b', name: 'ETH', amount: '1E-18' },
      { id: 'c', name: 'x', amount: '0.123456789012345678' },
    ])
  })

  it('returns worth as a string too, since it is the same NUMERIC(38,18) column type as amount', async () => {
    server.use(
      http.get('/api/investments', () =>
        jsonText('[{"id":"a","name":"Gold","amount":5,"investmentType":"Precious Metal","worth":1.000000000000000000}]'),
      ),
    )

    await expect(listInvestments()).resolves.toEqual([
      { id: 'a', name: 'Gold', amount: '5', investmentType: 'Precious Metal', worth: '1.000000000000000000' },
    ])
  })

  it('leaves a null worth (e.g. not yet derived) as null rather than "null"', async () => {
    server.use(
      http.get('/api/investments', () =>
        jsonText('[{"id":"a","name":"Gold","amount":5,"investmentType":null,"worth":null}]'),
      ),
    )

    await expect(listInvestments()).resolves.toEqual([
      { id: 'a', name: 'Gold', amount: '5', investmentType: null, worth: null },
    ])
  })

  it('does not touch the word "amount" inside a name', async () => {
    server.use(
      http.get('/api/investments', () => jsonText('[{"id":"a","name":"say \\"amount\\":5","amount":1}]')),
    )

    await expect(listInvestments()).resolves.toEqual([{ id: 'a', name: 'say "amount":5', amount: '1' }])
  })
})

describe('createInvestment', () => {
  it('sends the amount as an exact JSON number literal', async () => {
    let sentBody
    server.use(
      http.post('/api/investments', async ({ request }) => {
        sentBody = await request.text()
        return jsonText('{"id":"a","name":"ETH","amount":0.123456789012345678}', 201)
      }),
    )

    const created = await createInvestment({ name: 'ETH', amount: '0.123456789012345678' })

    expect(sentBody).toBe('{"name":"ETH","amount":0.123456789012345678}')
    expect(created).toEqual({ id: 'a', name: 'ETH', amount: '0.123456789012345678' })
  })

  it('escapes the name', async () => {
    let sentBody
    server.use(
      http.post('/api/investments', async ({ request }) => {
        sentBody = await request.text()
        return jsonText('{"id":"a","name":"x","amount":1}', 201)
      }),
    )

    await createInvestment({ name: 'a "quoted" name', amount: '1' })

    expect(JSON.parse(sentBody)).toEqual({ name: 'a "quoted" name', amount: 1 })
  })

  it('refuses amounts that are not plain decimals', async () => {
    await expect(createInvestment({ name: 'ETH', amount: '1e3' })).rejects.toThrow(TypeError)
    await expect(createInvestment({ name: 'ETH', amount: '1}' })).rejects.toThrow(TypeError)
  })
})

describe('updateInvestment', () => {
  it('PUTs to the investment URL', async () => {
    let received
    server.use(
      http.put('/api/investments/:id', async ({ params, request }) => {
        received = { id: params.id, body: await request.text() }
        return jsonText('{"id":"abc","name":"Silver","amount":12.5}')
      }),
    )

    const updated = await updateInvestment({ id: 'abc', name: 'Silver', amount: '12.5' })

    expect(received).toEqual({ id: 'abc', body: '{"name":"Silver","amount":12.5}' })
    expect(updated).toEqual({ id: 'abc', name: 'Silver', amount: '12.5' })
  })
})

describe('deleteInvestment', () => {
  it('resolves to null on 204', async () => {
    server.use(http.delete('/api/investments/:id', () => new HttpResponse(null, { status: 204 })))

    await expect(deleteInvestment('abc')).resolves.toBeNull()
  })
})

describe('error handling', () => {
  it('maps validation problems to field errors', async () => {
    server.use(
      http.post('/api/investments', () =>
        HttpResponse.json(
          {
            title: 'Validation failed',
            status: 400,
            detail: 'Request validation failed',
            errors: [{ field: 'amount', message: 'must be greater than 0' }],
          },
          { status: 400 },
        ),
      ),
    )

    const error = await createInvestment({ name: 'ETH', amount: '1' }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.message).toBe('Request validation failed')
    expect(error.fieldErrors).toEqual([{ field: 'amount', message: 'must be greater than 0' }])
  })

  it('uses the problem detail for 404s', async () => {
    server.use(
      http.delete('/api/investments/:id', () =>
        HttpResponse.json({ title: 'Investment not found', status: 404, detail: 'Investment with id x not found' }, { status: 404 }),
      ),
    )

    const error = await deleteInvestment('x').catch((e) => e)

    expect(error).toMatchObject({ status: 404, message: 'Investment with id x not found', fieldErrors: [] })
  })

  it('falls back to a generic message when the body is not a problem detail', async () => {
    server.use(http.get('/api/investments', () => new HttpResponse('Bad gateway', { status: 502 })))

    await expect(listInvestments()).rejects.toMatchObject({ status: 502, message: 'Request failed (status 502)' })
  })

  it('reports an unreachable server with status 0', async () => {
    server.use(http.get('/api/investments', () => HttpResponse.error()))

    await expect(listInvestments()).rejects.toMatchObject({
      status: 0,
      message: 'Could not reach the server. Is the backend running?',
    })
  })
})
