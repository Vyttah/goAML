import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { makeToken } from '../../test/util';
import { setToken } from '../../auth/tokenStore';
import { DashboardPage } from './DashboardPage';
import type { ReportView } from '../../types';

const REPORTS: ReportView[] = [
  {
    id: 'r-1',
    entityReference: 'DPMSR-0001',
    reportCode: 'DPMSR',
    status: 'VALID',
    rentityId: 3177,
    createdAt: '2026-06-01T10:00:00Z',
  },
  {
    id: 'r-2',
    entityReference: 'DPMSR-0002',
    reportCode: 'DPMSR',
    status: 'REJECTED',
    rentityId: 3177,
    createdAt: '2026-06-02T10:00:00Z',
  },
];

function listReturns(reports: ReportView[]) {
  server.use(http.get('*/api/v1/reports', () => HttpResponse.json(reports)));
}

function routes() {
  return (
    <Routes>
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/reports/new" element={<div>NEW REPORT PAGE</div>} />
      <Route path="/reports/:id" element={<div>REPORT DETAIL PAGE</div>} />
    </Routes>
  );
}

function renderDashboard(role = 'ANALYST') {
  setToken(makeToken({ roles: [role] }));
  return renderWithProviders(routes(), ['/dashboard']);
}

describe('DashboardPage', () => {
  it('renders reports from the API with status chips', async () => {
    listReturns(REPORTS);
    renderDashboard();

    expect(await screen.findByText('DPMSR-0001')).toBeInTheDocument();
    expect(screen.getByText('DPMSR-0002')).toBeInTheDocument();
    expect(screen.getByText('VALID')).toBeInTheDocument();
    expect(screen.getByText('REJECTED')).toBeInTheDocument();
  });

  it('filters by reference search', async () => {
    listReturns(REPORTS);
    renderDashboard();
    await screen.findByText('DPMSR-0001');

    await userEvent.type(screen.getByLabelText('Search reports'), '0002');

    expect(screen.queryByText('DPMSR-0001')).not.toBeInTheDocument();
    expect(screen.getByText('DPMSR-0002')).toBeInTheDocument();
  });

  it('navigates to the detail page on row click', async () => {
    listReturns(REPORTS);
    renderDashboard();

    await userEvent.click(await screen.findByText('DPMSR-0001'));
    expect(await screen.findByText('REPORT DETAIL PAGE')).toBeInTheDocument();
  });

  it('shows an error state when the list fails', async () => {
    server.use(http.get('*/api/v1/reports', () => new HttpResponse(null, { status: 500 })));
    renderDashboard();

    expect(await screen.findByText("Couldn't load reports")).toBeInTheDocument();
  });

  it('lets an author start a new report', async () => {
    listReturns(REPORTS);
    renderDashboard('MLRO');

    await userEvent.click(await screen.findByRole('button', { name: /new report/i }));
    expect(await screen.findByText('NEW REPORT PAGE')).toBeInTheDocument();
  });

  it('hides "New report" from a tenant admin (read-only role here)', async () => {
    listReturns(REPORTS);
    renderDashboard('TENANT_ADMIN');
    await screen.findByText('DPMSR-0001');

    expect(screen.queryByRole('button', { name: /new report/i })).not.toBeInTheDocument();
  });
});
