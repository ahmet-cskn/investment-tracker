import { useState } from 'react'
import { ApiError } from '../api/investmentsApi.js'
import { formatAmount, validateAmount } from '../utils/amount.js'
import Modal from './Modal.jsx'

/**
 * Add/edit modal for an initial investment. Pass `editing` to edit an existing one.
 * `onSubmit({ name, amount })` must return a promise and reject with an ApiError on failure.
 * `onClose` is called both for Cancel and for the dialog's own close (e.g. the Escape key).
 */
export default function InitialInvestmentFormModal({ open, editing, catalog, onSubmit, onClose }) {
  return (
    <Modal open={open} onClose={onClose}>
      <InitialInvestmentForm
        key={editing?.id ?? 'new'}
        editing={editing}
        catalog={catalog}
        onSubmit={onSubmit}
        onClose={onClose}
      />
    </Modal>
  )
}

function InitialInvestmentForm({ editing, catalog, onSubmit, onClose }) {
  const isEditing = Boolean(editing)
  const [name, setName] = useState(editing?.name ?? '')
  const [amount, setAmount] = useState(editing ? formatAmount(editing.amount) : '')
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError(null)

    const errors = {}
    if (!name) errors.name = 'Choose an investment'
    const amountError = validateAmount(amount)
    if (amountError) errors.amount = amountError
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit({ name, amount: formatAmount(amount) })
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
      <h2>{isEditing ? 'Edit initial investment' : 'Add initial investment'}</h2>

      <div className="field">
        <label htmlFor="initial-investment-name">Investment</label>
        <select
          id="initial-investment-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(fieldErrors.name)}
          aria-describedby={fieldErrors.name ? 'initial-investment-name-error' : undefined}
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
          <p id="initial-investment-name-error" className="field-error">
            {fieldErrors.name}
          </p>
        )}
      </div>

      <div className="field">
        <label htmlFor="initial-investment-amount">Amount</label>
        <input
          id="initial-investment-amount"
          inputMode="decimal"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          placeholder="e.g. 3.5"
          aria-invalid={Boolean(fieldErrors.amount)}
          aria-describedby={fieldErrors.amount ? 'initial-investment-amount-error' : undefined}
        />
        {fieldErrors.amount && (
          <p id="initial-investment-amount-error" className="field-error">
            {fieldErrors.amount}
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
