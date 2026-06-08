import { useMutation, useQuery } from '@tanstack/react-query';
import {
  listScreeningSubjects,
  seedReportFromSubject,
  type ScreeningSeedRequest,
} from '../../api/screening';

/** Screened subjects in the caller's tenant (the AML screening software's pushes). */
export function useScreeningSubjects() {
  return useQuery({ queryKey: ['screening', 'subjects'], queryFn: listScreeningSubjects });
}

/** Seed a DPMSR draft from a screened subject. */
export function useSeedReport() {
  return useMutation({
    mutationFn: (vars: { subjectRef: string; body: ScreeningSeedRequest }) =>
      seedReportFromSubject(vars.subjectRef, vars.body),
  });
}
