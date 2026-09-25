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
import { formatDate } from './utils/date.js'
import { formatUsd } from './utils/money.js'

// Initial investments (the rows behind /api/investments)
const INITIAL_GOLD = { id: 'id-a', name: 'Gold', amount: '2' }
const INITIAL_ETH = { id: 'id-b', name: 'Ethereum', amount: '3.5' }

// Transactions
const BTC_TX = { id: 'tx-a', name: 'Bitcoin', change: '-1.5', date: '2026-01-15' }
const GOLD_TX = { id: 'tx-b', name: 'Gold', change: '2.5', date: '2026-02-15' }
const GOLD_MINUS_ONE = { id: 'tx-c', name: 'Gold', change: '-1', date: '2026-03-01' }
const GOLD_PLUS_FIVE = { id: 'tx-d', name: 'Gold', change: '5', date: '2026-03-02' }

// App always renders the portfolio, the transactions and the catalog-backed "Add Transaction" button, so
// every render needs all of them mocked. initial/initialTransactions seed the fake backends and the fake
// portfolio is computed from both, like the real one; overrides let a test customise just what it needs.
function renderApp(initial = [], overrides = [], initialTransactions = [], transactionPrices = {}) {
  const backend = createFakeBackend(initial)
  const transactionsBackend = createFakeTransactionsBackend(initialTransactions, { prices: transactionPrices })
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

async function openInitialInvestments(user) {
  const button = await screen.findByRole('button', { name: 'Edit Initial Investments' })
  // disabled until the catalog (which its dropdown needs) has loaded
  await waitFor(() => expect(button).toBeEnabled())
  await user.click(button)
  return (await screen.findByRole('heading', { name: 'Initial investments' })).closest('dialog')
}

async function fillInitialInvestmentForm(user, { name, amount }) {
  if (name !== undefined) await user.selectOptions(screen.getByLabelText('Investment'), name)
  if (amount !== undefined) await user.type(screen.getByLabelText('Amount'), amount)
}

async function fillTransactionModal(user, { name, change, date }) {
  if (name !== undefined) await user.selectOptions(screen.getByLabelText('Investment'), name)
  if (change !== undefined) await user.type(screen.getByLabelText('Change'), change)
  if (date !== undefined) {
    fireEvent.change(screen.getByLabelText('Date'), { target: { value: date } })
  }
}

// The same investment names show up in several places (the investments table, the transactions table, and
// a modal's table or dropdown while it is open), so a bare getByText('Ethereum') can match more than one
// element; scope queries to the section or dialog under test.
function investmentsSection() {
  return screen.getByRole('heading', { name: 'Your investments' }).closest('section')
}

function transactionsSection() {
  return screen.getByRole('heading', { name: 'Your transactions' }).closest('section')
}

function netWorth() {
  return screen.getByRole('region', { name: 'Net worth' })
}

describe('the net worth', () => {
  it('shows the sum of the investments table\'s worth column', async () => {
    // Gold (2 + 2.5) at 100 each, Ethereum 3.5 at 3,000 each, Bitcoin -1.5 at 50,000 each
    renderApp([INITIAL_GOLD, INITIAL_ETH], [], [BTC_TX, GOLD_TX])

    await within(investmentsSection()).findAllByRole('row')

    expect(within(netWorth()).getByText(formatUsd('-64050'))).toBeInTheDocument()
    expect(within(netWorth()).queryByText(/without a price/)).not.toBeInTheDocument()
  })

  it('is zero when there is nothing', async () => {
    renderApp()

    await screen.findByText(/No investments yet/)

    expect(within(netWorth()).getByText(formatUsd('0'))).toBeInTheDocument()
  })

  it('leaves out investments without a price and says so', async () => {
    renderApp([INITIAL_ETH], [], [BTC_TX, GOLD_TX], { Gold: null })

    await within(investmentsSection()).findAllByRole('row')

    // Ethereum 10,500 and Bitcoin -75,000; Gold has no price
    expect(within(netWorth()).getByText(formatUsd('-64500'))).toBeInTheDocument()
    expect(within(netWorth()).getByText('1 investment without a price is not included')).toBeInTheDocument()
  })

  it('is not shown when the portfolio could not be loaded', async () => {
    renderApp([], [http.get('/api/portfolio', () => HttpResponse.error())])

    await screen.findByText(/Could not reach the server/)

    expect(screen.queryByRole('region', { name: 'Net worth' })).not.toBeInTheDocument()
  })

  it('updates when a transaction changes the portfolio', async () => {
    const { user } = renderApp([INITIAL_GOLD])
    await screen.findByText(formatUsd('200'), { selector: '.net-worth-value' })

    await user.click(await screen.findByRole('button', { name: 'Add Transaction' }))
    await fillTransactionModal(user, { name: 'Gold', change: '3', date: '2026-03-01' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await screen.findByText(formatUsd('500'), { selector: '.net-worth-value' })).toBeInTheDocument()
  })
})

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
    // the worth is the total at the latest price: 6 at 100 each
    expect(within(rows[1]).getByText(formatUsd('600'))).toBeInTheDocument()
  })

  it('shows each worth in dollars, negative for a negative total, and a dash where there is no price', async () => {
    renderApp([INITIAL_ETH], [], [BTC_TX, GOLD_TX], { Gold: null })

    const rows = await within(investmentsSection()).findAllByRole('row')

    // Bitcoin -1.5 at 50,000 each, Ethereum 3.5 at 3,000 each, Gold 2.5 with no price
    expect(within(rows[1]).getByText(formatUsd('-75000'))).toBeInTheDocument()
    expect(within(rows[2]).getByText(formatUsd('10500'))).toBeInTheDocument()
    expect(within(rows[3]).getByText('2.5')).toBeInTheDocument()
    expect(within(rows[3]).getByText('—')).toBeInTheDocument()
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
    renderApp([INITIAL_GOLD], [], [{ id: 'tx-z', name: 'Gold', change: '-2', date: '2026-03-01' }])

    const rows = await within(investmentsSection()).findAllByRole('row')

    expect(rows).toHaveLength(2)
    expect(within(rows[1]).getByText('0')).toBeInTheDocument()
  })

  it('shows a placeholder for a missing type', async () => {
    // "Gold (g)" is not in the catalog, so the fake backend derives no type for it
    renderApp([{ id: 'id-x', name: 'Gold (g)', amount: '5' }])

    const rows = await within(investmentsSection()).findAllByRole('row')

    // neither a type nor a price for a name outside the catalog
    expect(within(rows[1]).getAllByText('—')).toHaveLength(2)
  })

  it('is read-only: its rows have nothing to edit or delete, only the button for the initial investments', async () => {
    renderApp([INITIAL_GOLD], [], [BTC_TX])
    await within(investmentsSection()).findAllByRole('row')

    const buttons = within(investmentsSection()).getAllByRole('button')
    expect(buttons.map((button) => button.textContent)).toEqual(['Edit Initial Investments'])
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

describe('the initial investments window', () => {
  it('opens from the button and lists the initial investments sorted by name', async () => {
    const { user } = renderApp([INITIAL_GOLD, INITIAL_ETH])

    const dialog = await openInitialInvestments(user)
    const rows = await within(dialog).findAllByRole('row')

    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('Ethereum')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Cryptocurrency')).toBeInTheDocument()
    expect(within(rows[1]).getByText('3.5')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Gold')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Precious Metal')).toBeInTheDocument()
    expect(within(rows[2]).getByText('2')).toBeInTheDocument()
  })

  it('shows an empty state', async () => {
    const { user } = renderApp()

    const dialog = await openInitialInvestments(user)

    expect(await within(dialog).findByText(/No initial investments yet/)).toBeInTheDocument()
  })

  it('is closed by its Close button', async () => {
    const { user } = renderApp()
    const dialog = await openInitialInvestments(user)

    await user.click(within(dialog).getByRole('button', { name: 'Close' }))

    expect(screen.queryByRole('heading', { name: 'Initial investments' })).not.toBeInTheDocument()
  })

  it('does not fetch anything until it is opened', async () => {
    let requests = 0
    const counting = http.get('/api/investments', () => {
      requests += 1
      return HttpResponse.json([])
    })
    const { user } = renderApp([], [counting])
    await screen.findByText(/No investments yet/)
    expect(requests).toBe(0)

    await openInitialInvestments(user)

    await waitFor(() => expect(requests).toBe(1))
  })

  it('shows the error and lets the user retry', async () => {
    const failOnce = http.get('/api/investments', () => HttpResponse.error(), { once: true })
    const { user } = renderApp([INITIAL_GOLD], [failOnce])

    const dialog = await openInitialInvestments(user)

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Could not reach the server')
    await user.click(within(dialog).getByRole('button', { name: 'Retry' }))

    expect(await within(dialog).findByText('Precious Metal')).toBeInTheDocument()
  })

  it('cannot be opened while the catalog is unavailable', async () => {
    const catalogDown = http.get('/api/catalog', () => HttpResponse.error())
    renderApp([], [catalogDown])

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
    expect(screen.getByRole('button', { name: 'Edit Initial Investments' })).toBeDisabled()
  })
})

describe('adding an initial investment', () => {
  it('opens a second modal, and the new row appears in the window and in the investments table', async () => {
    const { user, backend } = renderApp()
    const dialog = await openInitialInvestments(user)

    await user.click(within(dialog).getByRole('button', { name: 'Add Initial Investment' }))
    expect(screen.getByRole('heading', { name: 'Add initial investment' })).toBeInTheDocument()
    await fillInitialInvestmentForm(user, { name: 'Ethereum', amount: '3.5' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    // the second modal closes and the window underneath is still open, now with the new row
    expect(screen.queryByRole('heading', { name: 'Add initial investment' })).not.toBeInTheDocument()
    const windowRows = await within(dialog).findAllByRole('row')
    expect(within(windowRows[1]).getByText('Ethereum')).toBeInTheDocument()
    expect(within(windowRows[1]).getByText('3.5')).toBeInTheDocument()
    // and the investments table behind it follows
    const tableRows = await within(investmentsSection()).findAllByRole('row')
    expect(within(tableRows[1]).getByText('Ethereum')).toBeInTheDocument()
    expect(within(tableRows[1]).getByText('Cryptocurrency')).toBeInTheDocument()
    expect([...backend.rows.values()][0]).toMatchObject({ name: 'Ethereum', amount: 3.5 })
  })

  it('adds to the total of an investment that already exists, as a second row', async () => {
    const { user } = renderApp([INITIAL_GOLD])
    await within(investmentsSection()).findByText('2')
    const dialog = await openInitialInvestments(user)

    await user.click(within(dialog).getByRole('button', { name: 'Add Initial Investment' }))
    await fillInitialInvestmentForm(user, { name: 'Gold', amount: '3' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(investmentsSection()).findByText('5')).toBeInTheDocument()
    expect(within(investmentsSection()).getAllByRole('row')).toHaveLength(2)
    expect(await within(dialog).findAllByRole('row')).toHaveLength(3)
  })

  it('validates on the client without calling the backend', async () => {
    const { user, backend } = renderApp()
    const dialog = await openInitialInvestments(user)
    await user.click(within(dialog).getByRole('button', { name: 'Add Initial Investment' }))

    await fillInitialInvestmentForm(user, { amount: 'abc' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(screen.getByText('Choose an investment')).toBeInTheDocument()
    expect(screen.getByText('Enter a number such as 5 or 3.5')).toBeInTheDocument()
    expect(backend.rows.size).toBe(0)
  })

  it('shows the backend error for an unknown investment name and keeps the second modal open', async () => {
    const { user } = renderApp()
    const dialog = await openInitialInvestments(user)
    server.use(
      http.post('/api/investments', () =>
        HttpResponse.json(
          {
            title: 'Invalid investment name',
            status: 400,
            detail: 'Unknown investment name: Gold',
            errors: [{ field: 'name', message: 'Unknown investment name: Gold' }],
          },
          { status: 400 },
        ),
      ),
    )

    await user.click(within(dialog).getByRole('button', { name: 'Add Initial Investment' }))
    await fillInitialInvestmentForm(user, { name: 'Gold', amount: '2' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await screen.findByText('Unknown investment name: Gold')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Add initial investment' })).toBeInTheDocument()
  })

  it('closes the second modal without submitting on Cancel', async () => {
    const { user, backend } = renderApp()
    const dialog = await openInitialInvestments(user)
    await user.click(within(dialog).getByRole('button', { name: 'Add Initial Investment' }))

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('heading', { name: 'Add initial investment' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Initial investments' })).toBeInTheDocument()
    expect(backend.rows.size).toBe(0)
  })
})

describe('editing an initial investment', () => {
  it('prefills the second modal and updates the window and the investments table', async () => {
    const { user, backend } = renderApp([INITIAL_GOLD])
    const dialog = await openInitialInvestments(user)

    await user.click(await within(dialog).findByRole('button', { name: 'Edit initial investment Gold' }))
    expect(screen.getByRole('heading', { name: 'Edit initial investment' })).toBeInTheDocument()
    expect(screen.getByLabelText('Investment')).toHaveValue('Gold')
    expect(screen.getByLabelText('Amount')).toHaveValue('2')

    await user.clear(screen.getByLabelText('Amount'))
    await user.type(screen.getByLabelText('Amount'), '4')
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(dialog).findByText('4')).toBeInTheDocument()
    expect(await within(investmentsSection()).findByText('4')).toBeInTheDocument()
    expect(backend.rows.get(INITIAL_GOLD.id)).toMatchObject({ name: 'Gold', amount: 4 })
  })

  it('can change which investment a row is for', async () => {
    const { user, backend } = renderApp([INITIAL_GOLD])
    const dialog = await openInitialInvestments(user)

    await user.click(await within(dialog).findByRole('button', { name: 'Edit initial investment Gold' }))
    await user.selectOptions(screen.getByLabelText('Investment'), 'Bitcoin')
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(dialog).findByText('Cryptocurrency')).toBeInTheDocument()
    expect(backend.rows.get(INITIAL_GOLD.id)).toMatchObject({ name: 'Bitcoin' })
  })
})

describe('deleting an initial investment', () => {
  it('asks for confirmation, then removes it from the window and the investments table', async () => {
    const { user, backend } = renderApp([INITIAL_GOLD, INITIAL_ETH])
    const dialog = await openInitialInvestments(user)
    await user.click(await within(dialog).findByRole('button', { name: 'Delete initial investment Gold' }))

    await user.click(within(dialog).getByRole('button', { name: 'Cancel delete initial investment Gold' }))
    expect(backend.rows.size).toBe(2)

    await user.click(within(dialog).getByRole('button', { name: 'Delete initial investment Gold' }))
    await user.click(within(dialog).getByRole('button', { name: 'Confirm delete initial investment Gold' }))

    await waitFor(() => expect(within(dialog).queryByText('Gold')).not.toBeInTheDocument())
    await waitFor(() => expect(within(investmentsSection()).queryByText('Gold')).not.toBeInTheDocument())
    expect(within(investmentsSection()).getByText('Ethereum')).toBeInTheDocument()
    expect(backend.rows.has(INITIAL_GOLD.id)).toBe(false)
  })

  it('shows an error banner in the window when deleting fails', async () => {
    const { user } = renderApp([INITIAL_GOLD])
    const dialog = await openInitialInvestments(user)
    server.use(
      http.delete('/api/investments/:id', () =>
        HttpResponse.json({ title: 'Internal server error', status: 500, detail: 'An unexpected error occurred' }, { status: 500 }),
      ),
    )

    await user.click(await within(dialog).findByRole('button', { name: 'Delete initial investment Gold' }))
    await user.click(within(dialog).getByRole('button', { name: 'Confirm delete initial investment Gold' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('An unexpected error occurred')
    expect(within(dialog).getByText('Gold')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Dismiss' }))
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('transaction listing', () => {
  it('shows an empty state when there are no transactions', async () => {
    renderApp()

    expect(await screen.findByText(/No transactions yet/)).toBeInTheDocument()
  })

  it('lists transactions newest first, with type derived from the catalog', async () => {
    // the fake backend, like the real one, sorts by date; GOLD_TX is later than BTC_TX
    renderApp([], [], [BTC_TX, GOLD_TX])

    const rows = await within(transactionsSection()).findAllByRole('row')

    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('Gold')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Precious Metal')).toBeInTheDocument()
    expect(within(rows[1]).getByText('2.5')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Bitcoin')).toBeInTheDocument()
    expect(within(rows[2]).getByText('-1.5')).toBeInTheDocument()
  })

  it('shows each transaction\'s worth in dollars, negative for a decrease', async () => {
    // fake prices: Gold 100 per unit, Bitcoin 50,000; GOLD_TX is +2.5 and BTC_TX is -1.5
    renderApp([], [], [BTC_TX, GOLD_TX])

    const rows = await within(transactionsSection()).findAllByRole('row')

    expect(within(rows[0]).getByText('Worth')).toBeInTheDocument()
    expect(within(rows[1]).getByText(formatUsd('250'))).toBeInTheDocument()
    expect(within(rows[2]).getByText(formatUsd('-75000'))).toBeInTheDocument()
  })

  it('shows a dash for a transaction that was saved without a worth', async () => {
    renderApp([], [], [GOLD_TX], { Gold: null })

    const rows = await within(transactionsSection()).findAllByRole('row')

    expect(within(rows[1]).getByText('—')).toBeInTheDocument()
    expect(within(rows[1]).queryByText(formatUsd('250'))).not.toBeInTheDocument()
  })

  it('shows each transaction\'s date, without a time', async () => {
    renderApp([], [], [BTC_TX])

    const rows = await within(transactionsSection()).findAllByRole('row')

    expect(within(rows[1]).getByText(formatDate('2026-01-15'))).toBeInTheDocument()
  })

  it('orders transactions on the same day by name', async () => {
    const sameDay = '2026-04-01'
    renderApp([], [], [
      { id: 'tx-1', name: 'Silver', change: '1', date: sameDay },
      { id: 'tx-2', name: 'Bitcoin', change: '2', date: sameDay },
      { id: 'tx-3', name: 'Gold', change: '3', date: sameDay },
    ])

    const rows = await within(transactionsSection()).findAllByRole('row')

    expect(within(rows[1]).getByText('Bitcoin')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Gold')).toBeInTheDocument()
    expect(within(rows[3]).getByText('Silver')).toBeInTheDocument()
  })
})

describe('adding a transaction', () => {
  it('opens the modal, submits, and shows the new row', async () => {
    const { user, transactionsBackend } = renderApp()
    await screen.findByText(/No transactions yet/)

    await user.click(await screen.findByRole('button', { name: 'Add Transaction' }))
    expect(screen.getByRole('heading', { name: 'Add transaction' })).toBeInTheDocument()

    await fillTransactionModal(user, { name: 'Bitcoin', change: '-1.5', date: '2026-01-15' })
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(transactionsSection()).findByText('Bitcoin')).toBeInTheDocument()
    expect(within(transactionsSection()).getByText('Cryptocurrency')).toBeInTheDocument()
    expect(within(transactionsSection()).getByText('-1.5')).toBeInTheDocument()
    // the worth is worked out by the backend, so the modal never asks for it: -1.5 at 50,000 each
    expect(within(transactionsSection()).getByText(formatUsd('-75000'))).toBeInTheDocument()
    expect(screen.queryByLabelText('Worth')).not.toBeInTheDocument()
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
    await fillTransactionModal(user, { name: 'Bitcoin', change: '1', date: '2026-01-15' })
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

    // Change the investment and the amount; leave the prefilled date as-is
    await user.selectOptions(screen.getByLabelText('Investment'), 'Gold')
    await user.clear(screen.getByLabelText('Change'))
    await user.type(screen.getByLabelText('Change'), '3')
    await user.click(screen.getByRole('button', { name: 'OK' }))

    expect(await within(transactionsSection()).findByText('Precious Metal')).toBeInTheDocument()
    expect(within(transactionsSection()).queryByText('Cryptocurrency')).not.toBeInTheDocument()
    // the worth was worked out again for the new investment and change: 3 at 100 each
    expect(within(transactionsSection()).getByText(formatUsd('300'))).toBeInTheDocument()
    expect(within(transactionsSection()).queryByText(formatUsd('-75000'))).not.toBeInTheDocument()
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
    await fillTransactionModal(user, { name: 'Gold', change: '5', date: '2026-03-01' })
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
