import { describe, it, expect, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { GoamlConfigPanel } from './GoamlConfigPanel';

const config = {
  tenantId: 't-1', jurisdictionCode: 'AE', rentityId: 3177,
  baseUrl: 'https://goaml.test/uae', secretsPath: 'goaml/t/creds', authMode: 'TOKEN',
  updatedAt: '2026-06-07T00:00:00Z',
};

function renderPanel() {
  setToken(makeToken({ roles: ['TENANT_ADMIN'] }));
  return renderWithProviders(<GoamlConfigPanel />);
}

describe('GoamlConfigPanel', () => {
  // The Jurisdiction field is a dropdown backed by the jurisdictions lookup.
  beforeEach(() => {
    server.use(
      http.get('*/api/v1/lookups/jurisdictions', () =>
        HttpResponse.json([
          { code: 'AE', name: 'United Arab Emirates', defaultCurrency: 'AED', allowedReportTypes: ['DPMSR'], dpmsThreshold: 55000, lookupSet: 'ae' },
        ]),
      ),
    );
  });

  it('pre-fills the form from an existing config (the populated branch)', async () => {
    server.use(http.get('*/api/v1/admin/goaml-config', () => HttpResponse.json(config)));
    renderPanel();
    expect(await screen.findByDisplayValue('https://goaml.test/uae')).toBeInTheDocument();
    expect(screen.getByDisplayValue('goaml/t/creds')).toBeInTheDocument();
    expect(screen.getByDisplayValue('3177')).toBeInTheDocument();
  });

  it('saves edits to an existing config and confirms', async () => {
    server.use(
      http.get('*/api/v1/admin/goaml-config', () => HttpResponse.json(config)),
      http.put('*/api/v1/admin/goaml-config', () => HttpResponse.json({ ...config, updatedAt: '2026-06-08T00:00:00Z' })),
    );
    renderPanel();
    await screen.findByDisplayValue('https://goaml.test/uae');
    await userEvent.click(screen.getByRole('button', { name: /save configuration/i }));
    expect(await screen.findByText('Configuration saved')).toBeInTheDocument();
  });
});
