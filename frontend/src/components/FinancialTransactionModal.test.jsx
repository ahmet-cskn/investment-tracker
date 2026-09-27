import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/financialTransactionsApi.js'
import FinancialTransactionModal from './FinancialTransactionModal.jsx'

function renderModal(props = {}) {
  const onSubmit = props.onSubmit ?? vi.fn().mockResolvedValue(undefined)
  const onClose = props.onClose ?? vi.fn()
  const user = userEvent.setup()
  render(
    <FinancialTransactionModal
      open={props.open ?? true}
      editing={props.editing ?? null}
      onSubmit={onSubmit}
      onClose={onClose}
    />,
  )
  return { user, onSubmit, onClose }
}

// `date` is the date input's own value format ("YYYY-MM-DD"), set directly since jsdom does not implement
// the native date picker widget that userEvent.type would otherwise drive
async function fillAndSubmit(user, { name, change, date } = {}) {
  if (name !== undefined) await user.type(screen.getByLabelText('Name'), name)
  if (change !== undefined) await user.type(screen.getByLabelText('Change'), change)
  if (date !== undefined) fireEvent.change(screen.getByLabelText('Date'), { target: { value: date } })
  await user.click(screen.getByRole('button', { name: 'OK' }))
}

describe('closed state', () => {
  it('has no accessible content when closed', () => {
    renderModal({ open: false })

    expect(screen.queryByRole('heading', { name: 'Add financial transaction' })).not.toBeInTheDocument()
  })
})

describe('adding', () => {
  it('submits the name, the change and the date as typed, then closes', async () => {
    const { user, onSubmit, onClose } = renderModal()

    await fillAndSubmit(user, { name: 'Groceries', change: '-42.5', date: '2026-01-15' })

    expect(onSubmit).toHaveBeenCalledWith({ name: 'Groceries', change: '-42.5', date: '2026-01-15' })
    expect(onClose).toHaveBeenCalled()
  })

  it('validates before calling onSubmit', async () => {
    const { user, onSubmit } = renderModal()

    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(screen.getByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('Change is required')).toBeInTheDocument()
    expect(screen.getByText('Date is required')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects a change that is not a plain number, without calling onSubmit', async () => {
    const { user, onSubmit } = renderModal()

    await fillAndSubmit(user, { name: 'Groceries', change: 'abc', date: '2026-01-15' })

    expect(screen.getByText('Enter a number such as 5, -5 or 3.5')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('accepts a zero change', async () => {
    const { user, onSubmit } = renderModal()

    await fillAndSubmit(user, { name: 'Groceries', change: '0', date: '2026-01-15' })

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ change: '0' }))
  })

  it('trims the name before submitting', async () => {
    const { user, onSubmit } = renderModal()

    await fillAndSubmit(user, { name: '  Groceries  ', change: '1', date: '2026-01-15' })

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ name: 'Groceries' }))
  })

  it('shows field errors from a rejected submit and keeps the dialog open', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError('must not be blank', { status: 400, fieldErrors: [{ field: 'name', message: 'must not be blank' }] }))
    const { user, onClose } = renderModal({ onSubmit })

    await fillAndSubmit(user, { name: 'Groceries', change: '1', date: '2026-01-15' })

    expect(await screen.findByText('must not be blank')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows a general error for a non-field failure', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('Could not reach the server. Is the backend running?'))
    const { user } = renderModal({ onSubmit })

    await fillAndSubmit(user, { name: 'Groceries', change: '1', date: '2026-01-15' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
  })
})

describe('editing', () => {
  it('prefills the name, change and date', () => {
    renderModal({ editing: { id: 'f1', name: 'Rent', change: '-900.000000000000000000', date: '2026-01-15' } })

    expect(screen.getByRole('heading', { name: 'Edit financial transaction' })).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('Rent')
    expect(screen.getByLabelText('Change')).toHaveValue('-900')
    expect(screen.getByLabelText('Date')).toHaveValue('2026-01-15')
  })
})

describe('cancelling', () => {
  it('calls onClose without calling onSubmit', async () => {
    const { user, onSubmit, onClose } = renderModal()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onClose).toHaveBeenCalled()
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
