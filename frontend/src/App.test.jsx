import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App.jsx'
import { createFakeBackend } from './test/fakeBackend.js'
import { fakeCatalogHandler } from './test/fakeCatalog.js'
import { createFakeTransactionsBackend } from './test/fakeTransactionsBackend.js'
import { server } from './test/server.js'

const GOLD = { id: 'id-a', name: 'Gold (g)', amount: '5' }
const ETH = { id: 'id-b', name: 'Ethereum', amount: '3.5' }

// App always renders the transactions section and the catalog-backed "Add Transaction" button too, so
// every render needs both mocked; initialTransactions/overrides let a test customise just what it needs.
function renderApp(initial = [], overrides = [], initialTransactions = []) {
  const backend = createFakeBackend(initial)
  const transactionsBackend = createFakeTransactionsBackend(initialTransactions)
  server.use(...overrides, ...backend.handlers, ...transactionsBackend.handlers, fakeCatalogHandler)
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
  return { user, backend, transactionsBackend }
}

async function fillForm(user, { name, amount }) {
  const nameInput = screen.getByLabelText('Name')
  const amountInput = screen.getByLabelText('Amount')
  await user.clear(nameInput)
  await user.clear(amountInput)
  if (name) await user.type(nameInput, name)
  if (amount) await user.type(amountInput, amount)
}

// The transaction modal's investment dropdown always renders a <option> per catalog entry, even while
// the dialog is closed, so a bare getByText('Ethereum') etc. can also match an option; scope to the
// investments table to avoid that.
function investmentsSection() {
  return screen.getByRole('heading', { name: 'Your investments' }).closest('section')
}

// Same reasoning as investmentsSection(): scope away from the modal's always-present dropdown options
function transactionsSection() {
  return screen.getByRole('heading', { name: 'Your transactions' }).closest('section')
}

