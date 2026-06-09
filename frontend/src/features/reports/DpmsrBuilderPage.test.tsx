import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { DpmsrBuilderPage } from './DpmsrBuilderPage';
import type { ValidationMessage } from '../../types';

function stubLookups() {
  server.use(
    http.get('*/api/v1/lookups/ae/countries', () =>
      HttpResponse.json({ jurisdiction: 'ae', set: 'countries', codes: ['AE', 'US', 'IN'] }),
    ),
    http.get('*/api/v1/lookups/ae/currencies', () =>
      HttpResponse.json({ jurisdiction: 'ae', set: 'currencies', codes: ['AED', 'USD'] }),
    ),
    http.get('*/api/v1/lookups/ae/item_types', () =>
      HttpResponse.json({
        jurisdiction: 'ae',
        set: 'item_types',
        codes: ['GOLD', 'DIMND'],
        entries: [
          { code: 'GOLD', label: 'Gold' },
          { code: 'DIMND', label: 'Diamond' },
        ],
      }),
    ),
    http.get('*/api/v1/lookups/ae/item_status', () =>
      HttpResponse.json({
        jurisdiction: 'ae',
        set: 'item_status',
        codes: ['SOLD'],
        entries: [{ code: 'SOLD', label: 'Sold' }],
      }),
    ),
    http.get('*/api/v1/lookups/ae/report_indicators', () =>
      HttpResponse.json({
        jurisdiction: 'ae',
        set: 'report_indicators',
        codes: ['ACTRC'],
        entries: [{ code: 'ACTRC', label: 'Customer acting as undisclosed agent' }],
      }),
    ),
  );
}

interface Captured {
  body: Record<string, unknown> | null;
}

function stubCreate(captured: Captured, status = 'VALID', messages: ValidationMessage[] = []) {
  server.use(
    http.post('*/api/v1/reports', async ({ request }) => {
      captured.body = (await request.json()) as Record<string, unknown>;
      return HttpResponse.json(
        { reportId: 'rep-1', status, validationMessages: messages },
        { status: 201 },
      );
    }),
  );
}

function routes() {
  return (
    <Routes>
      <Route path="/reports/new" element={<DpmsrBuilderPage />} />
      <Route path="/reports/:id" element={<div>REPORT DETAIL PAGE</div>} />
      <Route path="/dashboard" element={<div>DASHBOARD</div>} />
    </Routes>
  );
}

async function typeById(id: string, value: string) {
  const el = document.getElementById(id);
  if (!el) throw new Error(`no element #${id}`);
  await userEvent.type(el, value);
}

async function selectItemType(code: string, label: string) {
  // item type is a lookup-backed Select; open it (mouseDown opens AntD selects), filter by typing,
  // then click the single matching option
  const input = screen.getByLabelText('Item type');
  fireEvent.mouseDown(input);
  await userEvent.type(input, code);
  await userEvent.click(
    await screen.findByText(`${code} — ${label}`, { selector: '.ant-select-item-option-content' }),
  );
}

async function fillMinimalValidForm() {
  await typeById('entityReference', 'REF-1');
  await typeById('reportingPerson_firstName', 'Jane');
  await typeById('reportingPerson_lastName', 'Roe');
  await typeById('parties_0_person_firstName', 'John');
  await typeById('parties_0_person_lastName', 'Doe');
  await selectItemType('GOLD', 'Gold');
  await typeById('goods_0_estimatedValue', '60000');
}

function renderBuilder() {
  setToken(makeToken({ roles: ['MLRO'] }));
  return renderWithProviders(routes(), ['/reports/new']);
}

describe('DpmsrBuilderPage', () => {
  it('builds the full-fidelity DpmsrReportPayload and POSTs it on submit', async () => {
    stubLookups();
    const captured: Captured = { body: null };
    stubCreate(captured);

    renderBuilder();
    await fillMinimalValidForm();
    await userEvent.click(screen.getByRole('button', { name: /create.*validate/i }));

    expect(await screen.findByText('Report created')).toBeInTheDocument();

    const body = captured.body as Record<string, unknown>;
    expect(body.entityReference).toBe('REF-1');
    expect(typeof body.submissionDate).toBe('string');
    expect((body.reportingPerson as Record<string, unknown>).firstName).toBe('Jane');
    const parties = body.parties as Array<Record<string, unknown>>;
    // a person party maps to the schema's personMyClient slot (full-fidelity payload shape)
    expect((parties[0].personMyClient as Record<string, unknown>).firstName).toBe('John');
    expect(parties[0]).not.toHaveProperty('entity');
    const goods = body.goods as Array<Record<string, unknown>>;
    expect(goods[0].itemType).toBe('GOLD');
    expect(goods[0].estimatedValue).toBe(60000);
  });

  it('renders the server validation messages inline', async () => {
    stubLookups();
    const captured: Captured = { body: null };
    stubCreate(captured, 'INVALID', [
      { severity: 'ERROR', path: 'report.goods[0].item_type', code: 'MANDATORY', message: 'Item type required' },
    ]);

    renderBuilder();
    await fillMinimalValidForm();
    await userEvent.click(screen.getByRole('button', { name: /create.*validate/i }));

    expect(await screen.findByText('Item type required')).toBeInTheDocument();
    expect(screen.getByText('INVALID')).toBeInTheDocument();
  });

  it('navigates to the created report on "View report"', async () => {
    stubLookups();
    const captured: Captured = { body: null };
    stubCreate(captured);

    renderBuilder();
    await fillMinimalValidForm();
    await userEvent.click(screen.getByRole('button', { name: /create.*validate/i }));

    const panel = await screen.findByText('Report created');
    expect(panel).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /view report/i }));
    expect(await screen.findByText('REPORT DETAIL PAGE')).toBeInTheDocument();
  });
});
