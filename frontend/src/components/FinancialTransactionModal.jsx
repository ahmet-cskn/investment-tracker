import { useState } from 'react'
import { ApiError } from '../api/financialTransactionsApi.js'
import { formatAmount, validateChange, validateName } from '../utils/amount.js'
import Modal from './Modal.jsx'

/**
 * Add/edit modal for a financial transaction. Pass `editing` to edit an existing one.
 * `onSubmit({ name, change, date })` must return a promise and reject with an ApiError on failure.
 * `onClose` is called both for Cancel and for the dialog's own close (e.g. the Escape key).
 */
export default function FinancialTransactionModal({ open, editing, onSubmit, onClose }) {
  return (
    <Modal open={open} onClose={onClose}>
      <FinancialTransactionModalForm
        key={editing?.id ?? 'new'}
        editing={editing}
        onSubmit={onSubmit}
        onClose={onClose}
      />
    </Modal>
  )
}

function FinancialTransactionModalForm({ editing, onSubmit, onClose }) {
  const isEditing = Boolean(editing)
  const [name, setName] = useState(editing?.name ?? '')
  const [change, setChange] = useState(editing ? formatAmount(editing.change) : '')
  const [date, setDate] = useState(editing?.date ?? '')
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError(null)

    const errors = {}
    const nameError = validateName(name)
    if (nameError) errors.name = nameError
    const changeError = validateChange(change)
    if (changeError) errors.change = changeError
    if (!date) errors.date = 'Date is required'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit({ name: name.trim(), change: formatAmount(change), date })
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
      <h2>{isEditing ? 'Edit financial transaction' : 'Add financial transaction'}</h2>

      <div className="field">
        <label htmlFor="financial-transaction-name">Name</label>
        <input
          id="financial-transaction-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="e.g. Groceries or Salary"
          aria-invalid={Boolean(fieldErrors.name)}
          aria-describedby={fieldErrors.name ? 'financial-transaction-name-error' : undefined}
        />
        {fieldErrors.name && (
          <p id="financial-transaction-name-error" className="field-error">
            {fieldErrors.name}
          </p>
        )}
      </div>

      <div className="field">
        <label htmlFor="financial-transaction-change">Change</label>
        <input
          id="financial-transaction-change"
          inputMode="decimal"
          value={change}
          onChange={(event) => setChange(event.target.value)}
          placeholder="e.g. 3000 or -42.5"
          aria-invalid={Boolean(fieldErrors.change)}
          aria-describedby={fieldErrors.change ? 'financial-transaction-change-error' : undefined}
        />
        {fieldErrors.change && (
          <p id="financial-transaction-change-error" className="field-error">
            {fieldErrors.change}
          </p>
        )}
      </div>

      <div className="field">
        <label htmlFor="financial-transaction-date">Date</label>
        <input
          id="financial-transaction-date"
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
          aria-invalid={Boolean(fieldErrors.date)}
          aria-describedby={fieldErrors.date ? 'financial-transaction-date-error' : undefined}
        />
        {fieldErrors.date && (
          <p id="financial-transaction-date-error" className="field-error">
            {fieldErrors.date}
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
