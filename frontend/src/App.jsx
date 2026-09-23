import { useState } from 'react'
import ErrorBanner from './components/ErrorBanner.jsx'
import InvestmentForm from './components/InvestmentForm.jsx'
import InvestmentTable from './components/InvestmentTable.jsx'
import TransactionModal from './components/TransactionModal.jsx'
import TransactionTable from './components/TransactionTable.jsx'
import { useCatalog } from './hooks/useCatalog.js'
import {
  useCreateInvestment,
  useDeleteInvestment,
  useInvestments,
  useUpdateInvestment,
} from './hooks/useInvestments.js'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
  useUpdateTransaction,
} from './hooks/useTransactions.js'

export default function App() {
  const investments = useInvestments()
  const createInvestment = useCreateInvestment()
  const updateInvestment = useUpdateInvestment()
  const deleteInvestment = useDeleteInvestment()
  const [editingInvestment, setEditingInvestment] = useState(null)

  const transactions = useTransactions()
  const catalog = useCatalog()
  const createTransaction = useCreateTransaction()
  const updateTransaction = useUpdateTransaction()
  const deleteTransaction = useDeleteTransaction()
  const [transactionModalOpen, setTransactionModalOpen] = useState(false)
  const [editingTransaction, setEditingTransaction] = useState(null)

  async function handleInvestmentSubmit(values) {
    if (editingInvestment) {
      await updateInvestment.mutateAsync({ id: editingInvestment.id, ...values })
      setEditingInvestment(null)
    } else {
      await createInvestment.mutateAsync(values)
    }
  }

  async function handleInvestmentDelete(id) {
    await deleteInvestment.mutateAsync(id)
    if (editingInvestment?.id === id) setEditingInvestment(null)
  }

  function openAddTransaction() {
    setEditingTransaction(null)
    setTransactionModalOpen(true)
  }

  function openEditTransaction(transaction) {
    setEditingTransaction(transaction)
    setTransactionModalOpen(true)
  }

  async function handleTransactionSubmit(values) {
    if (editingTransaction) {
      await updateTransaction.mutateAsync({ id: editingTransaction.id, ...values })
    } else {
      await createTransaction.mutateAsync(values)
    }
  }

  return (
    <main>
      <h1>Investment Tracker</h1>

      <InvestmentForm
        key={editingInvestment?.id ?? 'new'}
        editing={editingInvestment}
        onSubmit={handleInvestmentSubmit}
        onCancel={() => setEditingInvestment(null)}
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
            editingId={editingInvestment?.id}
            onEdit={setEditingInvestment}
            onDelete={handleInvestmentDelete}
          />
        )}
      </section>

      {deleteTransaction.isError && (
        <ErrorBanner message={deleteTransaction.error.message} onDismiss={deleteTransaction.reset} />
      )}

      <section className="card">
        <h2>Your transactions</h2>
        {transactions.isPending && <p className="empty">Loading…</p>}
        {transactions.isError && (
          <ErrorBanner message={transactions.error.message} onRetry={() => transactions.refetch()} />
        )}
        {transactions.isSuccess && (
          <TransactionTable
            transactions={transactions.data}
            onEdit={openEditTransaction}
            onDelete={(id) => deleteTransaction.mutateAsync(id)}
          />
        )}

        <div className="transaction-actions">
          {catalog.isPending && <p className="empty">Loading investments…</p>}
          {catalog.isError && (
            <ErrorBanner message={catalog.error.message} onRetry={() => catalog.refetch()} />
          )}
          {catalog.isSuccess && (
            <button type="button" className="primary" onClick={openAddTransaction}>
              Add Transaction
            </button>
          )}
        </div>
      </section>

      <TransactionModal
        open={transactionModalOpen}
        editing={editingTransaction}
        catalog={catalog.data ?? []}
        onSubmit={handleTransactionSubmit}
        onClose={() => setTransactionModalOpen(false)}
      />
    </main>
  )
}
