import { describe, expect, it } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { AdminPage } from './AdminPage';
import type { GoamlPersonView, TenantView, UserView } from '../../types';

function tenant(slug: string): TenantView {
  return {
    id: `t-${slug}`,
    slug,
    name: `${slug} FZE`,
    jurisdictionCode: 'AE',
    schemaName: `tenant_${slug}`,
    status: 'ACTIVE',
    createdAt: '2026-06-01T10:00:00Z',
  };
}

function user(email: string, roles: string[]): UserView {
  return {
    id: `u-${email}`,
    email,
    firstName: 'A',
    lastName: 'B',
    status: 'ACTIVE',
    roles,
    createdAt: '2026-06-01T10:00:00Z',
  };
}

function person(): GoamlPersonView {
  return {
    id: 'gp-1',
    firstName: 'Aisha',
    lastName: 'Khan',
    nationality: 'AE',
    active: true,
    updatedAt: '2026-06-09T10:00:00Z',
  };
}

function render(role: string) {
  setToken(makeToken({ roles: [role] }));
  return renderWithProviders(<AdminPage />, ['/admin']);
}

describe('AdminPage — SUPER_ADMIN', () => {
  it('lists tenants and provisions a new one', async () => {
    let created = false;
    server.use(
      http.get('*/api/v1/admin/tenants', () =>
        HttpResponse.json(created ? [tenant('acme'), tenant('beta')] : [tenant('acme')]),
      ),
      http.post('*/api/v1/admin/tenants', () => {
        created = true;
        return HttpResponse.json(tenant('beta'), { status: 201 });
      }),
    );
    render('SUPER_ADMIN');

    expect(await screen.findByText('acme')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /provision tenant/i }));
    await userEvent.type(screen.getByLabelText('Slug'), 'beta');
    await userEvent.type(screen.getByLabelText('Name'), 'Beta FZE');
    await userEvent.type(screen.getByLabelText('Admin email'), 'admin@beta.test');
    await userEvent.type(screen.getByLabelText('Admin password'), 'P@ssw0rd!');
    await userEvent.type(screen.getByLabelText('First name'), 'Be');
    await userEvent.type(screen.getByLabelText('Last name'), 'Ta');
    await userEvent.click(screen.getByRole('button', { name: 'Provision' }));

    expect(await screen.findByText('beta')).toBeInTheDocument();
  });
});

