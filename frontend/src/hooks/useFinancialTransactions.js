import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createFinancialTransaction,
  deleteFinancialTransaction,
  listFinancialTransactions,
  updateFinancialTransaction,
} from '../api/financialTransactionsApi.js'

const FINANCIAL_TRANSACTIONS_KEY = ['financialTransactions']

// The backend already returns these newest-first, so no client-side sort is needed. Unlike investment
// transactions, these never affect the portfolio, so nothing else needs invalidating alongside them.
export function useFinancialTransactions() {
  return useQuery({ queryKey: FINANCIAL_TRANSACTIONS_KEY, queryFn: listFinancialTransactions })
}

function useInvalidatingMutation(mutationFn) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: () => queryClient.invalidateQueries({ queryKey: FINANCIAL_TRANSACTIONS_KEY }),
  })
}

export const useCreateFinancialTransaction = () => useInvalidatingMutation(createFinancialTransaction)
export const useUpdateFinancialTransaction = () => useInvalidatingMutation(updateFinancialTransaction)
export const useDeleteFinancialTransaction = () => useInvalidatingMutation(deleteFinancialTransaction)
