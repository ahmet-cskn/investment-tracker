import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/investmentsApi.js'
import InitialInvestmentFormModal from './InitialInvestmentFormModal.jsx'

const CATALOG = [
  { name: 'Bitcoin', investmentType: 'Cryptocurrency' },
  { name: 'Gold', investmentType: 'Precious Metal' },
]

function renderModal(props = {}) {
  const onSubmit = props.onSubmit ?? vi.fn().mockResolvedValue(undefined)
  const onClose = props.onClose ?? vi.fn()
  const user = userEvent.setup()
  render(
    <InitialInvestmentFormModal
      open={props.open ?? true}
      editing={props.editing ?? null}
      catalog={props.catalog ?? CATALOG}
      onSubmit={onSubmit}
      onClose={onClose}
    />,
  )
  return { user, onSubmit, onClose }
}

async function fillAndSubmit(user, { name, amount } = {}) {
  if (name !== undefined) await user.selectOptions(screen.getByLabelText('Investment'), name)
  if (amount !== undefined) await user.type(screen.getByLabelText('Amount'), amount)
  await user.click(screen.getByRole('button', { name: 'OK' }))
}

describe('closed state', () => {
  it('has no content at all when closed', () => {
    renderModal({ open: false })

    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Investment')).not.toBeInTheDocument()
  })
})

describe('adding', () => {
  it('lists the catalog in the investment dropdown', () => {
    renderModal()

    expect(screen.getByRole('heading', { name: 'Add initial investment' })).toBeInTheDocument()
    const options = screen.getAllByRole('option').map((option) => option.textContent)
    expect(options).toEqual(['Select an investment', 'Bitcoin', 'Gold'])
  })

  it('submits the selected investment and a normalised amount, then closes', async () => {
    const { user, onSubmit, onClose } = renderModal()

    await fillAndSubmit(user, { name: 'Gold', amount: '2.50' })

    expect(onSubmit).toHaveBeenCalledWith({ name: 'Gold', amount: '2.5' })
    expect(onClose).toHaveBeenCalled()
  })

  it('validates before calling onSubmit', async () => {
    const { user, onSubmit } = renderModal()

    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(screen.getByText('Choose an investment')).toBeInTheDocument()
    expect(screen.getByText('Amount is required')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it.each([
    ['abc', 'Enter a number such as 5 or 3.5'],
    ['-1', 'Enter a number such as 5 or 3.5'],
    ['0', 'Amount must be greater than 0'],
  ])('rejects the amount %s without calling onSubmit', async (amount, message) => {
    const { user, onSubmit } = renderModal()

    await fillAndSubmit(user, { name: 'Gold', amount })

    expect(screen.getByText(message)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows field errors from a rejected submit and keeps the dialog open', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError('Unknown investment name: Gold', {
        status: 400,
        fieldErrors: [{ field: 'name', message: 'Unknown investment name: Gold' }],
      }),
    )
    const { user, onClose } = renderModal({ onSubmit })

    await fillAndSubmit(user, { name: 'Gold', amount: '2' })

    expect(await screen.findByText('Unknown investment name: Gold')).toBeInTheDocument()
    expect(screen.getByLabelText('Investment')).toHaveAttribute('aria-invalid', 'true')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows a general error for a non-field failure', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('Could not reach the server. Is the backend running?'))
    const { user } = renderModal({ onSubmit })

    await fillAndSubmit(user, { name: 'Gold', amount: '2' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
  })
})

describe('editing', () => {
  it('prefills the investment and the amount without trailing zeros', () => {
    renderModal({ editing: { id: 'i1', name: 'Gold', amount: '2.500000000000000000' } })

    expect(screen.getByRole('heading', { name: 'Edit initial investment' })).toBeInTheDocument()
    expect(screen.getByLabelText('Investment')).toHaveValue('Gold')
    expect(screen.getByLabelText('Amount')).toHaveValue('2.5')
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
