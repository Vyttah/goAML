import { describe, expect, it, vi } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { ReportDetailPage } from './ReportDetailPage';
import type { AttachmentView, ReportView } from '../../types';

const ID = 'r-1';

function report(overrides: Partial<ReportView> = {}): ReportView {
  return {
    id: ID,
    entityReference: 'DPMSR-1',
    reportCode: 'DPMSR',
    status: 'VALID',
    rentityId: 3177,
    createdAt: '2026-06-01T10:00:00Z',
    ...overrides,
  };
}

function stubReport(status = 'VALID') {
  server.use(http.get(`*/api/v1/reports/${ID}`, () => HttpResponse.json(report({ status }))));
}

function stubAttachments(items: AttachmentView[] = []) {
  server.use(
    http.get(`*/api/v1/reports/${ID}/attachments`, () => HttpResponse.json(items)),
  );
}

function routes() {
  return (
    <Routes>
      <Route path="/reports/:id" element={<ReportDetailPage />} />
      <Route path="/dashboard" element={<div>DASHBOARD</div>} />
    </Routes>
  );
}

function renderDetail(role = 'MLRO') {
  setToken(makeToken({ roles: [role] }));
  return renderWithProviders(routes(), [`/reports/${ID}`]);
}

describe('ReportDetailPage', () => {
  it('renders the report summary', async () => {
    stubReport('VALID');
    stubAttachments();
    renderDetail();

    expect(await screen.findByText('DPMSR-1')).toBeInTheDocument();
    expect(screen.getByText('VALID')).toBeInTheDocument();
    expect(screen.getByText('No attachments')).toBeInTheDocument();
  });

  it('lets an MLRO submit a VALID report and reflects the new status', async () => {
    let submitted = false;
    server.use(
      http.get(`*/api/v1/reports/${ID}`, () =>
        HttpResponse.json(report({ status: submitted ? 'SUBMITTED' : 'VALID' })),
      ),
      http.post(`*/api/v1/reports/${ID}/submit`, () => {
        submitted = true;
        return HttpResponse.json({ submissionId: 's-1', reportKey: 'RK-1', status: 'SUBMITTED' });
      }),
    );
    stubAttachments();
    renderDetail('MLRO');

    await userEvent.click(await screen.findByRole('button', { name: /submit to fiu/i }));
    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));

    expect(await screen.findByText('SUBMITTED')).toBeInTheDocument();
    // submitted report now offers a status check
    expect(screen.getByRole('button', { name: /check fiu status/i })).toBeInTheDocument();
  });

  it('hides submit from a non-MLRO', async () => {
    stubReport('VALID');
    stubAttachments();
    renderDetail('ANALYST');

    await screen.findByText('DPMSR-1');
    expect(screen.queryByRole('button', { name: /submit to fiu/i })).not.toBeInTheDocument();
  });

  it('shows a no-actions hint in Lifecycle for a non-MLRO on an unsubmitted report', async () => {
    stubReport('VALID');
    stubAttachments();
    renderDetail('TENANT_ADMIN');

    await screen.findByText('DPMSR-1');
    expect(screen.getByText(/No actions available here/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /submit to fiu/i })).not.toBeInTheDocument();
  });

  it('disables submit for a non-VALID report and explains why', async () => {
    stubReport('INVALID');
    stubAttachments();
    renderDetail('MLRO');

    await screen.findByText('DPMSR-1');
    expect(screen.getByRole('button', { name: /submit to fiu/i })).toBeDisabled();
    expect(screen.getByText(/Only a/)).toBeInTheDocument();
  });

  it('checks FIU status on demand', async () => {
    stubReport('SUBMITTED');
    stubAttachments();
    server.use(
      http.get(`*/api/v1/reports/${ID}/status`, () =>
        HttpResponse.json({ reportKey: 'RK-9', status: 'ACCEPTED', errors: null }),
      ),
    );
    renderDetail('MLRO');

    await userEvent.click(await screen.findByRole('button', { name: /check fiu status/i }));

    expect(await screen.findByText('RK-9')).toBeInTheDocument();
    expect(screen.getByText('ACCEPTED')).toBeInTheDocument();
  });

  it('views the generated goAML XML in a modal', async () => {
    stubReport('VALID');
    stubAttachments();
    server.use(
      http.get(`*/api/v1/reports/${ID}/xml`, () =>
        HttpResponse.text('<report><report_code>DPMSR</report_code></report>', {
          headers: { 'Content-Type': 'application/xml' },
        }),
      ),
    );
    renderDetail('ANALYST');

    await userEvent.click(await screen.findByRole('button', { name: /view xml/i }));

    expect(await screen.findByLabelText('report-xml')).toHaveTextContent('<report_code>DPMSR</report_code>');
    expect(screen.getByRole('button', { name: /download/i })).toBeEnabled();
  });

  it('downloads an attachment', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    const item: AttachmentView = {
      id: 'att-1',
      reportId: ID,
      filename: 'invoice.pdf',
      contentType: 'application/pdf',
      sizeBytes: 2048,
      createdAt: '2026-06-01T10:00:00Z',
    };
    stubReport('VALID');
    stubAttachments([item]);
    server.use(
      http.get(`*/api/v1/reports/${ID}/attachments/att-1/content`, () =>
        HttpResponse.arrayBuffer(new TextEncoder().encode('PDF-BYTES').buffer, {
          headers: { 'Content-Type': 'application/pdf' },
        }),
      ),
    );
    renderDetail('TENANT_ADMIN');

    await userEvent.click(await screen.findByRole('button', { name: /download attachment/i }));

    await waitFor(() => expect(clickSpy).toHaveBeenCalled());
    clickSpy.mockRestore();
  });

  it('lists attachments and removes one', async () => {
    let items: AttachmentView[] = [
      {
        id: 'att-1',
        reportId: ID,
        filename: 'invoice.pdf',
        contentType: 'application/pdf',
        sizeBytes: 2048,
        createdAt: '2026-06-01T10:00:00Z',
      },
    ];
    stubReport('VALID');
    server.use(
      http.get(`*/api/v1/reports/${ID}/attachments`, () => HttpResponse.json(items)),
      http.delete(`*/api/v1/reports/${ID}/attachments/att-1`, () => {
        items = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderDetail('MLRO');

    expect(await screen.findByText('invoice.pdf')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /remove attachment/i }));
    await userEvent.click(await screen.findByRole('button', { name: 'Remove' }));

    expect(await screen.findByText('No attachments')).toBeInTheDocument();
  });
});
