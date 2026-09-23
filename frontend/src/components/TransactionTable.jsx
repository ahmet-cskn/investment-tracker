import { useState } from 'react'
import { formatAmount } from '../utils/amount.js'
import { formatTimestamp } from '../utils/dateTime.js'

/** `onDelete(id)` must return a promise; failures are reported by the parent, so they are ignored here. */
export default function TransactionTable({ transactions, onEdit, onDelete }) {
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
    return <p className="empty">No transactions yet. Add one below.</p>
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>Type</th>
          <th className="numeric">Change</th>
          <th>Timestamp</th>
          <th>
            <span className="visually-hidden">Actions</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {transactions.map((transaction) => {
          const { id, name, investmentType, change, timestamp } = transaction
          const isConfirming = confirmingId === id
          const isDeleting = deletingId === id
          return (
            <tr key={id}>
              <td>{name}</td>
              <td>{investmentType ?? '—'}</td>
              <td className="numeric">{formatAmount(change)}</td>
              <td>{formatTimestamp(timestamp)}</td>
              <td className="actions">
                {isConfirming ? (
                  <>
                    <span>Delete?</span>
                    <button
                      type="button"
                      className="danger"
                      aria-label={`Confirm delete transaction for ${name}`}
                      disabled={isDeleting}
                      onClick={() => confirmDelete(id)}
                    >
                      Yes
                    </button>
                    <button
                      type="button"
                      aria-label={`Cancel delete transaction for ${name}`}
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
                      aria-label={`Edit transaction for ${name}`}
                      onClick={() => onEdit(transaction)}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      aria-label={`Delete transaction for ${name}`}
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
