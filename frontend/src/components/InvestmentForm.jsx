import { useState } from 'react'
import { ApiError } from '../api/investmentsApi.js'
import { formatAmount, validateAmount, validateName } from '../utils/amount.js'

/**
 * Create/edit form. Pass `editing` to edit an existing investment; the parent should also set a `key`
 * that changes with it so the fields are re-initialised.
 * `onSubmit({ name, amount })` must return a promise and reject with an ApiError on failure.
 */
export default function InvestmentForm({ editing, onSubmit, onCancel }) {
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
    const nameError = validateName(name)
    if (nameError) errors.name = nameError
    const amountError = validateAmount(amount)
    if (amountError) errors.amount = amountError
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit({ name: name.trim(), amount: formatAmount(amount) })
      if (!isEditing) {
        setName('')
        setAmount('')
      }
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
    <form className="card form" onSubmit={handleSubmit} noValidate>
      <h2>{isEditing ? 'Edit initial investment' : 'Add initial investment'}</h2>

      <div className="field">
        <label htmlFor="investment-name">Name</label>
        <input
          id="investment-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="e.g. Gold (g)"
          aria-invalid={Boolean(fieldErrors.name)}
          aria-describedby={fieldErrors.name ? 'investment-name-error' : undefined}
        />
        {fieldErrors.name && (
          <p id="investment-name-error" className="field-error">
            {fieldErrors.name}
          </p>
        )}
      </div>

      <div className="field">
        <label htmlFor="investment-amount">Amount</label>
        <input
          id="investment-amount"
          inputMode="decimal"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          placeholder="e.g. 3.5"
          aria-invalid={Boolean(fieldErrors.amount)}
          aria-describedby={fieldErrors.amount ? 'investment-amount-error' : undefined}
        />
        {fieldErrors.amount && (
          <p id="investment-amount-error" className="field-error">
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
          {isEditing ? 'Save' : 'Add'}
        </button>
        {isEditing && (
          <button type="button" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
