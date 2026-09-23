import { useState } from 'react'
import ErrorBanner from './components/ErrorBanner.jsx'
import InvestmentForm from './components/InvestmentForm.jsx'
import PortfolioTable from './components/PortfolioTable.jsx'
import TransactionModal from './components/TransactionModal.jsx'
import TransactionTable from './components/TransactionTable.jsx'
import { useCatalog } from './hooks/useCatalog.js'
import { useCreateInvestment } from './hooks/useInvestments.js'
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
  const createInvestment = useCreateInvestment()

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

      <InvestmentForm onSubmit={(values) => createInvestment.mutateAsync(values)} />

      <section className="card">
        <h2>Your investments</h2>
        {portfolio.isPending && <p className="empty">Loading…</p>}
        {portfolio.isError && (
          <ErrorBanner message={portfolio.error.message} onRetry={() => portfolio.refetch()} />
        )}
        {portfolio.isSuccess && <PortfolioTable entries={portfolio.data} />}
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
