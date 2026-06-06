import { useEffect, useState } from 'react';
import { Card, Col, Descriptions, Empty, List, Row, Select, Space, Tag, Typography } from 'antd';
import { useJurisdictions, useLookupCodes, useLookupSets } from './useLookups';

/**
 * Read-only reference browser: pick a jurisdiction → see its config + lookup sets → pick a set → see its
 * codes. The same data the builder validates against, so operators can inspect what's valid.
 */
export function LookupsBrowserPage() {
  const jurisdictionsQuery = useJurisdictions();
  const [jurisdiction, setJurisdiction] = useState('');
  const [set, setSet] = useState<string | undefined>();

  // Default to the first jurisdiction once loaded.
  useEffect(() => {
    if (!jurisdiction && jurisdictionsQuery.data?.length) {
      setJurisdiction(jurisdictionsQuery.data[0].code);
    }
  }, [jurisdiction, jurisdictionsQuery.data]);

  const setsQuery = useLookupSets(jurisdiction);
  const codesQuery = useLookupCodes(set ?? '', jurisdiction);

  const current = jurisdictionsQuery.data?.find((j) => j.code === jurisdiction);

  return (
    <>
      <Typography.Title level={3}>Reference data</Typography.Title>

      <Space style={{ marginBottom: 16 }}>
        <Typography.Text>Jurisdiction</Typography.Text>
        <Select
          style={{ width: 280 }}
          value={jurisdiction || undefined}
          onChange={(v) => {
            setJurisdiction(v);
            setSet(undefined);
          }}
          loading={jurisdictionsQuery.isLoading}
          aria-label="Jurisdiction"
          options={(jurisdictionsQuery.data ?? []).map((j) => ({
            value: j.code,
            label: `${j.name} (${j.code})`,
          }))}
        />
      </Space>

      {current && (
        <Card style={{ marginBottom: 16 }}>
          <Descriptions column={2} size="small">
            <Descriptions.Item label="Name">{current.name}</Descriptions.Item>
            <Descriptions.Item label="Currency">{current.defaultCurrency}</Descriptions.Item>
            <Descriptions.Item label="DPMS threshold">
              {current.dpmsThreshold ?? '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Report types">
              <Space wrap>
                {current.allowedReportTypes.map((t) => (
                  <Tag key={t}>{t}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      <Row gutter={16}>
        <Col span={8}>
          <Card title="Lookup sets" size="small" loading={setsQuery.isLoading}>
            <List
              size="small"
              dataSource={setsQuery.data?.sets ?? []}
              locale={{ emptyText: 'No sets' }}
              renderItem={(name) => (
                <List.Item
                  style={{ cursor: 'pointer', fontWeight: name === set ? 600 : undefined }}
                  onClick={() => setSet(name)}
                >
                  {name}
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col span={16}>
          <Card title={set ? `Codes — ${set}` : 'Codes'} size="small" loading={codesQuery.isLoading}>
            {!set ? (
              <Empty description="Select a lookup set" />
            ) : (
              <Space wrap>
                {(codesQuery.data ?? []).map((code) => (
                  <Tag key={code}>{code}</Tag>
                ))}
                {codesQuery.data?.length === 0 && <Typography.Text>No codes</Typography.Text>}
              </Space>
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
