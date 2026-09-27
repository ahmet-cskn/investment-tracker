import { useState } from 'react'
import { formatDate } from '../utils/date.js'
import { formatUsd } from '../utils/money.js'

/** `onDelete(id)` must return a promise; failures are reported by the parent, so they are ignored here. */
export default function FinancialTransactionTable({ transactions, onEdit, onDelete }) {
  const [confirmingId, setConfirmingId] = useState(null)
  const [deletingId, setDeletingId] = useState(null)

  async function confirmDelete(id) {
    setDeletingId(id)
    try {
      await onDelete(id)
    } catch {
      // Shown by the parent's error banner
    } finally {
      setDeletingId(null)
      setConfirmingId(null)
    }
  }

  if (transactions.length === 0) {
    return <p className="empty">No financial transactions yet. Add one below.</p>
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th className="numeric">Change</th>
          <th>Date</th>
          <th>
            <span className="visually-hidden">Actions</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {transactions.map((transaction) => {
          const { id, name, change, date } = transaction
          const isConfirming = confirmingId === id
          const isDeleting = deletingId === id
          return (
            <tr key={id}>
              <td>{name}</td>
              <td className="numeric">{formatUsd(change)}</td>
              <td>{formatDate(date)}</td>
              <td className="actions">
                {isConfirming ? (
                  <>
                    <span>Delete?</span>
                    <button
                      type="button"
                      className="danger"
                      aria-label={`Confirm delete financial transaction for ${name}`}
                      disabled={isDeleting}
                      onClick={() => confirmDelete(id)}
                    >
                      Yes
                    </button>
                    <button
                      type="button"
                      aria-label={`Cancel delete financial transaction for ${name}`}
                      disabled={isDeleting}
                      onClick={() => setConfirmingId(null)}
                    >
                      No
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      type="button"
                      aria-label={`Edit financial transaction for ${name}`}
                      onClick={() => onEdit(transaction)}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      aria-label={`Delete financial transaction for ${name}`}
                      onClick={() => setConfirmingId(id)}
                    >
                      Delete
                    </button>
                  </>
                )}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
