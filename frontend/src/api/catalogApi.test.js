import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server.js'
import { listCatalog } from './catalogApi.js'

describe('listCatalog', () => {
  it('returns the catalog entries as-is', async () => {
    server.use(
      http.get('/api/catalog', () =>
        HttpResponse.json([
          { name: 'Bitcoin', investmentType: 'Cryptocurrency' },
          { name: 'Gold', investmentType: 'Precious Metal' },
        ]),
      ),
    )

    await expect(listCatalog()).resolves.toEqual([
      { name: 'Bitcoin', investmentType: 'Cryptocurrency' },
      { name: 'Gold', investmentType: 'Precious Metal' },
    ])
  })

  it('rejects with an ApiError when the server is unreachable', async () => {
    server.use(http.get('/api/catalog', () => HttpResponse.error()))

    await expect(listCatalog()).rejects.toMatchObject({ message: 'Could not reach the server. Is the backend running?' })
  })
})
