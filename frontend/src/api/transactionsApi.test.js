import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server.js'
import {
  ApiError,
  createTransaction,
  deleteTransaction,
  listTransactions,
  updateTransaction,
} from './transactionsApi.js'

const jsonText = (text, status = 200) =>
  new HttpResponse(text, { status, headers: { 'Content-Type': 'application/json' } })

describe('listTransactions', () => {
  it('returns change as a string, including a negative value, so no precision is lost', async () => {
    server.use(
      http.get('/api/transactions', () =>
        jsonText(
          '[{"id":"a","name":"Bitcoin","investmentType":"Cryptocurrency","change":-1.500000000000000000,"date":"2026-01-15"}]',
        ),
      ),
    )

    await expect(listTransactions()).resolves.toEqual([
      {
        id: 'a',
        name: 'Bitcoin',
        investmentType: 'Cryptocurrency',
        change: '-1.500000000000000000',
        date: '2026-01-15',
      },
    ])
  })
})

describe('createTransaction', () => {
  it('sends change as an exact JSON number literal, sign included', async () => {
    let sentBody
    server.use(
      http.post('/api/transactions', async ({ request }) => {
        sentBody = await request.text()
        return jsonText(
          '{"id":"a","name":"Gold","investmentType":"Precious Metal","change":-2.5,"date":"2026-01-15"}',
          201,
        )
      }),
    )

    const created = await createTransaction({ name: 'Gold', change: '-2.5', date: '2026-01-15' })

    expect(sentBody).toBe('{"name":"Gold","change":-2.5,"date":"2026-01-15"}')
    expect(created).toMatchObject({ name: 'Gold', change: '-2.5' })
  })

  it('rejects a change that is not a plain signed decimal, as a rejected promise', async () => {
    await expect(createTransaction({ name: 'Gold', change: '1e3', date: '2026-01-15' })).rejects
      .toThrow(TypeError)
  })
})

describe('updateTransaction', () => {
  it('PUTs to the transaction URL', async () => {
    let received
    server.use(
      http.put('/api/transactions/:id', async ({ params, request }) => {
        received = { id: params.id, body: await request.text() }
        return jsonText('{"id":"abc","name":"Bitcoin","investmentType":"Cryptocurrency","change":1,"date":"2026-02-01"}')
      }),
    )

    const updated = await updateTransaction({ id: 'abc', name: 'Bitcoin', change: '1', date: '2026-02-01' })

    expect(received.id).toBe('abc')
    expect(updated).toMatchObject({ id: 'abc', name: 'Bitcoin' })
  })
})

describe('deleteTransaction', () => {
  it('resolves to null on 204', async () => {
    server.use(http.delete('/api/transactions/:id', () => new HttpResponse(null, { status: 204 })))

    await expect(deleteTransaction('abc')).resolves.toBeNull()
  })
})

describe('error handling', () => {
  it('maps an unknown-investment-name problem to a field error, same shape as investments', async () => {
    server.use(
      http.post('/api/transactions', () =>
        HttpResponse.json(
          {
            title: 'Invalid investment name',
            status: 400,
            detail: 'Unknown investment name: Dogecoin',
            errors: [{ field: 'name', message: 'Unknown investment name: Dogecoin' }],
          },
          { status: 400 },
        ),
      ),
    )

    const error = await createTransaction({ name: 'Dogecoin', change: '1', date: '2026-01-15' }).catch(
      (e) => e,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect(error.fieldErrors).toEqual([{ field: 'name', message: 'Unknown investment name: Dogecoin' }])
  })
})
