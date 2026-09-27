import { useState } from 'react'
import ErrorBanner from './components/ErrorBanner.jsx'
import FinancialTransactionModal from './components/FinancialTransactionModal.jsx'
import FinancialTransactionTable from './components/FinancialTransactionTable.jsx'
import InitialInvestmentsModal from './components/InitialInvestmentsModal.jsx'
import NetWorth from './components/NetWorth.jsx'
import PortfolioTable from './components/PortfolioTable.jsx'
import TransactionModal from './components/TransactionModal.jsx'
import TransactionTable from './components/TransactionTable.jsx'
import { useCatalog } from './hooks/useCatalog.js'
import {
  useCreateFinancialTransaction,
  useDeleteFinancialTransaction,
  useFinancialTransactions,
  useUpdateFinancialTransaction,
} from './hooks/useFinancialTransactions.js'
import { usePortfolio } from './hooks/usePortfolio.js'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
  useUpdateTransaction,
} from './hooks/useTransactions.js'

export default function App() {
  const [activeTab, setActiveTab] = useState('investments')

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

  const financialTransactions = useFinancialTransactions()
  const createFinancialTransaction = useCreateFinancialTransaction()
  const updateFinancialTransaction = useUpdateFinancialTransaction()
  const deleteFinancialTransaction = useDeleteFinancialTransaction()
  const [financialTransactionModalOpen, setFinancialTransactionModalOpen] = useState(false)
  const [editingFinancialTransaction, setEditingFinancialTransaction] = useState(null)

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

  function openAddFinancialTransaction() {
    setEditingFinancialTransaction(null)
    setFinancialTransactionModalOpen(true)
  }

  function openEditFinancialTransaction(transaction) {
    setEditingFinancialTransaction(transaction)
    setFinancialTransactionModalOpen(true)
  }

  async function handleFinancialTransactionSubmit(values) {
    if (editingFinancialTransaction) {
      await updateFinancialTransaction.mutateAsync({ id: editingFinancialTransaction.id, ...values })
    } else {
      await createFinancialTransaction.mutateAsync(values)
    }
  }

  return (
    <main>
      <h1>Investment Tracker</h1>

      <div className="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'investments'}
          className={activeTab === 'investments' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('investments')}
        >
          Investments
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'finances'}
          className={activeTab === 'finances' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('finances')}
        >
          Daily Finances
        </button>
      </div>

      {activeTab === 'investments' && (
        <>
          {/* Only once the portfolio has loaded; if it failed, the investments card below shows why */}
          {portfolio.isSuccess && <NetWorth entries={portfolio.data} />}

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
        </>
      )}

      {activeTab === 'finances' && (
        <>
          {deleteFinancialTransaction.isError && (
            <ErrorBanner message={deleteFinancialTransaction.error.message} onDismiss={deleteFinancialTransaction.reset} />
          )}

          <section className="card">
            <h2>Daily Finances</h2>
            {financialTransactions.isPending && <p className="empty">Loading…</p>}
            {financialTransactions.isError && (
              <ErrorBanner
                message={financialTransactions.error.message}
                onRetry={() => financialTransactions.refetch()}
              />
            )}
            {financialTransactions.isSuccess && (
              <FinancialTransactionTable
                transactions={financialTransactions.data}
                onEdit={openEditFinancialTransaction}
                onDelete={(id) => deleteFinancialTransaction.mutateAsync(id)}
              />
            )}

            <div className="card-actions">
              <button type="button" className="primary" onClick={openAddFinancialTransaction}>
                Add Financial Transaction
              </button>
            </div>
          </section>

          <FinancialTransactionModal
            open={financialTransactionModalOpen}
            editing={editingFinancialTransaction}
            onSubmit={handleFinancialTransactionSubmit}
            onClose={() => setFinancialTransactionModalOpen(false)}
          />
        </>
      )}
    </main>
  )
}
