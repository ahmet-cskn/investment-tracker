import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/transactionsApi.js'
import TransactionModal from './TransactionModal.jsx'

const CATALOG = [
  { name: 'Bitcoin', investmentType: 'Cryptocurrency' },
  { name: 'Gold', investmentType: 'Precious Metal' },
]

function renderModal(props = {}) {
  const onSubmit = props.onSubmit ?? vi.fn().mockResolvedValue(undefined)
  const onClose = props.onClose ?? vi.fn()
  const user = userEvent.setup()
  render(
    <TransactionModal
      open={props.open ?? true}
      editing={props.editing ?? null}
      catalog={props.catalog ?? CATALOG}
      onSubmit={onSubmit}
      onClose={onClose}
    />,
  )
  return { user, onSubmit, onClose }
}

// `date` is the date input's own value format ("YYYY-MM-DD"), set directly since jsdom does not implement
// the native date picker widget that userEvent.type would otherwise drive
async function fillAndSubmit(user, { name, change, date } = {}) {
  if (name !== undefined) await user.selectOptions(screen.getByLabelText('Investment'), name)
  if (change !== undefined) await user.type(screen.getByLabelText('Change'), change)
  if (date !== undefined) fireEvent.change(screen.getByLabelText('Date'), { target: { value: date } })
  await user.click(screen.getByRole('button', { name: 'OK' }))
}

describe('closed state', () => {
  it('has no accessible content when closed', () => {
    renderModal({ open: false })

    expect(screen.queryByRole('heading', { name: 'Add transaction' })).not.toBeInTheDocument()
  })
})

describe('adding', () => {
  it('lists the catalog in the investment dropdown', () => {
    renderModal()

    const options = screen.getAllByRole('option').map((option) => option.textContent)
    expect(options).toEqual(['Select an investment', 'Bitcoin', 'Gold'])
  })

  it('submits the selected investment, the change and the date as typed, then closes', async () => {
    const { user, onSubmit, onClose } = renderModal()

    await fillAndSubmit(user, { name: 'Bitcoin', change: '-1.5', date: '2026-01-15' })

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'Bitcoin',
      change: '-1.5',
      date: '2026-01-15',
    })
    expect(onClose).toHaveBeenCalled()
  })

  it('validates before calling onSubmit', async () => {
    const { user, onSubmit } = renderModal()

    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(screen.getByText('Choose an investment')).toBeInTheDocument()
    expect(screen.getByText('Change is required')).toBeInTheDocument()
    expect(screen.getByText('Date is required')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects a change that is not a plain number, without calling onSubmit', async () => {
    const { user, onSubmit } = renderModal()

    await fillAndSubmit(user, { name: 'Gold', change: 'abc', date: '2026-01-15' })

    expect(screen.getByText('Enter a number such as 5, -5 or 3.5')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('accepts a zero change', async () => {
    const { user, onSubmit } = renderModal()

    await fillAndSubmit(user, { name: 'Gold', change: '0', date: '2026-01-15' })

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ change: '0' }))
  })

  it('shows field errors from a rejected submit and keeps the dialog open', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError('Unknown investment name: Bitcoin', {
        status: 400,
        fieldErrors: [{ field: 'name', message: 'Unknown investment name: Bitcoin' }],
      }))
    const { user, onClose } = renderModal({ onSubmit })

    await fillAndSubmit(user, { name: 'Bitcoin', change: '1', date: '2026-01-15' })

    expect(await screen.findByText('Unknown investment name: Bitcoin')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows a general error for a non-field failure', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('Could not reach the server. Is the backend running?'))
    const { user } = renderModal({ onSubmit })

    await fillAndSubmit(user, { name: 'Bitcoin', change: '1', date: '2026-01-15' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
  })
})

describe('editing', () => {
  it('prefills the investment, change and date', () => {
    renderModal({ editing: { id: 't1', name: 'Gold', change: '2.500000000000000000', date: '2026-01-15' } })

    expect(screen.getByRole('heading', { name: 'Edit transaction' })).toBeInTheDocument()
    expect(screen.getByLabelText('Investment')).toHaveValue('Gold')
    expect(screen.getByLabelText('Change')).toHaveValue('2.5')
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
