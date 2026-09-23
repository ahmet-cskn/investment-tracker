import { formatAmount } from '../utils/amount.js'

/**
 * Read-only: each row is derived by the backend (initial amount plus the sum of the transaction
 * changes), so there is nothing to edit or delete here.
 */
export default function PortfolioTable({ entries }) {
  if (entries.length === 0) {
    return <p className="empty">No investments yet. Add an initial investment above or a transaction below.</p>
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>Type</th>
          <th className="numeric">Amount</th>
          <th className="numeric">Worth</th>
        </tr>
      </thead>
      <tbody>
        {entries.map(({ name, investmentType, amount, worth }) => (
          <tr key={name}>
            <td>{name}</td>
            <td>{investmentType ?? '—'}</td>
            <td className="numeric">{formatAmount(amount)}</td>
            <td className="numeric">{worth != null ? formatAmount(worth) : '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
