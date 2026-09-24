import { useEffect, useRef } from 'react'

/**
 * A modal dialog on the native <dialog> element: real browsers get the focus trap, the backdrop and
 * Escape-to-close for free. Several can be open at once (the newest is on top), which is how a modal
 * can open another one.
 *
 * The <dialog> stays mounted so its ref and the native show/close API keep working, but `children` are
 * rendered only while `open`. A closed modal therefore has no fields in the page, and each opening
 * starts from fresh state without any effect that resets it.
 *
 * `onClose` is also called when the dialog closes itself (the Escape key); it may then run a second time
 * after the parent already set `open` to false, which is harmless.
 */
export default function Modal({ open, onClose, className = 'modal', children }) {
  const dialogRef = useRef(null)

  useEffect(() => {
    const dialog = dialogRef.current
    // jsdom (used by the component tests) has no showModal()/close(), only the plain `open` attribute;
    // real browsers get the full modal behaviour, jsdom a plain toggle, which is enough to render and
    // interact with the content.
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') dialog.showModal()
      else dialog.setAttribute('open', '')
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close()
      else dialog.removeAttribute('open')
    }
  }, [open])

  useEffect(() => {
    const dialog = dialogRef.current
    dialog.addEventListener('close', onClose)
    return () => dialog.removeEventListener('close', onClose)
  }, [onClose])

  return (
    <dialog ref={dialogRef} className={className}>
      {open ? children : null}
    </dialog>
  )
}
