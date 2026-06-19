import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { SuiteConnectionsPanel } from './SuiteConnectionsPanel';

const service = {
  id: 'ts-1', sourceSystem: 'SCREENING', description: 'AML app', publicKeyPem: 'PEM',
  jitProvisioning: true, defaultRole: 'ANALYST', status: 'ACTIVE',
  createdAt: '2026-06-01T10:00:00Z', updatedAt: '2026-06-01T10:00:00Z',
};
const link = { id: 'lk-1', tenantId: 't-acme', sourceSystem: 'SCREENING', externalOrgRef: 'aumtech_1', createdAt: '2026-06-01T10:00:00Z' };
const tenant = { id: 't-acme', slug: 'acme', name: 'Acme FZE', jurisdictionCode: 'AE', schemaName: 'tenant_acme', status: 'ACTIVE', createdAt: '2026-06-01T10:00:00Z' };

function renderPanel() {
  setToken(makeToken({ roles: ['SUPER_ADMIN'] }));
  return renderWithProviders(<SuiteConnectionsPanel />);
}

describe('SuiteConnectionsPanel', () => {
  it('lists existing trusted services and company links (resolving the tenant name)', async () => {
    server.use(
      http.get('*/api/v1/admin/trusted-services', () => HttpResponse.json([service])),
      http.get('*/api/v1/admin/tenant-external-refs', () => HttpResponse.json([link])),
      http.get('*/api/v1/admin/tenants', () => HttpResponse.json([tenant])),
    );
    renderPanel();
    expect(await screen.findByText('aumtech_1')).toBeInTheDocument();
    expect(await screen.findByText('Acme FZE (acme)')).toBeInTheDocument();
  });

  it('revokes a trusted service via the Popconfirm', async () => {
    let revoked = false;
    server.use(
      http.get('*/api/v1/admin/trusted-services', () => HttpResponse.json([service])),
      http.get('*/api/v1/admin/tenant-external-refs', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/tenants', () => HttpResponse.json([tenant])),
      http.delete('*/api/v1/admin/trusted-services/:id', () => { revoked = true; return new HttpResponse(null, { status: 204 }); }),
    );
    renderPanel();
    await userEvent.click(await screen.findByRole('button', { name: 'Revoke' }));
    const confirms = await screen.findAllByRole('button', { name: 'Revoke' });
    await userEvent.click(confirms[confirms.length - 1]); // the Popconfirm OK
    await waitFor(() => expect(revoked).toBe(true));
  });

  it('removes a company link via the Popconfirm', async () => {
    let removed = false;
    server.use(
      http.get('*/api/v1/admin/trusted-services', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/tenant-external-refs', () => HttpResponse.json([link])),
      http.get('*/api/v1/admin/tenants', () => HttpResponse.json([tenant])),
      http.delete('*/api/v1/admin/tenant-external-refs/:id', () => { removed = true; return new HttpResponse(null, { status: 204 }); }),
    );
    renderPanel();
    await userEvent.click(await screen.findByRole('button', { name: 'Remove' }));
    const confirms = await screen.findAllByRole('button', { name: 'Remove' });
    await userEvent.click(confirms[confirms.length - 1]);
    await waitFor(() => expect(removed).toBe(true));
  });

  it('shows empty states when there are no services or links', async () => {
    server.use(
      http.get('*/api/v1/admin/trusted-services', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/tenant-external-refs', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/tenants', () => HttpResponse.json([])),
    );
    renderPanel();
    expect(await screen.findByText('No trusted services yet')).toBeInTheDocument();
    expect(await screen.findByText('No company links yet')).toBeInTheDocument();
  });
});
