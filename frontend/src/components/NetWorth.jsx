import { formatUsd, sumWorth } from '../utils/money.js'

/** The sum of the worth column of the investments table, so the two always agree. */
export default function NetWorth({ entries }) {
  const { total, unpriced } = sumWorth(entries)
  const negative = total.startsWith('-') && formatUsd(total).startsWith('-')

  return (
    <section className="card net-worth" aria-label="Net worth">
      <p className="net-worth-label">Net Worth</p>
      <p className={negative ? 'net-worth-value negative' : 'net-worth-value'}>{formatUsd(total)}</p>
      {unpriced > 0 && (
        <p className="net-worth-note">
          {unpriced === 1 ? '1 investment without a price is' : `${unpriced} investments without a price are`} not
          included
        </p>
      )}
    </section>
  )
}
