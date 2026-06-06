import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { LookupsBrowserPage } from './LookupsBrowserPage';

function stub() {
  server.use(
    http.get('*/api/v1/lookups/jurisdictions', () =>
      HttpResponse.json([
        {
          code: 'ae',
          name: 'United Arab Emirates',
          defaultCurrency: 'AED',
          allowedReportTypes: ['DPMSR'],
          dpmsThreshold: 55000,
          lookupSet: 'ae',
        },
      ]),
    ),
    http.get('*/api/v1/lookups/ae', () =>
      HttpResponse.json({ jurisdiction: 'ae', sets: ['countries', 'currencies'] }),
    ),
    http.get('*/api/v1/lookups/ae/countries', () =>
      HttpResponse.json({ jurisdiction: 'ae', set: 'countries', codes: ['AE', 'US', 'IN'] }),
    ),
  );
}

function render() {
  setToken(makeToken({ roles: ['ANALYST'] }));
  return renderWithProviders(<LookupsBrowserPage />, ['/reference']);
}

describe('LookupsBrowserPage', () => {
  it('shows the default jurisdiction, its sets, and a set’s codes', async () => {
    stub();
    render();

    // jurisdiction details + sets load for the default jurisdiction
    expect(await screen.findByText('United Arab Emirates')).toBeInTheDocument();
    expect(await screen.findByText('countries')).toBeInTheDocument();
    expect(screen.getByText('currencies')).toBeInTheDocument();

    // selecting a set shows its codes
    await userEvent.click(screen.getByText('countries'));
    expect(await screen.findByText('AE')).toBeInTheDocument();
    expect(screen.getByText('US')).toBeInTheDocument();
  });
});
