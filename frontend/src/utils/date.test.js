import { afterEach, describe, expect, it } from 'vitest'
import { formatDate } from './date.js'

describe('formatDate', () => {
  const originalTimezone = process.env.TZ

  afterEach(() => {
    if (originalTimezone === undefined) delete process.env.TZ
    else process.env.TZ = originalTimezone
  })

  it('renders a readable date containing the year', () => {
    const formatted = formatDate('2026-03-15')

    expect(formatted).toEqual(expect.any(String))
    expect(formatted).toContain('2026')
    expect(formatted).toContain('15')
  })

  // The bug this guards against: new Date('2026-01-01') is UTC midnight, which is still Dec 31, 2025 in
  // the Americas, so a date-only string would render as the day before
  it.each(['America/Los_Angeles', 'Pacific/Auckland', 'UTC'])('shows the same day in the %s timezone', (timezone) => {
    process.env.TZ = timezone

    const formatted = formatDate('2026-01-01')

    expect(formatted).toContain('2026')
    expect(formatted).not.toContain('2025')
  })
})
