import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server.js'
import {
  ApiError,
  createFinancialTransaction,
  deleteFinancialTransaction,
  listFinancialTransactions,
  updateFinancialTransaction,
} from './financialTransactionsApi.js'

const jsonText = (text, status = 200) =>
  new HttpResponse(text, { status, headers: { 'Content-Type': 'application/json' } })

describe('listFinancialTransactions', () => {
  it('returns change as a string, including a negative value and exponent notation, so no precision is lost', async () => {
    server.use(
      http.get('/api/financial-transactions', () =>
        jsonText(
          '[{"id":"a","name":"Groceries","change":-42.500000000000000000,"date":"2026-01-15"},' +
            '{"id":"b","name":"tiny","change":1E-18,"date":"2026-01-15"}]',
        ),
      ),
    )

    await expect(listFinancialTransactions()).resolves.toEqual([
      { id: 'a', name: 'Groceries', change: '-42.500000000000000000', date: '2026-01-15' },
      { id: 'b', name: 'tiny', change: '1E-18', date: '2026-01-15' },
    ])
  })
})

describe('createFinancialTransaction', () => {
  it('sends change as an exact JSON number literal, sign included', async () => {
    let sentBody
    server.use(
      http.post('/api/financial-transactions', async ({ request }) => {
        sentBody = await request.text()
        return jsonText('{"id":"a","name":"Salary","change":3000,"date":"2026-01-15"}', 201)
      }),
    )

    const created = await createFinancialTransaction({ name: 'Salary', change: '3000', date: '2026-01-15' })

    expect(sentBody).toBe('{"name":"Salary","change":3000,"date":"2026-01-15"}')
    expect(created).toMatchObject({ name: 'Salary', change: '3000' })
  })

  it('rejects a change that is not a plain signed decimal, as a rejected promise', async () => {
    await expect(createFinancialTransaction({ name: 'Salary', change: '1e3', date: '2026-01-15' })).rejects
      .toThrow(TypeError)
  })
})

describe('updateFinancialTransaction', () => {
  it('PUTs to the financial-transaction URL', async () => {
    let received
    server.use(
      http.put('/api/financial-transactions/:id', async ({ params, request }) => {
        received = { id: params.id, body: await request.text() }
        return jsonText('{"id":"abc","name":"Rent","change":-900,"date":"2026-02-01"}')
      }),
    )

    const updated = await updateFinancialTransaction({ id: 'abc', name: 'Rent', change: '-900', date: '2026-02-01' })

    expect(received.id).toBe('abc')
    expect(updated).toMatchObject({ id: 'abc', name: 'Rent' })
  })
})

describe('deleteFinancialTransaction', () => {
  it('resolves to null on 204', async () => {
    server.use(http.delete('/api/financial-transactions/:id', () => new HttpResponse(null, { status: 204 })))

    await expect(deleteFinancialTransaction('abc')).resolves.toBeNull()
  })
})

describe('error handling', () => {
  it('maps a validation problem to a field error, same shape as transactions', async () => {
    server.use(
      http.post('/api/financial-transactions', () =>
        HttpResponse.json(
          {
            title: 'Validation failed',
            status: 400,
            detail: 'Invalid request content.',
            errors: [{ field: 'name', message: 'must not be blank' }],
          },
          { status: 400 },
        ),
      ),
    )

    const error = await createFinancialTransaction({ name: '', change: '1', date: '2026-01-15' }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.fieldErrors).toEqual([{ field: 'name', message: 'must not be blank' }])
  })
})
