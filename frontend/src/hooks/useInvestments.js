import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createInvestment,
  deleteInvestment,
  listInvestments,
  updateInvestment,
} from '../api/investmentsApi.js'

const INVESTMENTS_KEY = ['investments']

// The backend returns rows in no particular order, so a row would jump around after an edit
const byName = (a, b) => a.name.localeCompare(b.name) || a.id.localeCompare(b.id)

export function useInvestments() {
  return useQuery({
    queryKey: INVESTMENTS_KEY,
    queryFn: listInvestments,
    select: (investments) => [...investments].sort(byName),
  })
}

// Refetching in onSettled (and returning the promise) means mutateAsync resolves once the list is fresh,
// and the list also self-corrects after a failed mutation (e.g. deleting a row that is already gone).
function useInvalidatingMutation(mutationFn) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: () => queryClient.invalidateQueries({ queryKey: INVESTMENTS_KEY }),
  })
}

export const useCreateInvestment = () => useInvalidatingMutation(createInvestment)
export const useUpdateInvestment = () => useInvalidatingMutation(updateInvestment)
export const useDeleteInvestment = () => useInvalidatingMutation(deleteInvestment)
