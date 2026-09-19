export default function ErrorBanner({ message, onRetry, onDismiss }) {
  return (
    <div className="banner" role="alert">
      <span>{message}</span>
      <span className="banner-actions">
        {onRetry && (
          <button type="button" onClick={onRetry}>
            Retry
          </button>
        )}
        {onDismiss && (
          <button type="button" onClick={onDismiss}>
            Dismiss
          </button>
        )}
      </span>
    </div>
  )
}
