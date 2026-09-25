import { useQuery } from '@tanstack/react-query'
import { listPortfolio } from '../api/portfolioApi.js'

export const PORTFOLIO_KEY = ['portfolio']

// The backend already returns the portfolio sorted by name. It is derived from the initial investments
// and the transactions, so the mutation hooks for both invalidate this key when they settle.
export function usePortfolio() {
  return useQuery({ queryKey: PORTFOLIO_KEY, queryFn: listPortfolio })
}
