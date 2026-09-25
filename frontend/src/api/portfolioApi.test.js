import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server.js'
import { listPortfolio } from './portfolioApi.js'

const jsonText = (text, status = 200) =>
  new HttpResponse(text, { status, headers: { 'Content-Type': 'application/json' } })

describe('listPortfolio', () => {
  it('returns amount and worth as strings, including negative and exponent values, so no precision is lost', async () => {
    server.use(
      http.get('/api/portfolio', () =>
        jsonText(
          '[{"name":"Gold","investmentType":"Precious Metal","amount":6.000000000000000000,"worth":1},' +
            '{"name":"Silver","investmentType":"Precious Metal","amount":-4,"worth":1},' +
            '{"name":"Ethereum","investmentType":"Cryptocurrency","amount":3E-18,"worth":1}]',
        ),
      ),
    )

    await expect(listPortfolio()).resolves.toEqual([
      { name: 'Gold', investmentType: 'Precious Metal', amount: '6.000000000000000000', worth: '1' },
      { name: 'Silver', investmentType: 'Precious Metal', amount: '-4', worth: '1' },
      { name: 'Ethereum', investmentType: 'Cryptocurrency', amount: '3E-18', worth: '1' },
    ])
  })

  it('leaves a missing investment type as null', async () => {
    server.use(
      http.get('/api/portfolio', () =>
        jsonText('[{"name":"Unlisted","investmentType":null,"amount":2,"worth":1}]'),
      ),
    )

    await expect(listPortfolio()).resolves.toEqual([
      { name: 'Unlisted', investmentType: null, amount: '2', worth: '1' },
    ])
  })

  it('returns an empty list when there is nothing', async () => {
    server.use(http.get('/api/portfolio', () => jsonText('[]')))

    await expect(listPortfolio()).resolves.toEqual([])
  })

  it('rejects with an ApiError when the server is unreachable', async () => {
    server.use(http.get('/api/portfolio', () => HttpResponse.error()))

    await expect(listPortfolio()).rejects.toMatchObject({
      message: 'Could not reach the server. Is the backend running?',
    })
  })
})
