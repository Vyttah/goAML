import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntApp } from 'antd';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import type * as RouterDom from 'react-router-dom';
import { ScreeningSubjectsPage } from './ScreeningSubjectsPage';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof RouterDom>();
  return { ...actual, useNavigate: () => navigateMock };
});

function render() {
  setToken(makeToken({ roles: ['MLRO'] }));
  return renderWithProviders(
    <AntApp>
      <ScreeningSubjectsPage />
    </AntApp>,
    ['/screening'],
  );
}

describe('ScreeningSubjectsPage', () => {
  it('lists screened subjects and seeds a DPMSR draft from one', async () => {
    let seededBody: Record<string, unknown> | null = null;
    server.use(
      http.get('*/api/v1/screening/subjects', () =>
        HttpResponse.json([
          {
            subjectRef: 'SCR-501-LEG-1',
            subjectType: 'LEGAL',
            displayName: 'Risky Trading FZE',
            riskFlag: true,
            parties: [],
            sanctionsContext: 'Sanctions screening risk flagged — 1 hit(s)',
          },
        ]),
      ),
      http.post('*/api/v1/screening/subjects/SCR-501-LEG-1/seed-report', async ({ request }) => {
        seededBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ reportId: 'rpt-9', status: 'VALID', validationMessages: [] });
      }),
    );

    render();

    // the subject row renders with its risk flag
    expect(await screen.findByText('Risky Trading FZE')).toBeInTheDocument();
    expect(screen.getByText('Risk')).toBeInTheDocument();

    // open the seed modal
    await userEvent.click(screen.getByRole('button', { name: 'Seed report' }));
    expect(await screen.findByText('Seed report — Risky Trading FZE')).toBeInTheDocument();
    expect(screen.getByText(/Sanctions screening risk flagged/)).toBeInTheDocument();

    // fill the report-completing fields
    await userEvent.type(screen.getByLabelText('Report reference'), 'SCR-RPT-1');
    await userEvent.type(screen.getByLabelText('MLRO first name'), 'Sara');
    await userEvent.type(screen.getByLabelText('MLRO last name'), 'Khan');
    await userEvent.type(screen.getByLabelText('Goods item type'), 'GOLD');
    await userEvent.type(screen.getByLabelText('Estimated value (AED)'), '90000');

    // submit → seeds via the API → navigates to the created report
    await userEvent.click(screen.getByRole('button', { name: 'Create draft' }));

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/reports/rpt-9'));
    expect(seededBody).toMatchObject({
      entityReference: 'SCR-RPT-1',
      reportingPerson: { firstName: 'Sara', lastName: 'Khan' },
      goods: [{ itemType: 'GOLD', estimatedValue: 90000, currencyCode: 'AED' }],
    });
  });
});
