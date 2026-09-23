import { useQuery } from '@tanstack/react-query'
import { listCatalog } from '../api/catalogApi.js'

// The catalog is fixed reference data (seeded once by a database migration), so it is treated as
// never stale: no refetch on window focus or remount, only on demand via the query's own refetch().
export function useCatalog() {
  return useQuery({ queryKey: ['catalog'], queryFn: listCatalog, staleTime: Infinity })
}