describe('AdminPage — TENANT_ADMIN', () => {
  it('lists users and shows "no config yet"', async () => {
    server.use(
      http.get('*/api/v1/admin/users', () => HttpResponse.json([user('mlro@t.test', ['MLRO'])])),
      http.get('*/api/v1/admin/goaml-persons', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/goaml-config', () => new HttpResponse(null, { status: 404 })),
    );
    render('TENANT_ADMIN');

    expect(await screen.findByText('mlro@t.test')).toBeInTheDocument();
    expect(await screen.findByText(/No configuration yet/)).toBeInTheDocument();
  });

  it('creates a user', async () => {
    let captured: Record<string, unknown> | null = null;
    server.use(
      http.get('*/api/v1/admin/users', () => HttpResponse.json([user('a@t.test', ['ANALYST'])])),
      http.post('*/api/v1/admin/users', async ({ request }) => {
        captured = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(user('new@t.test', ['MLRO']), { status: 201 });
      }),
      http.get('*/api/v1/admin/goaml-persons', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/goaml-config', () => new HttpResponse(null, { status: 404 })),
    );
    render('TENANT_ADMIN');

    await userEvent.click(await screen.findByRole('button', { name: /add user/i }));
    // Scope to the Add-user dialog — the goAML reporting-person form on the same page shares these labels.
    const dialog = within(await screen.findByRole('dialog'));
    await userEvent.type(dialog.getByLabelText('Email'), 'new@t.test');
    await userEvent.type(dialog.getByLabelText('Password'), 'P@ssw0rd!');
    await userEvent.type(dialog.getByLabelText('First name'), 'Ne');
    await userEvent.type(dialog.getByLabelText('Last name'), 'W');
    // open the role Select (mouseDown opens AntD selects) and pick MLRO (options render in a body portal)
    fireEvent.mouseDown(dialog.getByLabelText('Role'));
    await userEvent.click(await screen.findByText('MLRO', { selector: '.ant-select-item-option-content' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(captured).not.toBeNull());
    expect(captured).toMatchObject({ email: 'new@t.test', role: 'MLRO' });
  });

  it('edits, disables, and deletes a user', async () => {
    let putBody: Record<string, unknown> | null = null;
    let deleted = false;
    server.use(
      http.get('*/api/v1/admin/users', () => HttpResponse.json(deleted ? [] : [user('a@t.test', ['ANALYST'])])),
      http.put('*/api/v1/admin/users/:id', async ({ request }) => {
        putBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...user('a@t.test', [String(putBody.role)]), status: String(putBody.status) });
      }),
      http.delete('*/api/v1/admin/users/:id', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.get('*/api/v1/admin/goaml-persons', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/goaml-config', () => new HttpResponse(null, { status: 404 })),
    );
    render('TENANT_ADMIN');

    // Quick disable → PUT carries the current role + DISABLED status.
    await userEvent.click(await screen.findByRole('button', { name: 'Disable' }));
    await waitFor(() => expect(putBody).not.toBeNull());
    expect(putBody).toMatchObject({ status: 'DISABLED', role: 'ANALYST' });

    // Delete (row button opens a Popconfirm whose OK is also "Delete") → row disappears.
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
    const confirmButtons = await screen.findAllByRole('button', { name: 'Delete' });
    await userEvent.click(confirmButtons[confirmButtons.length - 1]);
    await waitFor(() => expect(deleted).toBe(true));
    await waitFor(() => expect(screen.queryByText('a@t.test')).not.toBeInTheDocument());
  });

  it('saves the goAML config', async () => {
    server.use(
      http.get('*/api/v1/admin/users', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/goaml-persons', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/goaml-config', () => new HttpResponse(null, { status: 404 })),
      http.put('*/api/v1/admin/goaml-config', () =>
        HttpResponse.json({
          tenantId: 't-1',
          jurisdictionCode: 'AE',
          rentityId: 3177,
          baseUrl: 'https://goaml.test',
          secretsPath: 'goaml/t/creds',
          authMode: 'TOKEN',
          updatedAt: '2026-06-07T00:00:00Z',
        }),
      ),
    );
    render('TENANT_ADMIN');

    await userEvent.type(await screen.findByLabelText('Entity ID (rentityId)'), '3177');
    await userEvent.type(screen.getByLabelText('Base URL'), 'https://goaml.test');
    await userEvent.type(screen.getByLabelText('Secrets path'), 'goaml/t/creds');
    await userEvent.click(screen.getByRole('button', { name: /save configuration/i }));

    expect(await screen.findByText('Configuration saved')).toBeInTheDocument();
  });

  it('creates the active reporting person via the inline form', async () => {
    let captured: Record<string, unknown> | null = null;
    let created = false;
    server.use(
      http.get('*/api/v1/admin/users', () => HttpResponse.json([])),
      http.get('*/api/v1/admin/goaml-config', () => new HttpResponse(null, { status: 404 })),
      http.get('*/api/v1/admin/goaml-persons', () => HttpResponse.json(created ? [person()] : [])),
      http.post('*/api/v1/admin/goaml-persons', async ({ request }) => {
        captured = (await request.json()) as Record<string, unknown>;
        created = true;
        return HttpResponse.json(person(), { status: 201 });
      }),
    );
    render('TENANT_ADMIN');

    // No active person yet → the single inline form is shown; fill it and create (sent as the active default).
    await userEvent.type(await screen.findByLabelText('First name'), 'Aisha');
    await userEvent.type(screen.getByLabelText('Last name'), 'Khan');
    await userEvent.click(screen.getByRole('button', { name: /create reporting person/i }));

    await waitFor(() => expect(captured).not.toBeNull());
    expect(captured).toMatchObject({ firstName: 'Aisha', lastName: 'Khan', active: true });
    // after the refetch the form re-keys to the active person and shows the Active (default) tag
    expect(await screen.findByText('Active (default)')).toBeInTheDocument();
  });
});