describe('listing', () => {
  it('shows an empty state when there are no investments', async () => {
    renderApp()

    expect(await screen.findByText(/No investments yet/)).toBeInTheDocument()
  })

  it('lists investments sorted by name with padding trimmed from amounts', async () => {
    renderApp([GOLD, ETH])

    const rows = await screen.findAllByRole('row')

    // header row + one row per investment
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('Ethereum')).toBeInTheDocument()
    expect(within(rows[1]).getByText('3.5')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Gold (g)')).toBeInTheDocument()
    expect(within(rows[2]).getByText('5')).toBeInTheDocument()
  })

  it('shows the type and worth derived from the catalog, and a placeholder for a name outside it', async () => {
    // "Bitcoin" is a catalog name (like ETH's "Ethereum"); "Gold (g)" is not, so it has no derived type/worth
    const BITCOIN = { id: 'id-c', name: 'Bitcoin', amount: '0.5' }
    renderApp([GOLD, BITCOIN])

    const rows = await screen.findAllByRole('row')

    expect(within(rows[1]).getByText('Bitcoin')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Cryptocurrency')).toBeInTheDocument()
    expect(within(rows[1]).getByText('1')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Gold (g)')).toBeInTheDocument()
    expect(within(rows[2]).getAllByText('—')).toHaveLength(2)
  })
})

describe('load failure', () => {
  it('shows the error and lets the user retry', async () => {
    const failOnce = http.get('/api/investments', () => HttpResponse.error(), { once: true })
    const { user } = renderApp([GOLD], [failOnce])

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByText('Gold (g)')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('adding', () => {
  it('adds an investment and clears the form', async () => {
    const { user, backend } = renderApp()
    await screen.findByText(/No investments yet/)

    await fillForm(user, { name: '  Ethereum ', amount: '3.5' })
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await within(investmentsSection()).findByText('Ethereum')).toBeInTheDocument()
    expect(within(investmentsSection()).getByText('3.5')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('')
    expect(screen.getByLabelText('Amount')).toHaveValue('')
    expect([...backend.rows.values()][0]).toMatchObject({ name: 'Ethereum', amount: 3.5 })
  })

  it('validates on the client without calling the backend', async () => {
    const { user, backend } = renderApp()
    await screen.findByText(/No investments yet/)

    await fillForm(user, { name: '', amount: 'abc' })
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(screen.getByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('Enter a number such as 5 or 3.5')).toBeInTheDocument()
    expect(backend.rows.size).toBe(0)
  })

  it('shows field errors returned by the backend', async () => {
    const { user } = renderApp()
    await screen.findByText(/No investments yet/)
    server.use(
      http.post('/api/investments', () =>
        HttpResponse.json(
          {
            title: 'Validation failed',
            status: 400,
            detail: 'Request validation failed',
            errors: [{ field: 'name', message: 'size must be between 0 and 255' }],
          },
          { status: 400 },
        ),
      ),
    )

    await fillForm(user, { name: 'Gold', amount: '5' })
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('size must be between 0 and 255')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')
    // the user's input is kept so they can correct it
    expect(screen.getByLabelText('Name')).toHaveValue('Gold')
  })

  it('shows a general error when the server fails', async () => {
    const { user } = renderApp()
    await screen.findByText(/No investments yet/)
    server.use(
      http.post('/api/investments', () =>
        HttpResponse.json({ title: 'Internal server error', status: 500, detail: 'An unexpected error occurred' }, { status: 500 }),
      ),
    )

    await fillForm(user, { name: 'Gold', amount: '5' })
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('An unexpected error occurred')
  })
})

describe('editing', () => {
  it('prefills the form, saves the change and returns to add mode', async () => {
    const { user, backend } = renderApp([GOLD, ETH])
    await user.click(await screen.findByRole('button', { name: 'Edit Gold (g)' }))

    expect(screen.getByRole('heading', { name: 'Edit investment' })).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('Gold (g)')
    expect(screen.getByLabelText('Amount')).toHaveValue('5')

    await fillForm(user, { name: 'Silver (g)', amount: '12.5' })
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Silver (g)')).toBeInTheDocument()
    expect(screen.queryByText('Gold (g)')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Add investment' })).toBeInTheDocument()
    expect(backend.rows.get(GOLD.id)).toMatchObject({ name: 'Silver (g)', amount: 12.5 })
  })

  it('discards changes on cancel', async () => {
    const { user, backend } = renderApp([GOLD])
    await user.click(await screen.findByRole('button', { name: 'Edit Gold (g)' }))
    await fillForm(user, { name: 'Changed', amount: '1' })

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.getByRole('heading', { name: 'Add investment' })).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('')
    expect(screen.getByText('Gold (g)')).toBeInTheDocument()
    expect(backend.rows.get(GOLD.id).name).toBe('Gold (g)')
  })

  it('shows the backend error if the investment was deleted elsewhere in the meantime', async () => {
    const { user, backend } = renderApp([GOLD])
    await user.click(await screen.findByRole('button', { name: 'Edit Gold (g)' }))
    backend.rows.delete(GOLD.id)

    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(`Investment with id ${GOLD.id} not found`)
    await waitFor(() => expect(screen.getByText(/No investments yet/)).toBeInTheDocument())
  })
})

describe('deleting', () => {
  it('asks for confirmation before deleting', async () => {
    const { user, backend } = renderApp([GOLD, ETH])
    await user.click(await screen.findByRole('button', { name: 'Delete Gold (g)' }))

    await user.click(screen.getByRole('button', { name: 'Cancel delete Gold (g)' }))
    expect(backend.rows.size).toBe(2)

    await user.click(screen.getByRole('button', { name: 'Delete Gold (g)' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete Gold (g)' }))

    await waitFor(() => expect(screen.queryByText('Gold (g)')).not.toBeInTheDocument())
    expect(within(investmentsSection()).getByText('Ethereum')).toBeInTheDocument()
    expect(backend.rows.has(GOLD.id)).toBe(false)
  })

  it('shows an error banner when deleting fails', async () => {
    const { user } = renderApp([GOLD])
    server.use(
      http.delete('/api/investments/:id', () =>
        HttpResponse.json({ title: 'Internal server error', status: 500, detail: 'An unexpected error occurred' }, { status: 500 }),
      ),
    )

    await user.click(await screen.findByRole('button', { name: 'Delete Gold (g)' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete Gold (g)' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('An unexpected error occurred')
    expect(screen.getByText('Gold (g)')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('leaves edit mode if the edited investment is deleted', async () => {
    const { user } = renderApp([GOLD, ETH])
    await user.click(await screen.findByRole('button', { name: 'Edit Gold (g)' }))

    await user.click(screen.getByRole('button', { name: 'Delete Gold (g)' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete Gold (g)' }))

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Add investment' })).toBeInTheDocument())
  })
})

const BTC_TX = { id: 'tx-a', name: 'Bitcoin', change: '-1.5', timestamp: '2026-01-15T10:00:00Z' }
const GOLD_TX = { id: 'tx-b', name: 'Gold', change: '2.5', timestamp: '2026-02-15T10:00:00Z' }

async function fillTransactionModal(user, { name, change, timestamp }) {
  if (name !== undefined) await user.selectOptions(screen.getByLabelText('Investment'), name)
  if (change !== undefined) await user.type(screen.getByLabelText('Change'), change)
  if (timestamp !== undefined) {
    fireEvent.change(screen.getByLabelText('Timestamp'), { target: { value: timestamp } })
  }
}

describe('transaction listing', () => {
  it('shows an empty state when there are no transactions', async () => {
    renderApp()

    expect(await screen.findByText(/No transactions yet/)).toBeInTheDocument()
  })

  it('lists transactions newest first, with type derived from the catalog', async () => {
    // the fake backend, like the real one, sorts by timestamp; GOLD_TX is later than BTC_TX
    renderApp([], [], [BTC_TX, GOLD_TX])

    const rows = await within(transactionsSection()).findAllByRole('row')

    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('Gold')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Precious Metal')).toBeInTheDocument()
    expect(within(rows[1]).getByText('2.5')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Bitcoin')).toBeInTheDocument()
    expect(within(rows[2]).getByText('-1.5')).toBeInTheDocument()
  })
})

describe('adding a transaction', () => {
  it('opens the modal, submits, and shows the new row', async () => {
    const { user, transactionsBackend } = renderApp()
    await screen.findByText(/No transactions yet/)

    await user.click(await screen.findByRole('button', { name: 'Add Transaction' }))
    expect(screen.getByRole('heading', { name: 'Add transaction' })).toBeInTheDocument()

    await fillTransactionModal(user, { name: 'Bitcoin', change: '-1.5', timestamp: '2026-01-15T10:00' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(transactionsSection()).findByText('Bitcoin')).toBeInTheDocument()
    expect(within(transactionsSection()).getByText('Cryptocurrency')).toBeInTheDocument()
    expect(within(transactionsSection()).getByText('-1.5')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Add transaction' })).not.toBeInTheDocument()
    expect([...transactionsBackend.rows.values()][0]).toMatchObject({ name: 'Bitcoin', change: -1.5 })
  })

  it('shows the backend error for an unknown investment name and keeps the modal open', async () => {
    const { user } = renderApp()
    await screen.findByText(/No transactions yet/)
    server.use(
      http.post('/api/transactions', () =>
        HttpResponse.json(
          {
            title: 'Invalid investment name',
            status: 400,
            detail: 'Unknown investment name: Bitcoin',
            errors: [{ field: 'name', message: 'Unknown investment name: Bitcoin' }],
          },
          { status: 400 },
        ),
      ),
    )

    await user.click(await screen.findByRole('button', { name: 'Add Transaction' }))
    await fillTransactionModal(user, { name: 'Bitcoin', change: '1', timestamp: '2026-01-15T10:00' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await screen.findByText('Unknown investment name: Bitcoin')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Add transaction' })).toBeInTheDocument()
  })

  it('closes the modal without submitting on Cancel', async () => {
    const { user, transactionsBackend } = renderApp()
    await user.click(await screen.findByRole('button', { name: 'Add Transaction' }))

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('heading', { name: 'Add transaction' })).not.toBeInTheDocument()
    expect(transactionsBackend.rows.size).toBe(0)
  })
})

describe('editing a transaction', () => {
  it('prefills the modal and saves the change', async () => {
    const { user, transactionsBackend } = renderApp([], [], [BTC_TX])
    await user.click(await screen.findByRole('button', { name: 'Edit transaction for Bitcoin' }))

    expect(screen.getByRole('heading', { name: 'Edit transaction' })).toBeInTheDocument()
    expect(screen.getByLabelText('Investment')).toHaveValue('Bitcoin')
    expect(screen.getByLabelText('Change')).toHaveValue('-1.5')

    // Change the investment and the amount; leave the prefilled timestamp as-is
    await user.selectOptions(screen.getByLabelText('Investment'), 'Gold')
    await user.clear(screen.getByLabelText('Change'))
    await user.type(screen.getByLabelText('Change'), '3')
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(transactionsSection()).findByText('Precious Metal')).toBeInTheDocument()
    expect(within(transactionsSection()).queryByText('Cryptocurrency')).not.toBeInTheDocument()
    expect(transactionsBackend.rows.get(BTC_TX.id)).toMatchObject({ name: 'Gold', change: 3 })
  })
})

describe('deleting a transaction', () => {
  it('asks for confirmation before deleting', async () => {
    const { user, transactionsBackend } = renderApp([], [], [BTC_TX, GOLD_TX])
    await user.click(await screen.findByRole('button', { name: 'Delete transaction for Bitcoin' }))

    await user.click(screen.getByRole('button', { name: 'Cancel delete transaction for Bitcoin' }))
    expect(transactionsBackend.rows.size).toBe(2)

    await user.click(screen.getByRole('button', { name: 'Delete transaction for Bitcoin' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete transaction for Bitcoin' }))

    await waitFor(() => expect(within(transactionsSection()).queryByText('Bitcoin')).not.toBeInTheDocument())
    expect(within(transactionsSection()).getByText('Gold')).toBeInTheDocument()
    expect(transactionsBackend.rows.has(BTC_TX.id)).toBe(false)
  })

  it('shows an error banner when deleting fails', async () => {
    const { user } = renderApp([], [], [BTC_TX])
    server.use(
      http.delete('/api/transactions/:id', () =>
        HttpResponse.json({ title: 'Internal server error', status: 500, detail: 'An unexpected error occurred' }, { status: 500 }),
      ),
    )

    await user.click(await screen.findByRole('button', { name: 'Delete transaction for Bitcoin' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete transaction for Bitcoin' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('An unexpected error occurred')
    expect(within(transactionsSection()).getByText('Bitcoin')).toBeInTheDocument()
  })
})

describe('catalog availability', () => {
  it('disables adding a transaction and offers a retry when the catalog fails to load', async () => {
    const failOnce = http.get('/api/catalog', () => HttpResponse.error(), { once: true })
    const { user } = renderApp([], [failOnce])

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
    expect(screen.queryByRole('button', { name: 'Add Transaction' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByRole('button', { name: 'Add Transaction' })).toBeInTheDocument()
  })
})
