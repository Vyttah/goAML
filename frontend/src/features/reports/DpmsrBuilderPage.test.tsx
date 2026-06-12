import { afterEach, describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/render';
import { setToken } from '../../auth/tokenStore';
import { makeToken } from '../../test/util';
import { DpmsrBuilderPage } from './DpmsrBuilderPage';
import type { ValidationMessage } from '../../types';

const DRAFT_KEY = 'goaml.dpmsrDraft';

afterEach(() => sessionStorage.clear());

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

async function selectIndicator(code: string, label: string) {
  // indicators is a lookup-backed multi-select; open it and click the matching option
  const input = screen.getByLabelText('Indicators');
  fireEvent.mouseDown(input);
  await userEvent.click(
    await screen.findByText(`${code} — ${label}`, { selector: '.ant-select-item-option-content' }),
  );
}

async function fillMinimalValidForm() {
  await typeById('entityReference', 'REF-1');
  await selectIndicator('ACTRC', 'Customer acting as undisclosed agent');
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
    expect(body.indicators).toEqual(['ACTRC']);
    const parties = body.parties as Array<Record<string, unknown>>;
    // a person party maps to the plain person slot (UAE DPMSR activity reports never use *_my_client)
    expect((parties[0].person as Record<string, unknown>).firstName).toBe('John');
    expect(parties[0]).not.toHaveProperty('personMyClient');
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

  it('restores an autosaved draft on mount', async () => {
    stubLookups();
    sessionStorage.setItem(
      DRAFT_KEY,
      JSON.stringify({ entityReference: 'DRAFT-REF-99', reason: 'restored reason' }),
    );

    renderBuilder();

    expect(await screen.findByText(/Restored an unsaved draft/i)).toBeInTheDocument();
    expect((document.getElementById('entityReference') as HTMLInputElement).value).toBe(
      'DRAFT-REF-99',
    );
  });

  it('restores a draft with nested dates (party birthdate + identification dates) without throwing', async () => {
    stubLookups();
    // Persist exactly what saveDpmsrDraft would: JSON.stringify turns nested Dayjs into ISO strings.
    // Before the deep rehydration these strings reached AntD v5 DatePickers and threw at render.
    sessionStorage.setItem(
      DRAFT_KEY,
      JSON.stringify({
        entityReference: 'DRAFT-NESTED-1',
        parties: [
          {
            _type: 'person',
            person: {
              firstName: 'John',
              lastName: 'Doe',
              birthdate: dayjs('1990-05-01'),
              identifications: [{ issueDate: dayjs('2020-01-02'), expiryDate: dayjs('2030-01-02') }],
            },
          },
        ],
      }),
    );

    renderBuilder();

    expect(await screen.findByText(/Restored an unsaved draft/i)).toBeInTheDocument();
    expect((document.getElementById('entityReference') as HTMLInputElement).value).toBe(
      'DRAFT-NESTED-1',
    );
    // the nested DatePicker values rendered as dates (i.e. they were rehydrated to Dayjs)
    expect(
      (document.getElementById('parties_0_person_birthdate') as HTMLInputElement).value,
    ).toBe('1990-05-01');
    expect(
      (document.getElementById('parties_0_person_identifications_0_issueDate') as HTMLInputElement)
        .value,
    ).toBe('2020-01-02');
  });

  it('clears the saved draft after a successful create', async () => {
    stubLookups();
    const captured: Captured = { body: null };
    stubCreate(captured);
    // Pre-seed a draft so we can prove it gets cleared on success.
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ entityReference: 'OLD' }));

    renderBuilder();
    await fillMinimalValidForm();
    await userEvent.click(screen.getByRole('button', { name: /create.*validate/i }));

    expect(await screen.findByText('Report created')).toBeInTheDocument();
    await waitFor(() => expect(sessionStorage.getItem(DRAFT_KEY)).toBeNull());
  });

  it('shows a field-level (inline) error for a known-invalid field, keeping the summary too', async () => {
    stubLookups();
    const captured: Captured = { body: null };
    // The server returns INVALID with a field-pathed message; the page maps it inline AND keeps the
    // bottom summary. (PersonFields' own required rules already block AntD on empty names, so this
    // exercises the server/Zod-style path→field surfacing on an otherwise-submittable form.)
    stubCreate(captured, 'INVALID', [
      {
        severity: 'ERROR',
        path: 'report.goods[0].item_type',
        code: 'MANDATORY',
        message: 'Item type required',
      },
    ]);

    renderBuilder();
    await fillMinimalValidForm();
    // Clear the goods estimated value — InputNumber + AntD required surfaces an inline field error
    // before submit, proving errors render at the field (not only in a summary).
    const value = document.getElementById('goods_0_estimatedValue') as HTMLInputElement;
    await userEvent.clear(value);
    await userEvent.click(screen.getByRole('button', { name: /create.*validate/i }));

    const item = value.closest('.ant-form-item');
    await waitFor(() =>
      expect(item?.querySelector('.ant-form-item-explain-error')).toBeInTheDocument(),
    );
  });
});
