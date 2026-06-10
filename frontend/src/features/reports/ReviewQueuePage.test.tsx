import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { ReviewQueuePage } from './ReviewQueuePage';
import type { ReportView } from '../../types';

function pending(id: string, ref: string): ReportView {
  return {
    id,
    entityReference: ref,
    reportCode: 'DPMSR',
    status: 'PENDING_REVIEW',
    rentityId: 3177,
    createdAt: '2026-06-01T10:00:00Z',
  };
}

function routes() {
  return (
    <Routes>
      <Route path="/reports/review" element={<ReviewQueuePage />} />
      <Route path="/reports/:id" element={<div>REPORT DETAIL</div>} />
    </Routes>
  );
}

function renderQueue(role = 'MLRO') {
  setToken(makeToken({ roles: [role] }));
  return renderWithProviders(routes(), ['/reports/review']);
}

describe('ReviewQueuePage', () => {
  it('lists reports awaiting review', async () => {
    server.use(
      http.get('*/api/v1/reports/review-queue', () =>
        HttpResponse.json([pending('r-1', 'DPMSR-A'), pending('r-2', 'DPMSR-B')]),
      ),
    );
    renderQueue();

    expect(await screen.findByText('DPMSR-A')).toBeInTheDocument();
    expect(screen.getByText('DPMSR-B')).toBeInTheDocument();
  });

  it('lets an MLRO approve a report and removes it from the queue', async () => {
    let approved = false;
    server.use(
      http.get('*/api/v1/reports/review-queue', () =>
        HttpResponse.json(approved ? [] : [pending('r-1', 'DPMSR-A')]),
      ),
      http.post('*/api/v1/reports/r-1/approve', () => {
        approved = true;
        return HttpResponse.json({
          reportId: 'r-1',
          status: 'APPROVED',
          reviewedBy: 'u-1',
          reviewedAt: '2026-06-02T10:00:00Z',
          remark: null,
        });
      }),
    );
    renderQueue();

    expect(await screen.findByText('DPMSR-A')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /approve/i }));
    // confirm in the popconfirm
    await userEvent.click(await screen.findByRole('button', { name: 'Yes, approve' }));

    await waitFor(() =>
      expect(screen.getByText('Nothing awaiting review')).toBeInTheDocument(),
    );
  });

  it('requires a remark to reject', async () => {
    let rejectedRemark: string | null = null;
    server.use(
      http.get('*/api/v1/reports/review-queue', () =>
        HttpResponse.json(rejectedRemark ? [] : [pending('r-1', 'DPMSR-A')]),
      ),
      http.post('*/api/v1/reports/r-1/reject', async ({ request }) => {
        const body = (await request.json()) as { remark: string };
        rejectedRemark = body.remark;
        return HttpResponse.json({
          reportId: 'r-1',
          status: 'VALID',
          reviewedBy: 'u-1',
          reviewedAt: '2026-06-02T10:00:00Z',
          remark: body.remark,
        });
      }),
    );
    renderQueue();

    await screen.findByText('DPMSR-A');
    await userEvent.click(screen.getByRole('button', { name: /reject/i }));

    // OK is disabled until a reason is typed
    const okButton = await screen.findByRole('button', { name: 'Reject report' });
    expect(okButton).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Rejection reason'), 'missing party id');
    expect(okButton).toBeEnabled();
    await userEvent.click(okButton);

    await waitFor(() => expect(rejectedRemark).toBe('missing party id'));
  });

  it('shows the queue to a TENANT_ADMIN without approve/reject actions', async () => {
    server.use(
      http.get('*/api/v1/reports/review-queue', () =>
        HttpResponse.json([pending('r-1', 'DPMSR-A')]),
      ),
    );
    renderQueue('TENANT_ADMIN');

    expect(await screen.findByText('DPMSR-A')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /approve/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /reject/i })).not.toBeInTheDocument();
  });
});
