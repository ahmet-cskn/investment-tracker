import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App.jsx'
import { createFakeBackend } from './test/fakeBackend.js'
import { fakeCatalogHandler } from './test/fakeCatalog.js'
import { fakePortfolioHandler } from './test/fakePortfolio.js'
import { createFakeTransactionsBackend } from './test/fakeTransactionsBackend.js'
import { server } from './test/server.js'

// Initial investments (the rows behind /api/investments)
const INITIAL_GOLD = { id: 'id-a', name: 'Gold', amount: '2' }
const INITIAL_ETH = { id: 'id-b', name: 'Ethereum', amount: '3.5' }

// Transactions
const BTC_TX = { id: 'tx-a', name: 'Bitcoin', change: '-1.5', timestamp: '2026-01-15T10:00:00Z' }
const GOLD_TX = { id: 'tx-b', name: 'Gold', change: '2.5', timestamp: '2026-02-15T10:00:00Z' }
const GOLD_MINUS_ONE = { id: 'tx-c', name: 'Gold', change: '-1', timestamp: '2026-03-01T10:00:00Z' }
const GOLD_PLUS_FIVE = { id: 'tx-d', name: 'Gold', change: '5', timestamp: '2026-03-02T10:00:00Z' }

// App always renders the portfolio, the transactions and the catalog-backed "Add Transaction" button, so
// every render needs all of them mocked. initial/initialTransactions seed the fake backends and the fake
// portfolio is computed from both, like the real one; overrides let a test customise just what it needs.
function renderApp(initial = [], overrides = [], initialTransactions = []) {
  const backend = createFakeBackend(initial)
  const transactionsBackend = createFakeTransactionsBackend(initialTransactions)
  server.use(
    ...overrides,
    ...backend.handlers,
    ...transactionsBackend.handlers,
    fakePortfolioHandler(backend, transactionsBackend),
    fakeCatalogHandler,
  )
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

async function fillTransactionModal(user, { name, change, timestamp }) {
  if (name !== undefined) await user.selectOptions(screen.getByLabelText('Investment'), name)
  if (change !== undefined) await user.type(screen.getByLabelText('Change'), change)
  if (timestamp !== undefined) {
    fireEvent.change(screen.getByLabelText('Timestamp'), { target: { value: timestamp } })
  }
}

// The transaction modal's investment dropdown always renders a <option> per catalog entry, even while
// the dialog is closed, so a bare getByText('Ethereum') etc. can also match an option; scope to a
// section to avoid that.
function investmentsSection() {
  return screen.getByRole('heading', { name: 'Your investments' }).closest('section')
}

function transactionsSection() {
  return screen.getByRole('heading', { name: 'Your transactions' }).closest('section')
}

describe('the investments table (the portfolio)', () => {
  it('shows an empty state when there is nothing', async () => {
    renderApp()

    expect(await screen.findByText(/No investments yet/)).toBeInTheDocument()
  })

  it('adds the initial amount and the transaction changes together', async () => {
    // The spec example: initial Gold 2, transactions -1 and +5, so Gold shows 2 + (-1) + 5 = 6
    renderApp([INITIAL_GOLD], [], [GOLD_MINUS_ONE, GOLD_PLUS_FIVE])

    const rows = await within(investmentsSection()).findAllByRole('row')

    expect(rows).toHaveLength(2)
    expect(within(rows[1]).getByText('Gold')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Precious Metal')).toBeInTheDocument()
    expect(within(rows[1]).getByText('6')).toBeInTheDocument()
    expect(within(rows[1]).getByText('1')).toBeInTheDocument()
  })

  it('lists every investment sorted by name, including ones that only have transactions', async () => {
    renderApp([INITIAL_GOLD, INITIAL_ETH], [], [BTC_TX])

    const rows = await within(investmentsSection()).findAllByRole('row')

    expect(rows).toHaveLength(4)
    expect(within(rows[1]).getByText('Bitcoin')).toBeInTheDocument()
    expect(within(rows[1]).getByText('-1.5')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Ethereum')).toBeInTheDocument()
    expect(within(rows[2]).getByText('3.5')).toBeInTheDocument()
    expect(within(rows[3]).getByText('Gold')).toBeInTheDocument()
  })

  it('keeps a total of zero', async () => {
    renderApp([INITIAL_GOLD], [], [{ id: 'tx-z', name: 'Gold', change: '-2', timestamp: '2026-03-01T10:00:00Z' }])

    const rows = await within(investmentsSection()).findAllByRole('row')

    expect(rows).toHaveLength(2)
    expect(within(rows[1]).getByText('0')).toBeInTheDocument()
  })

  it('shows a placeholder for a missing type', async () => {
    // "Gold (g)" is not in the catalog, so the fake backend derives no type for it
    renderApp([{ id: 'id-x', name: 'Gold (g)', amount: '5' }])

    const rows = await within(investmentsSection()).findAllByRole('row')

    expect(within(rows[1]).getByText('—')).toBeInTheDocument()
  })

  it('is read-only: there is nothing to edit or delete in it', async () => {
    renderApp([INITIAL_GOLD], [], [BTC_TX])
    await within(investmentsSection()).findAllByRole('row')

    expect(within(investmentsSection()).queryAllByRole('button')).toHaveLength(0)
  })

  it('shows the error and lets the user retry', async () => {
    const failOnce = http.get('/api/portfolio', () => HttpResponse.error(), { once: true })
    const { user } = renderApp([INITIAL_GOLD], [failOnce])

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await within(investmentsSection()).findByText('Gold')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('adding an initial investment', () => {
  it('shows it in the investments table and clears the form', async () => {
    const { user, backend } = renderApp()
    await screen.findByText(/No investments yet/)

    expect(screen.getByRole('heading', { name: 'Add initial investment' })).toBeInTheDocument()
    await fillForm(user, { name: '  Ethereum ', amount: '3.5' })
    await user.click(screen.getByRole('button', { name: 'Add' }))

    const rows = await within(investmentsSection()).findAllByRole('row')
    expect(within(rows[1]).getByText('Ethereum')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Cryptocurrency')).toBeInTheDocument()
    expect(within(rows[1]).getByText('3.5')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('')
    expect(screen.getByLabelText('Amount')).toHaveValue('')
    expect([...backend.rows.values()][0]).toMatchObject({ name: 'Ethereum', amount: 3.5 })
  })

  it('adds to the total of an investment that already exists', async () => {
    const { user } = renderApp([INITIAL_GOLD])
    await within(investmentsSection()).findByText('2')

    await fillForm(user, { name: 'Gold', amount: '3' })
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await within(investmentsSection()).findByText('5')).toBeInTheDocument()
    expect(within(investmentsSection()).getAllByRole('row')).toHaveLength(2)
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
            title: 'Invalid investment name',
            status: 400,
            detail: 'Unknown investment name: Dogecoin',
            errors: [{ field: 'name', message: 'Unknown investment name: Dogecoin' }],
          },
          { status: 400 },
        ),
      ),
    )

    await fillForm(user, { name: 'Dogecoin', amount: '5' })
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Unknown investment name: Dogecoin')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')
    // the user's input is kept so they can correct it
    expect(screen.getByLabelText('Name')).toHaveValue('Dogecoin')
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

describe('the investments table follows the transactions', () => {
  it('updates when a transaction is added', async () => {
    const { user } = renderApp([INITIAL_GOLD])
    await within(investmentsSection()).findByText('2')

    await user.click(await screen.findByRole('button', { name: 'Add Transaction' }))
    await fillTransactionModal(user, { name: 'Gold', change: '5', timestamp: '2026-03-01T10:00' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(investmentsSection()).findByText('7')).toBeInTheDocument()
    expect(within(investmentsSection()).queryByText('2')).not.toBeInTheDocument()
  })

  it('updates when a transaction is edited', async () => {
    const { user } = renderApp([INITIAL_GOLD], [], [GOLD_PLUS_FIVE])
    await within(investmentsSection()).findByText('7')

    await user.click(await screen.findByRole('button', { name: 'Edit transaction for Gold' }))
    await user.clear(screen.getByLabelText('Change'))
    await user.type(screen.getByLabelText('Change'), '1')
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(investmentsSection()).findByText('3')).toBeInTheDocument()
  })

  it('updates when a transaction is deleted', async () => {
    const { user } = renderApp([INITIAL_GOLD], [], [GOLD_PLUS_FIVE])
    await within(investmentsSection()).findByText('7')

    await user.click(await screen.findByRole('button', { name: 'Delete transaction for Gold' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete transaction for Gold' }))

    expect(await within(investmentsSection()).findByText('2')).toBeInTheDocument()
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
