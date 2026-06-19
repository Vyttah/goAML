import { describe, it, expect } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { CodeSelect } from './CodeSelect';

// CodeSelect is backed by a server lookup set so the choices always match server validation. It shows
// "CODE — label" when the backend supplies a distinct label, and the bare code otherwise.
describe('CodeSelect', () => {
  it('loads the lookup set and renders "CODE — label" options', async () => {
    server.use(
      http.get('*/api/v1/lookups/ae/countries', () =>
        HttpResponse.json({
          jurisdiction: 'ae',
          set: 'countries',
          codes: ['AE', 'IN'],
          entries: [
            { code: 'AE', label: 'United Arab Emirates' },
            { code: 'IN', label: 'India' },
          ],
        }),
      ),
    );
    setToken(makeToken());
    renderWithProviders(<CodeSelect set="countries" />);

    fireEvent.mouseDown(screen.getByRole('combobox'));
    expect(
      await screen.findByText('AE — United Arab Emirates', { selector: '.ant-select-item-option-content' }),
    ).toBeInTheDocument();
    expect(screen.getByText('IN — India', { selector: '.ant-select-item-option-content' })).toBeInTheDocument();
  });

  it('shows the bare code when the backend label equals the code', async () => {
    server.use(
      http.get('*/api/v1/lookups/ae/item_status', () =>
        HttpResponse.json({ jurisdiction: 'ae', set: 'item_status', codes: ['NEW'], entries: [{ code: 'NEW', label: 'NEW' }] }),
      ),
    );
    setToken(makeToken());
    renderWithProviders(<CodeSelect set="item_status" />);

    fireEvent.mouseDown(screen.getByRole('combobox'));
    expect(await screen.findByText('NEW', { selector: '.ant-select-item-option-content' })).toBeInTheDocument();
  });
});
