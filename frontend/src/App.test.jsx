import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App.jsx'
import { createFakeBackend } from './test/fakeBackend.js'
import { server } from './test/server.js'

const GOLD = { id: 'id-a', name: 'Gold (g)', amount: '5' }
const ETH = { id: 'id-b', name: 'Ethereum', amount: '3.5' }

function renderApp(initial = [], overrides = []) {
  const backend = createFakeBackend(initial)
  server.use(...overrides, ...backend.handlers)
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
  return { user, backend }
}

async function fillForm(user, { name, amount }) {
  const nameInput = screen.getByLabelText('Name')
  const amountInput = screen.getByLabelText('Amount')
  await user.clear(nameInput)
  await user.clear(amountInput)
  if (name) await user.type(nameInput, name)
  if (amount) await user.type(amountInput, amount)
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

    expect(await screen.findByText('Ethereum')).toBeInTheDocument()
    expect(screen.getByText('3.5')).toBeInTheDocument()
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
    expect(screen.getByText('Ethereum')).toBeInTheDocument()
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
