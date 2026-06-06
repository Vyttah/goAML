import { apiClient } from './client';
import { API_PREFIX } from '../lib/config';
import type { ImportJobView } from '../types';

const BASE = `${API_PREFIX}/imports`;

async function upload(path: string, file: File): Promise<ImportJobView> {
  const form = new FormData();
  form.append('file', file);
  // Clear the JSON default so axios sets multipart/form-data with the correct boundary.
  const { data } = await apiClient.post<ImportJobView>(`${BASE}/${path}`, form, {
    headers: { 'Content-Type': undefined },
  });
  return data;
}

/** Import a goAML XML file → an import job with per-row results. */
export function importXml(file: File): Promise<ImportJobView> {
  return upload('xml', file);
}

/** Import a DPMSR CSV file → an import job with per-row results. */
export function importCsv(file: File): Promise<ImportJobView> {
  return upload('csv', file);
}

/** List past import jobs (newest-first). */
export async function listImports(): Promise<ImportJobView[]> {
  const { data } = await apiClient.get<ImportJobView[]>(BASE);
  return data;
}

/** Fetch a single import job. */
export async function getImport(id: string): Promise<ImportJobView> {
  const { data } = await apiClient.get<ImportJobView>(`${BASE}/${id}`);
  return data;
}
