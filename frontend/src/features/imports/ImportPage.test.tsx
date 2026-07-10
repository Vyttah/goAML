import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { ImportPage } from './ImportPage';
import type { ImportJobView } from '../../types';

const JOB: ImportJobView = {
  id: 'job-1',
  sourceType: 'DPMSR_CSV',
  filename: 'data.csv',
  status: 'PARTIAL',
  totalRows: 2,
  succeeded: 1,
  failed: 1,
  results: [
    { row: 1, entityReference: 'REF-1', status: 'VALID', reportId: 'rep-1', errors: [] },
    { row: 2, entityReference: null, status: 'FAILED', reportId: null, errors: ['Missing reference'] },
  ],
  createdAt: '2026-06-01T10:00:00Z',
};

function routes() {
  return (
    <Routes>
      <Route path="/imports" element={<ImportPage />} />
      <Route path="/reports/:id" element={<div>REPORT DETAIL PAGE</div>} />
    </Routes>
  );
}

function renderImports(role = 'MLRO') {
  setToken(makeToken({ roles: [role] }));
  return renderWithProviders(routes(), ['/imports']);
}

function fileInput(container: HTMLElement): HTMLInputElement {
  const input = container.querySelector('input[type="file"]');
  if (!input) throw new Error('no file input');
  return input as HTMLInputElement;
}

describe('ImportPage', () => {
  it('uploads a CSV and renders the per-row results', async () => {
    server.use(
      http.get('*/api/v1/imports', () => HttpResponse.json([])),
      http.post('*/api/v1/imports/csv', () => HttpResponse.json(JOB, { status: 201 })),
    );
    const { container } = renderImports('MLRO');

    const file = new File(['a,b\n1,2'], 'data.csv', { type: 'text/csv' });
    await userEvent.upload(fileInput(container), file);

    expect(await screen.findByText('data.csv')).toBeInTheDocument();
    expect(screen.getByText(/1 succeeded, 1 failed of 2 rows/)).toBeInTheDocument();
    expect(screen.getByText('REF-1')).toBeInTheDocument();
    expect(screen.getByText('VALID')).toBeInTheDocument();
    expect(screen.getByText('Missing reference')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'open' })).toBeInTheDocument();
  });

  it('shows an error when the whole file is rejected', async () => {
    server.use(
      http.get('*/api/v1/imports', () => HttpResponse.json([])),
      http.post('*/api/v1/imports/csv', () =>
        HttpResponse.json({ status: 400, message: 'CSV header missing columns' }, { status: 400 }),
      ),
    );
    const { container } = renderImports('MLRO');

    const file = new File(['x'], 'bad.csv', { type: 'text/csv' });
    await userEvent.upload(fileInput(container), file);

    expect(await screen.findByText('CSV header missing columns')).toBeInTheDocument();
  });

  it('renders import history', async () => {
    server.use(http.get('*/api/v1/imports', () => HttpResponse.json([JOB])));
    renderImports('TENANT_ADMIN');

    expect(await screen.findByText('data.csv')).toBeInTheDocument();
    expect(screen.getByText('PARTIAL')).toBeInTheDocument();
  });

  it('shows the uploader to a tenant admin (has analyst/MLRO import rights)', async () => {
    server.use(http.get('*/api/v1/imports', () => HttpResponse.json([])));
    const { container } = renderImports('TENANT_ADMIN');

    await screen.findByText('Import history');
    expect(container.querySelector('input[type="file"]')).not.toBeNull();
  });
});
