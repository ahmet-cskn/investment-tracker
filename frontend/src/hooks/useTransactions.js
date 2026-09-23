import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createTransaction,
  deleteTransaction,
  listTransactions,
  updateTransaction,
} from '../api/transactionsApi.js'

const TRANSACTIONS_KEY = ['transactions']

// Unlike investments, the backend already returns transactions newest-first, so no client-side sort is needed
export function useTransactions() {
  return useQuery({ queryKey: TRANSACTIONS_KEY, queryFn: listTransactions })
}

function useInvalidatingMutation(mutationFn) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: () => queryClient.invalidateQueries({ queryKey: TRANSACTIONS_KEY }),
  })
}

export const useCreateTransaction = () => useInvalidatingMutation(createTransaction)
export const useUpdateTransaction = () => useInvalidatingMutation(updateTransaction)
export const useDeleteTransaction = () => useInvalidatingMutation(deleteTransaction)
