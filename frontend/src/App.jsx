import { useState } from 'react'
import ErrorBanner from './components/ErrorBanner.jsx'
import InvestmentForm from './components/InvestmentForm.jsx'
import InvestmentTable from './components/InvestmentTable.jsx'
import {
  useCreateInvestment,
  useDeleteInvestment,
  useInvestments,
  useUpdateInvestment,
} from './hooks/useInvestments.js'

export default function App() {
  const investments = useInvestments()
  const createInvestment = useCreateInvestment()
  const updateInvestment = useUpdateInvestment()
  const deleteInvestment = useDeleteInvestment()
  const [editing, setEditing] = useState(null)

  async function handleSubmit(values) {
    if (editing) {
      await updateInvestment.mutateAsync({ id: editing.id, ...values })
      setEditing(null)
    } else {
      await createInvestment.mutateAsync(values)
    }
  }

  async function handleDelete(id) {
    await deleteInvestment.mutateAsync(id)
    if (editing?.id === id) setEditing(null)
  }

  return (
    <main>
      <h1>Investment Tracker</h1>

      <InvestmentForm
        key={editing?.id ?? 'new'}
        editing={editing}
        onSubmit={handleSubmit}
        onCancel={() => setEditing(null)}
      />

      {deleteInvestment.isError && (
        <ErrorBanner message={deleteInvestment.error.message} onDismiss={deleteInvestment.reset} />
      )}

      <section className="card">
        <h2>Your investments</h2>
        {investments.isPending && <p className="empty">Loading…</p>}
        {investments.isError && (
          <ErrorBanner message={investments.error.message} onRetry={() => investments.refetch()} />
        )}
        {investments.isSuccess && (
          <InvestmentTable
            investments={investments.data}
            editingId={editing?.id}
            onEdit={setEditing}
            onDelete={handleDelete}
          />
        )}
      </section>
    </main>
  )
}
