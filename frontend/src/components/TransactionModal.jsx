import { useState } from 'react'
import { ApiError } from '../api/transactionsApi.js'
import { formatAmount, validateChange } from '../utils/amount.js'
import { fromDateTimeLocalValue, toDateTimeLocalValue } from '../utils/dateTime.js'
import Modal from './Modal.jsx'

/**
 * Add/edit modal for a transaction. Pass `editing` to edit an existing transaction.
 * `onSubmit({ name, change, timestamp })` must return a promise and reject with an ApiError on failure.
 * `onClose` is called both for Cancel and for the dialog's own close (e.g. the Escape key).
 */
export default function TransactionModal({ open, editing, catalog, onSubmit, onClose }) {
  return (
    <Modal open={open} onClose={onClose}>
      <TransactionModalForm
        key={editing?.id ?? 'new'}
        editing={editing}
        catalog={catalog}
        onSubmit={onSubmit}
        onClose={onClose}
      />
    </Modal>
  )
}

function TransactionModalForm({ editing, catalog, onSubmit, onClose }) {
  const isEditing = Boolean(editing)
  const [name, setName] = useState(editing?.name ?? '')
  const [change, setChange] = useState(editing ? formatAmount(editing.change) : '')
  const [timestamp, setTimestamp] = useState(editing ? toDateTimeLocalValue(editing.timestamp) : '')
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError(null)

    const errors = {}
    if (!name) errors.name = 'Choose an investment'
    const changeError = validateChange(change)
    if (changeError) errors.change = changeError
    if (!timestamp) errors.timestamp = 'Timestamp is required'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit({ name, change: formatAmount(change), timestamp: fromDateTimeLocalValue(timestamp) })
      onClose()
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        setFieldErrors(Object.fromEntries(error.fieldErrors.map(({ field, message }) => [field, message])))
      } else {
        setFormError(error.message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <h2>{isEditing ? 'Edit transaction' : 'Add transaction'}</h2>

      <div className="field">
        <label htmlFor="transaction-name">Investment</label>
        <select
          id="transaction-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(fieldErrors.name)}
          aria-describedby={fieldErrors.name ? 'transaction-name-error' : undefined}
        >
          <option value="" disabled>
            Select an investment
          </option>
          {catalog.map((entry) => (
            <option key={entry.name} value={entry.name}>
              {entry.name}
            </option>
          ))}
        </select>
        {fieldErrors.name && (
          <p id="transaction-name-error" className="field-error">
            {fieldErrors.name}
          </p>
        )}
      </div>

      <div className="field">
        <label htmlFor="transaction-change">Change</label>
        <input
          id="transaction-change"
          inputMode="decimal"
          value={change}
          onChange={(event) => setChange(event.target.value)}
          placeholder="e.g. 3.5 or -1.5"
          aria-invalid={Boolean(fieldErrors.change)}
          aria-describedby={fieldErrors.change ? 'transaction-change-error' : undefined}
        />
        {fieldErrors.change && (
          <p id="transaction-change-error" className="field-error">
            {fieldErrors.change}
          </p>
        )}
      </div>

      <div className="field">
        <label htmlFor="transaction-timestamp">Timestamp</label>
        <input
          id="transaction-timestamp"
          type="datetime-local"
          value={timestamp}
          onChange={(event) => setTimestamp(event.target.value)}
          aria-invalid={Boolean(fieldErrors.timestamp)}
          aria-describedby={fieldErrors.timestamp ? 'transaction-timestamp-error' : undefined}
        />
        {fieldErrors.timestamp && (
          <p id="transaction-timestamp-error" className="field-error">
            {fieldErrors.timestamp}
          </p>
        )}
      </div>

      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}

      <div className="form-actions">
        <button type="submit" className="primary" disabled={submitting}>
          OK
        </button>
        <button type="button" onClick={onClose} disabled={submitting}>
          Cancel
        </button>
      </div>
    </form>
  )
}
