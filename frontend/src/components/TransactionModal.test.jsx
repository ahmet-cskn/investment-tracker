import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/transactionsApi.js'
import { toDateTimeLocalValue } from '../utils/dateTime.js'
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

// `timestamp` is the datetime-local input's own value format ("YYYY-MM-DDTHH:mm", local time), since
// jsdom does not implement the native date/time picker widget that userEvent.type would otherwise drive
async function fillAndSubmit(user, { name, change, timestamp } = {}) {
  if (name !== undefined) await user.selectOptions(screen.getByLabelText('Investment'), name)
  if (change !== undefined) await user.type(screen.getByLabelText('Change'), change)
  if (timestamp !== undefined) fireEvent.change(screen.getByLabelText('Timestamp'), { target: { value: timestamp } })
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

  it('submits the selected investment, change and an ISO timestamp, then closes', async () => {
    const { user, onSubmit, onClose } = renderModal()

    await fillAndSubmit(user, { name: 'Bitcoin', change: '-1.5', timestamp: '2026-01-15T10:00' })

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'Bitcoin',
      change: '-1.5',
      timestamp: new Date('2026-01-15T10:00').toISOString(),
    })
    expect(onClose).toHaveBeenCalled()
  })

  it('validates before calling onSubmit', async () => {
    const { user, onSubmit } = renderModal()

    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(screen.getByText('Choose an investment')).toBeInTheDocument()
    expect(screen.getByText('Change is required')).toBeInTheDocument()
    expect(screen.getByText('Timestamp is required')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects a change that is not a plain number, without calling onSubmit', async () => {
    const { user, onSubmit } = renderModal()

    await fillAndSubmit(user, { name: 'Gold', change: 'abc', timestamp: '2026-01-15T10:00' })

    expect(screen.getByText('Enter a number such as 5, -5 or 3.5')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('accepts a zero change', async () => {
    const { user, onSubmit } = renderModal()

    await fillAndSubmit(user, { name: 'Gold', change: '0', timestamp: '2026-01-15T10:00' })

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

    await fillAndSubmit(user, { name: 'Bitcoin', change: '1', timestamp: '2026-01-15T10:00' })

    expect(await screen.findByText('Unknown investment name: Bitcoin')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows a general error for a non-field failure', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('Could not reach the server. Is the backend running?'))
    const { user } = renderModal({ onSubmit })

    await fillAndSubmit(user, { name: 'Bitcoin', change: '1', timestamp: '2026-01-15T10:00' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
  })
})

describe('editing', () => {
  it('prefills the investment, change and timestamp', () => {
    const timestamp = '2026-01-15T10:00:00Z'
    renderModal({ editing: { id: 't1', name: 'Gold', change: '2.500000000000000000', timestamp } })

    expect(screen.getByRole('heading', { name: 'Edit transaction' })).toBeInTheDocument()
    expect(screen.getByLabelText('Investment')).toHaveValue('Gold')
    expect(screen.getByLabelText('Change')).toHaveValue('2.5')
    // Compared against the same conversion the component uses, so this holds regardless of the test
    // runner's own timezone (a hardcoded "2026-01-15T10:00" would only be correct in UTC).
    expect(screen.getByLabelText('Timestamp')).toHaveValue(toDateTimeLocalValue(timestamp))
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
