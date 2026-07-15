import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, InputNumber, Row, Select, Skeleton } from 'antd';
import { useGoamlConfig, useUpsertGoamlConfig } from './useAdmin';
import { useJurisdictions } from '../lookups/useLookups';
import { errorMessage } from '../../api/client';
import type { GoamlConfigRequest } from '../../types';

/** TENANT_ADMIN: view + edit the caller tenant's goAML B2B config (the single per-tenant row). */
export function GoamlConfigPanel() {
  const config = useGoamlConfig();
  const upsert = useUpsertGoamlConfig();
  const jurisdictions = useJurisdictions();
  const [form] = Form.useForm<GoamlConfigRequest>();
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (config.data) form.setFieldsValue(config.data);
  }, [config.data, form]);

  const onFinish = async (values: GoamlConfigRequest) => {
    setError(null);
    setSaved(false);
    try {
      await upsert.mutateAsync(values);
      setSaved(true);
    } catch (err) {
      setError(errorMessage(err, 'Could not save configuration'));
    }
  };

  if (config.isLoading) {
    return (
      <Card title="goAML B2B configuration">
        <Skeleton active />
      </Card>
    );
  }

  return (
    <Card title="goAML B2B configuration">
      {config.data === null && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="No configuration yet — set it below to enable FIU submission for this tenant."
        />
      )}
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      {saved && (
        <Alert type="success" showIcon message="Configuration saved" style={{ marginBottom: 16 }} />
      )}
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{ jurisdictionCode: 'AE', authMode: 'TOKEN' }}
      >
        <Row gutter={12}>
          <Col span={8}>
            <Form.Item label="Jurisdiction" name="jurisdictionCode" rules={[{ required: true }]}>
              <Select
                showSearch
                loading={jurisdictions.isLoading}
                placeholder="Select jurisdiction"
                optionFilterProp="label"
                options={(jurisdictions.data ?? []).map((j) => ({
                  value: j.code,
                  label: j.name && j.name !== j.code ? `${j.code} — ${j.name}` : j.code,
                }))}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="Entity ID (rentityId)" name="rentityId" rules={[{ required: true }]}>
              <InputNumber style={{ width: '100%' }} min={1} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="Auth mode" name="authMode" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'TOKEN', label: 'TOKEN' },
                  { value: 'BASIC', label: 'BASIC' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item label="Base URL" name="baseUrl" rules={[{ required: true }]}>
              <Input placeholder="https://goaml.uaefiu.gov.ae/..." />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item
              label="Secrets path"
              name="secretsPath"
              rules={[{ required: true }]}
              tooltip="Reference to the AWS Secrets Manager entry holding the FIU B2B credentials — not the credentials themselves."
            >
              <Input placeholder="goaml/<tenant>/b2b-credentials" />
            </Form.Item>
          </Col>
        </Row>
        <Button type="primary" htmlType="submit" loading={upsert.isPending}>
          Save configuration
        </Button>
      </Form>
    </Card>
  );
}
