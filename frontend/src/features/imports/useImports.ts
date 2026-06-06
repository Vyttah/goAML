import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { importCsv, importXml, listImports } from '../../api/imports';

export const importsQueryKey = ['imports'] as const;

/** Past import jobs. */
export function useImports() {
  return useQuery({ queryKey: importsQueryKey, queryFn: listImports });
}

/** Upload an import file (xml/csv); refreshes the history on success. */
export function useImport(format: 'xml' | 'csv') {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => (format === 'xml' ? importXml(file) : importCsv(file)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: importsQueryKey }),
  });
}
