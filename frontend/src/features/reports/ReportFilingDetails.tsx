import { Collapse, Descriptions, Empty, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

/**
 * Renders the stored filing input (Phase D.3) into readable DPMSR sections — header, reporting person,
 * parties, goods — with a collapsible raw-JSON fallback so nothing is hidden. The input is usually a
 * `DpmsrReportPayload` but screening/accounting feeds store the curated shape, so every field is probed
 * defensively (the typed sections show what they find; the raw view always shows the rest).
 */

function asRecord(v: unknown): Record<string, unknown> | undefined {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : undefined;
}

function asArray(v: unknown): unknown[] {
  return Array.isArray(v) ? v : [];
}

function str(v: unknown): string | undefined {
  if (typeof v === 'string') return v || undefined;
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  return undefined;
}

function dash(v: string | undefined): string {
  return v ?? '—';
}

/** A party's display name: entity name (my-client or lenient), person first+last (my-client or lenient),
 *  or a bank account — covering every subject shape the cockpit's hybrid flow files. */
function partyName(party: Record<string, unknown>): string {
  const entity = asRecord(party.entityMyClient) ?? asRecord(party.entity);
  if (entity) return str(entity.name) ?? '(entity)';
  const person = asRecord(party.personMyClient) ?? asRecord(party.person);
  if (person) {
    const name = [str(person.firstName), str(person.lastName)].filter(Boolean).join(' ');
    return name || '(person)';
  }
  const account = asRecord(party.accountMyClient) ?? asRecord(party.account);
  if (account) return str(account.institutionName) ?? str(account.account) ?? '(account)';
  return '(unknown party)';
}

function partyKind(party: Record<string, unknown>): string {
  if (asRecord(party.entityMyClient)) return 'Entity (my client)';
  if (asRecord(party.entity)) return 'Entity';
  if (asRecord(party.personMyClient)) return 'Person (my client)';
  if (asRecord(party.person)) return 'Person';
  if (asRecord(party.accountMyClient) ?? asRecord(party.account)) return 'Account';
  return '—';
}

interface PartyRow {
  key: number;
  name: string;
  kind: string;
  reason: string;
}

interface GoodsRow {
  key: number;
  itemType: string;
  description: string;
  value: string;
}

export function ReportFilingDetails({ input }: { input: Record<string, unknown> | null }) {
  if (!input) {
    return <Empty description="No stored filing input" />;
  }

  const indicators = asArray(input.indicators).map(str).filter(Boolean) as string[];
  const person = asRecord(input.reportingPerson);
  const parties = asArray(input.parties).map(asRecord).filter(Boolean) as Record<string, unknown>[];
  const goods = asArray(input.goods).map(asRecord).filter(Boolean) as Record<string, unknown>[];

  const partyRows: PartyRow[] = parties.map((p, i) => ({
    key: i,
    name: partyName(p),
    kind: partyKind(p),
    reason: dash(str(p.reason)),
  }));

  const goodsRows: GoodsRow[] = goods.map((g, i) => {
    const value = str(g.estimatedValue);
    const currency = str(g.currencyCode);
    return {
      key: i,
      itemType: dash(str(g.itemType)),
      description: dash(str(g.description) ?? str(g.itemMake)),
      value: value ? `${value}${currency ? ` ${currency}` : ''}` : '—',
    };
  });

  const partyColumns: ColumnsType<PartyRow> = [
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Kind', dataIndex: 'kind', key: 'kind', width: 110 },
    { title: 'Role / reason', dataIndex: 'reason', key: 'reason', width: 200 },
  ];

  const goodsColumns: ColumnsType<GoodsRow> = [
    { title: 'Item type', dataIndex: 'itemType', key: 'itemType', width: 140 },
    { title: 'Description', dataIndex: 'description', key: 'description' },
    { title: 'Estimated value', dataIndex: 'value', key: 'value', width: 180 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Descriptions column={2} size="small" title="Filing">
        <Descriptions.Item label="Reason">{dash(str(input.reason))}</Descriptions.Item>
        <Descriptions.Item label="Action">{dash(str(input.action))}</Descriptions.Item>
        <Descriptions.Item label="Submission date">{dash(str(input.submissionDate))}</Descriptions.Item>
        <Descriptions.Item label="FIU ref">{dash(str(input.fiuRefNumber))}</Descriptions.Item>
        <Descriptions.Item label="Indicators" span={2}>
          {indicators.length ? (
            <Space wrap size={[4, 4]}>
              {indicators.map((i) => (
                <Tag key={i}>{i}</Tag>
              ))}
            </Space>
          ) : (
            '—'
          )}
        </Descriptions.Item>
      </Descriptions>

      {person && (
        <Descriptions column={2} size="small" title="Reporting person">
          <Descriptions.Item label="Name">
            {dash([str(person.firstName), str(person.lastName)].filter(Boolean).join(' ') || undefined)}
          </Descriptions.Item>
          <Descriptions.Item label="Nationality">{dash(str(person.nationality1))}</Descriptions.Item>
          <Descriptions.Item label="ID number">{dash(str(person.idNumber))}</Descriptions.Item>
          <Descriptions.Item label="Occupation">{dash(str(person.occupation))}</Descriptions.Item>
        </Descriptions>
      )}

      <div>
        <Typography.Text strong>Parties ({partyRows.length})</Typography.Text>
        <Table<PartyRow>
          rowKey="key"
          size="small"
          style={{ marginTop: 8 }}
          columns={partyColumns}
          dataSource={partyRows}
          pagination={false}
          locale={{ emptyText: 'No parties' }}
        />
      </div>

      <div>
        <Typography.Text strong>Goods ({goodsRows.length})</Typography.Text>
        <Table<GoodsRow>
          rowKey="key"
          size="small"
          style={{ marginTop: 8 }}
          columns={goodsColumns}
          dataSource={goodsRows}
          pagination={false}
          locale={{ emptyText: 'No goods' }}
        />
      </div>

      <Collapse
        size="small"
        items={[
          {
            key: 'raw',
            label: 'Raw filing input (JSON)',
            children: (
              <pre
                aria-label="filing-input-json"
                style={{
                  maxHeight: '40vh',
                  overflow: 'auto',
                  margin: 0,
                  background: '#f6f8fa',
                  padding: 12,
                  borderRadius: 4,
                  fontSize: 12,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {JSON.stringify(input, null, 2)}
              </pre>
            ),
          },
        ]}
      />
    </Space>
  );
}
