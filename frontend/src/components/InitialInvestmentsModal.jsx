import { useState } from 'react'
import {
  useCreateInvestment,
  useDeleteInvestment,
  useInvestments,
  useUpdateInvestment,
} from '../hooks/useInvestments.js'
import ErrorBanner from './ErrorBanner.jsx'
import InitialInvestmentFormModal from './InitialInvestmentFormModal.jsx'
import InitialInvestmentTable from './InitialInvestmentTable.jsx'
import Modal from './Modal.jsx'

/**
 * The window for managing the initial investments (what the user already owns before any transaction):
 * a table with Edit/Delete and an Add button, the same shape as the transaction history section.
 * Add and Edit open a second modal on top of this one.
 */
export default function InitialInvestmentsModal({ open, catalog, onClose }) {
  // Nothing is fetched until the modal is opened
  const investments = useInvestments({ enabled: open })
  const createInvestment = useCreateInvestment()
  const updateInvestment = useUpdateInvestment()
  const deleteInvestment = useDeleteInvestment()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState(null)

  function openAdd() {
    setEditing(null)
    setFormOpen(true)
  }

  function openEdit(investment) {
    setEditing(investment)
    setFormOpen(true)
  }

  async function handleSubmit(values) {
    if (editing) {
      await updateInvestment.mutateAsync({ id: editing.id, ...values })
    } else {
      await createInvestment.mutateAsync(values)
    }
  }

  return (
    <>
      <Modal open={open} onClose={onClose} className="modal wide">
        <div className="modal-body">
          <h2>Initial investments</h2>

          {investments.isPending && <p className="empty">Loading…</p>}
          {investments.isError && (
            <ErrorBanner message={investments.error.message} onRetry={() => investments.refetch()} />
          )}
          {deleteInvestment.isError && (
            <ErrorBanner message={deleteInvestment.error.message} onDismiss={deleteInvestment.reset} />
          )}
          {investments.isSuccess && (
            <InitialInvestmentTable
              investments={investments.data}
              onEdit={openEdit}
              onDelete={(id) => deleteInvestment.mutateAsync(id)}
            />
          )}

          <div className="card-actions">
            <button type="button" className="primary" onClick={openAdd}>
              Add Initial Investment
            </button>
            <button type="button" onClick={onClose}>
              Close
            </button>
          </div>
        </div>
      </Modal>

      <InitialInvestmentFormModal
        open={formOpen}
        editing={editing}
        catalog={catalog}
        onSubmit={handleSubmit}
        onClose={() => setFormOpen(false)}
      />
    </>
  )
}
