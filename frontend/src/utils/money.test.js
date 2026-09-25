import { describe, expect, it } from 'vitest'
import { formatUsd, sumWorth } from './money.js'

describe('formatUsd', () => {
  it.each([
    ['1234.5678', '$1,234.57'],
    ['250', '$250.00'],
    ['250.000000000000000000', '$250.00'],
    ['0', '$0.00'],
    ['-273.406', '-$273.41'],
    ['-75000', '-$75,000.00'],
  ])('formats %s as %s', (input, expected) => {
    expect(formatUsd(input, 'en-US')).toBe(expected)
  })

  it('rounds half a cent up exactly, which a floating point number cannot promise', () => {
    // 0.005 and 1.005 are not exactly representable as doubles, so Number-based rounding gets them wrong
    expect(formatUsd('0.005', 'en-US')).toBe('$0.01')
    expect(formatUsd('1.005', 'en-US')).toBe('$1.01')
    expect(formatUsd('-0.005', 'en-US')).toBe('-$0.01')
  })

  it('keeps every digit of a huge amount instead of losing them to a double', () => {
    expect(formatUsd('12345678901234567890.12', 'en-US')).toBe('$12,345,678,901,234,567,890.12')
  })

  it('reads the exponent notation the backend may use for tiny values', () => {
    expect(formatUsd('1E-18', 'en-US')).toBe('$0.00')
    expect(formatUsd('1.5E+3', 'en-US')).toBe('$1,500.00')
  })

  it('shows a negative that rounds to zero as $0.00 without a minus sign', () => {
    expect(formatUsd('-0.000000000000000001', 'en-US')).toBe('$0.00')
    expect(formatUsd('-0.004', 'en-US')).toBe('$0.00')
  })

  it('follows the given locale', () => {
    expect(formatUsd('1234.5', 'de-DE')).toBe('1.234,50 $')
  })
})

describe('sumWorth', () => {
  const sum = (...worths) => sumWorth(worths.map((worth) => ({ worth })))

  it('adds worths, negative ones included', () => {
    expect(sum('100.5', '-40.25', '1000').total).toBe('1060.250000000000000000')
  })

  it('is exact to the 18th decimal, where floating point numbers are not', () => {
    expect(sum('0.100000000000000000', '0.200000000000000000').total).toBe('0.300000000000000000')
    expect(sum('0.000000000000000001', '0.000000000000000002').total).toBe('0.000000000000000003')
  })

  it('reads exponent notation', () => {
    expect(sum('1E-16', '2').total).toBe('2.000000000000000100')
  })

  it('keeps the sign of a negative total, even one below a dollar', () => {
    expect(sum('-5', '2').total).toBe('-3.000000000000000000')
    expect(sum('-0.5', '0.25').total).toBe('-0.250000000000000000')
  })

  it('is zero for nothing', () => {
    expect(sumWorth([])).toEqual({ total: '0.000000000000000000', unpriced: 0 })
    expect(sum('5', '-5').total).toBe('0.000000000000000000')
  })

  it('leaves out entries without a worth and counts them', () => {
    expect(sum('10', null, '5', null)).toEqual({ total: '15.000000000000000000', unpriced: 2 })
  })

  it('handles values beyond the range of a double', () => {
    expect(sum('99999999999999999999.999999999999999999', '0.000000000000000001').total).toBe(
      '100000000000000000000.000000000000000000',
    )
  })
})
