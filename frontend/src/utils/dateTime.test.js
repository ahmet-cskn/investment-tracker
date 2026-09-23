import { describe, expect, it } from 'vitest'
import { formatTimestamp, fromDateTimeLocalValue, toDateTimeLocalValue } from './dateTime.js'

describe('toDateTimeLocalValue / fromDateTimeLocalValue', () => {
  it('round-trips an ISO timestamp through the datetime-local value and back', () => {
    const original = '2026-03-15T14:30:00.000Z'

    const localValue = toDateTimeLocalValue(original)
    const roundTripped = fromDateTimeLocalValue(localValue)

    expect(roundTripped).toBe(original)
  })

  it('pads single-digit month, day, hour and minute', () => {
    // 2026-01-02T03:04:00Z, interpreted in the test runner's own timezone (same one toDateTimeLocalValue uses)
    const iso = new Date(2026, 0, 2, 3, 4, 0).toISOString()

    expect(toDateTimeLocalValue(iso)).toBe('2026-01-02T03:04')
  })
})

describe('formatTimestamp', () => {
  it('renders a readable, non-empty string for a valid timestamp', () => {
    const formatted = formatTimestamp('2026-01-15T10:00:00Z')

    expect(formatted).toEqual(expect.any(String))
    expect(formatted.length).toBeGreaterThan(0)
    expect(formatted).toContain('2026')
  })
})
