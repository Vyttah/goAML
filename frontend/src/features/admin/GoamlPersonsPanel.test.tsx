import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { GoamlPersonsPanel } from './GoamlPersonsPanel';

const active = { id: 'gp-1', firstName: 'Aisha', lastName: 'Khan', nationality: 'AE', active: true, updatedAt: '2026-06-09T10:00:00Z' };
const other = { id: 'gp-2', firstName: 'Omar', lastName: 'Saeed', nationality: 'AE', active: false, updatedAt: '2026-06-08T10:00:00Z' };

function renderPanel() {
  setToken(makeToken({ roles: ['TENANT_ADMIN'] }));
  return renderWithProviders(<GoamlPersonsPanel />);
}

describe('GoamlPersonsPanel', () => {
  // The Nationality field is a dropdown backed by the countries lookup (CodeSelect).
  beforeEach(() => {
    server.use(
      http.get('*/api/v1/lookups/ae/countries', () =>
        HttpResponse.json({
          jurisdiction: 'ae',
          set: 'countries',
          codes: ['AE', 'US'],
          entries: [
            { code: 'AE', label: 'United Arab Emirates' },
            { code: 'US', label: 'United States' },
          ],
        }),
      ),
    );
  });

  it('pre-fills the active person and Saves an UPDATE (not a create)', async () => {
    let putBody: Record<string, unknown> | null = null;
    let method: string | null = null;
    server.use(
      http.get('*/api/v1/admin/goaml-persons', () => HttpResponse.json([active])),
      http.put('*/api/v1/admin/goaml-persons/:id', async ({ request }) => {
        method = 'PUT';
        putBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(active);
      }),
    );
    renderPanel();

    // the form is keyed to the active person and pre-filled; the button reads "Save", not "Create"
    expect(await screen.findByDisplayValue('Aisha')).toBeInTheDocument();
    expect(screen.getByText('Active (default)')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(method).toBe('PUT'));
    expect(putBody).toMatchObject({ firstName: 'Aisha', active: true });
  });

  it('activates an additional (inactive) person', async () => {
    let putId: string | undefined;
    server.use(
      http.get('*/api/v1/admin/goaml-persons', () => HttpResponse.json([active, other])),
      http.put('*/api/v1/admin/goaml-persons/:id', ({ params }) => {
        putId = params.id as string;
        return HttpResponse.json(other);
      }),
    );
    renderPanel();

    await userEvent.click(await screen.findByText(/Manage additional reporting persons/));
    await userEvent.click(await screen.findByRole('button', { name: 'Make active' }));
    await waitFor(() => expect(putId).toBe('gp-2'));
  });

  it('deletes an additional person via the Popconfirm', async () => {
    let deletedId: string | undefined;
    server.use(
      http.get('*/api/v1/admin/goaml-persons', () => HttpResponse.json([active, other])),
      http.delete('*/api/v1/admin/goaml-persons/:id', ({ params }) => {
        deletedId = params.id as string;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderPanel();

    await userEvent.click(await screen.findByText(/Manage additional reporting persons/));
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
    const confirms = await screen.findAllByRole('button', { name: 'Delete' });
    await userEvent.click(confirms[confirms.length - 1]);
    await waitFor(() => expect(deletedId).toBe('gp-2'));
  });
});
