import { http, HttpResponse } from 'msw'

// Mirrors the investment_catalog seed data (services/investment-service .../004-create-investment-catalog-table.yaml)
export const CATALOG = [
  { name: 'Bitcoin', investmentType: 'Cryptocurrency' },
  { name: 'Ethereum', investmentType: 'Cryptocurrency' },
  { name: 'Gold', investmentType: 'Precious Metal' },
  { name: 'S&P500', investmentType: 'Stock' },
  { name: 'Silver', investmentType: 'Precious Metal' },
]

export const catalogTypeByName = Object.fromEntries(CATALOG.map((entry) => [entry.name, entry.investmentType]))

/** The default catalog handler; tests that want a failure or a different list use `server.use(...)` to override it. */
export const fakeCatalogHandler = http.get('/api/catalog', () => HttpResponse.json(CATALOG))
