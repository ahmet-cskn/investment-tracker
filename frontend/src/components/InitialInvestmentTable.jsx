import { useState } from 'react'
import { formatAmount } from '../utils/amount.js'

/** `onDelete(id)` must return a promise; failures are reported by the parent, so they are ignored here. */
export default function InitialInvestmentTable({ investments, onEdit, onDelete }) {
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

  if (investments.length === 0) {
    return <p className="empty">No initial investments yet. Add one below.</p>
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>Type</th>
          <th className="numeric">Amount</th>
          <th>
            <span className="visually-hidden">Actions</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {investments.map((investment) => {
          const { id, name, investmentType, amount } = investment
          const isConfirming = confirmingId === id
          const isDeleting = deletingId === id
          return (
            <tr key={id}>
              <td>{name}</td>
              <td>{investmentType ?? '—'}</td>
              <td className="numeric">{formatAmount(amount)}</td>
              <td className="actions">
                {isConfirming ? (
                  <>
                    <span>Delete?</span>
                    <button
                      type="button"
                      className="danger"
                      aria-label={`Confirm delete initial investment ${name}`}
                      disabled={isDeleting}
                      onClick={() => confirmDelete(id)}
                    >
                      Yes
                    </button>
                    <button
                      type="button"
                      aria-label={`Cancel delete initial investment ${name}`}
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
                      aria-label={`Edit initial investment ${name}`}
                      onClick={() => onEdit(investment)}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      aria-label={`Delete initial investment ${name}`}
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
