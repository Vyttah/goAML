import { useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, Modal, Row, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useProvisionTenant, useTenants } from './useAdmin';
import { errorMessage } from '../../api/client';
import type { TenantProvisioningRequest, TenantView } from '../../types';

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/** SUPER_ADMIN: list tenants + provision a new one (schema + initial TENANT_ADMIN). */
export function TenantsPanel() {
  const tenants = useTenants();
  const provision = useProvisionTenant();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm<TenantProvisioningRequest>();

  const onFinish = async (values: TenantProvisioningRequest) => {
    setError(null);
    try {
      await provision.mutateAsync(values);
      setOpen(false);
      form.resetFields();
    } catch (err) {
      setError(errorMessage(err, 'Provisioning failed'));
    }
  };

  const columns: ColumnsType<TenantView> = [
    { title: 'Company ID', dataIndex: 'slug', key: 'slug' },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Jurisdiction', dataIndex: 'jurisdictionCode', key: 'jurisdictionCode', width: 120 },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'success' : 'default'}>{s}</Tag>,
    },
    { title: 'Schema', dataIndex: 'schemaName', key: 'schemaName' },
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', width: 190, render: formatTimestamp },
  ];

  return (
    <Card
      title="Tenants"
      style={{ marginBottom: 16 }}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
          Provision tenant
        </Button>
      }
    >
      <Table<TenantView>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={tenants.data ?? []}
        loading={tenants.isLoading}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        locale={{ emptyText: 'No tenants yet' }}
      />

      <Modal
        title="Provision tenant"
        open={open}
        okText="Provision"
        onOk={() => form.submit()}
        confirmLoading={provision.isPending}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ jurisdictionCode: 'AE' }}
          preserve={false}
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                label="Company ID"
                name="slug"
                rules={[
                  { required: true, message: 'Company ID is required' },
                  {
                    pattern: /^[A-Za-z0-9_-]{3,64}$/,
                    message: '3–64 chars: letters, digits, hyphens, underscores',
                  },
                ]}
                extra="Used at login and as the schema name (stored lower-case). Keep equal to the AML company id."
              >
                <Input placeholder="acme-gold" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Name" name="name" rules={[{ required: true }]}>
                <Input placeholder="Acme Dealers FZE" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Jurisdiction" name="jurisdictionCode" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Typography.Text type="secondary">Initial tenant administrator</Typography.Text>
          <Row gutter={12} style={{ marginTop: 8 }}>
            <Col span={12}>
              <Form.Item
                label="Admin email"
                name="adminEmail"
                rules={[{ required: true, type: 'email' }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Admin password" name="adminPassword" rules={[{ required: true }]}>
                <Input.Password />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="First name" name="adminFirstName" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Last name" name="adminLastName" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Card>
  );
}
