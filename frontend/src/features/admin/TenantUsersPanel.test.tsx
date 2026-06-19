import { describe, it, expect } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { TenantUsersPanel } from './TenantUsersPanel';

const tenant = { id: 't-acme', slug: 'acme', name: 'Acme FZE', jurisdictionCode: 'AE', schemaName: 'tenant_acme', status: 'ACTIVE', createdAt: '2026-06-01T10:00:00Z' };
const aUser = { id: 'u-1', email: 'mlro@acme.test', firstName: 'Mel', lastName: 'Roe', status: 'ACTIVE', roles: ['MLRO'], createdAt: '2026-06-01T10:00:00Z' };

function renderPanel() {
  setToken(makeToken({ roles: ['SUPER_ADMIN'] }));
  return renderWithProviders(<TenantUsersPanel />);
}

async function pickTenant() {
  fireEvent.mouseDown(await screen.findByRole('combobox'));
  await userEvent.click(await screen.findByText('Acme FZE (acme)', { selector: '.ant-select-item-option-content' }));
}

describe('TenantUsersPanel', () => {
  it('prompts to pick a tenant first, with Add user disabled', async () => {
    server.use(http.get('*/api/v1/admin/tenants', () => HttpResponse.json([tenant])));
    renderPanel();
    expect(await screen.findByText(/Pick a tenant above/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add user/i })).toBeDisabled();
  });

  it('loads a tenant’s users and creates a new one', async () => {
    let body: Record<string, unknown> | null = null;
    server.use(
      http.get('*/api/v1/admin/tenants', () => HttpResponse.json([tenant])),
      http.get('*/api/v1/admin/tenants/t-acme/users', () => HttpResponse.json([aUser])),
      http.post('*/api/v1/admin/tenants/t-acme/users', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(aUser, { status: 201 });
      }),
    );
    renderPanel();
    await pickTenant();
    expect(await screen.findByText('mlro@acme.test')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /add user/i }));
    const dialog = within(await screen.findByRole('dialog'));
    await userEvent.type(dialog.getByLabelText('Email'), 'new@acme.test');
    await userEvent.type(dialog.getByLabelText('Password'), 'P@ssw0rd!');
    await userEvent.type(dialog.getByLabelText('First name'), 'Ne');
    await userEvent.type(dialog.getByLabelText('Last name'), 'W');
    fireEvent.mouseDown(dialog.getByLabelText('Role'));
    await userEvent.click(await screen.findByText('MLRO', { selector: '.ant-select-item-option-content' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(body).not.toBeNull());
    expect(body).toMatchObject({ email: 'new@acme.test', role: 'MLRO' });
  });

  it('deletes a tenant user via the Popconfirm', async () => {
    let deleted = false;
    server.use(
      http.get('*/api/v1/admin/tenants', () => HttpResponse.json([tenant])),
      http.get('*/api/v1/admin/tenants/t-acme/users', () => HttpResponse.json([aUser])),
      http.delete('*/api/v1/admin/tenants/t-acme/users/:id', () => { deleted = true; return new HttpResponse(null, { status: 204 }); }),
    );
    renderPanel();
    await pickTenant();
    await screen.findByText('mlro@acme.test');

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
    const confirms = await screen.findAllByRole('button', { name: 'Delete' });
    await userEvent.click(confirms[confirms.length - 1]);
    await waitFor(() => expect(deleted).toBe(true));
  });
});
