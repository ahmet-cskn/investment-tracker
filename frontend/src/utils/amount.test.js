import { describe, expect, it } from 'vitest'
import { formatAmount, validateAmount, validateChange, validateName } from './amount.js'

describe('formatAmount', () => {
  it.each([
    ['5.000000000000000000', '5'],
    ['3.500000000000000000', '3.5'],
    ['3.5', '3.5'],
    ['50', '50'],
    ['0.5', '0.5'],
    ['0.000000000000000001', '0.000000000000000001'],
    ['  7  ', '7'],
    ['007', '7'],
  ])('formats %s as %s', (input, expected) => {
    expect(formatAmount(input)).toBe(expected)
  })

  it.each([
    ['1E-18', '0.000000000000000001'],
    ['1.5E-7', '0.00000015'],
    ['1E+2', '100'],
    ['1.25E+1', '12.5'],
  ])('expands exponent notation %s to %s', (input, expected) => {
    expect(formatAmount(input)).toBe(expected)
  })

  it('leaves non-numeric input unchanged', () => {
    expect(formatAmount('abc')).toBe('abc')
  })
})

describe('validateAmount', () => {
  it.each(['5', '3.5', '0.000000000000000001', '  12.25  ', '12345678901234567890'])('accepts %s', (input) => {
    expect(validateAmount(input)).toBeNull()
  })

  it('requires a value', () => {
    expect(validateAmount('  ')).toBe('Amount is required')
  })

  it.each(['abc', '1e3', '-5', '.5', '5.', '1,5'])('rejects non-plain-number %s', (input) => {
    expect(validateAmount(input)).toBe('Enter a number such as 5 or 3.5')
  })

  it.each(['0', '0.0', '000'])('rejects zero (%s)', (input) => {
    expect(validateAmount(input)).toBe('Amount must be greater than 0')
  })

  it('rejects more than 18 decimal places but ignores trailing zeros', () => {
    expect(validateAmount('0.0000000000000000001')).toBe('Use at most 18 decimal places')
    expect(validateAmount('1.5000000000000000000')).toBeNull()
  })

  it('rejects more than 20 integer digits', () => {
    expect(validateAmount('123456789012345678901')).toBe('Amount is too large')
  })
})

describe('validateChange', () => {
  it.each(['5', '-5', '3.5', '-3.5', '0', '0.0', '  12.25  '])('accepts %s', (input) => {
    expect(validateChange(input)).toBeNull()
  })

  it('requires a value', () => {
    expect(validateChange('  ')).toBe('Change is required')
  })

  it.each(['abc', '1e3', '.5', '5.', '1,5', '--5'])('rejects non-plain-number %s', (input) => {
    expect(validateChange(input)).toBe('Enter a number such as 5, -5 or 3.5')
  })

  it('rejects more than 18 decimal places but ignores trailing zeros, including when negative', () => {
    expect(validateChange('-0.0000000000000000001')).toBe('Use at most 18 decimal places')
    expect(validateChange('-1.5000000000000000000')).toBeNull()
  })

  it('rejects more than 20 integer digits, including when negative', () => {
    expect(validateChange('123456789012345678901')).toBe('Change is too large')
    expect(validateChange('-123456789012345678901')).toBe('Change is too large')
  })
})

describe('validateName', () => {
  it('requires a non-blank name', () => {
    expect(validateName('   ')).toBe('Name is required')
    expect(validateName('Gold')).toBeNull()
  })

  it('limits the length to 255 characters', () => {
    expect(validateName('a'.repeat(256))).toBe('Name must be at most 255 characters')
    expect(validateName('a'.repeat(255))).toBeNull()
  })
})
