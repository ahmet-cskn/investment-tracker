import { useState } from 'react'
import ErrorBanner from './components/ErrorBanner.jsx'
import InitialInvestmentsModal from './components/InitialInvestmentsModal.jsx'
import PortfolioTable from './components/PortfolioTable.jsx'
import TransactionModal from './components/TransactionModal.jsx'
import TransactionTable from './components/TransactionTable.jsx'
import { useCatalog } from './hooks/useCatalog.js'
import { usePortfolio } from './hooks/usePortfolio.js'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
  useUpdateTransaction,
} from './hooks/useTransactions.js'

export default function App() {
  // The investments table is the portfolio: initial investments plus the sum of the transactions
  const portfolio = usePortfolio()
  const [initialInvestmentsOpen, setInitialInvestmentsOpen] = useState(false)

  const transactions = useTransactions()
  const catalog = useCatalog()
  const createTransaction = useCreateTransaction()
  const updateTransaction = useUpdateTransaction()
  const deleteTransaction = useDeleteTransaction()
  const [transactionModalOpen, setTransactionModalOpen] = useState(false)
  const [editingTransaction, setEditingTransaction] = useState(null)

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

      <section className="card">
        <h2>Your investments</h2>
        {portfolio.isPending && <p className="empty">Loading…</p>}
        {portfolio.isError && (
          <ErrorBanner message={portfolio.error.message} onRetry={() => portfolio.refetch()} />
        )}
        {portfolio.isSuccess && <PortfolioTable entries={portfolio.data} />}

        <div className="card-actions">
          {/* The modal's dropdown needs the catalog; if it failed to load, the transactions section below shows why */}
          <button type="button" disabled={!catalog.isSuccess} onClick={() => setInitialInvestmentsOpen(true)}>
            Edit Initial Investments
          </button>
        </div>
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

        <div className="card-actions">
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

      <InitialInvestmentsModal
        open={initialInvestmentsOpen}
        catalog={catalog.data ?? []}
        onClose={() => setInitialInvestmentsOpen(false)}
      />

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
