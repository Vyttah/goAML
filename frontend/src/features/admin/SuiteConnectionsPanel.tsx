import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import {
  useCreateTenantExternalRef,
  useCreateTrustedService,
  useDeleteTenantExternalRef,
  useDeleteTrustedService,
  useTenantExternalRefs,
  useTenants,
  useTrustedServices,
} from './useAdmin';
import { errorMessage } from '../../api/client';
import type {
  CreateTenantExternalRefRequest,
  CreateTrustedServiceRequest,
  TenantExternalRefView,
  TrustedServiceView,
} from '../../types';

const SOURCE_SYSTEMS = ['SCREENING', 'ACCOUNTING'] as const;

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * SUPER_ADMIN: manage the federated suite connection — register a sibling app's public key (trusted service)
 * and map each suite company id to a goAML tenant. Replaces the hand-run SQL bootstrap; goAML stays the
 * system-of-record for cross-app trust + tenant resolution.
 */
export function SuiteConnectionsPanel() {
  const services = useTrustedServices();
  const links = useTenantExternalRefs();
  const tenants = useTenants();
  const createService = useCreateTrustedService();
  const deleteService = useDeleteTrustedService();
  const createLink = useCreateTenantExternalRef();
  const deleteLink = useDeleteTenantExternalRef();

  const [serviceOpen, setServiceOpen] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [serviceForm] = Form.useForm<CreateTrustedServiceRequest>();
  const [linkForm] = Form.useForm<CreateTenantExternalRefRequest>();

  const tenantName = (id: string): string => {
    const t = tenants.data?.find((x) => x.id === id);
    return t ? `${t.name} (${t.slug})` : id;
  };

  const submitService = async (values: CreateTrustedServiceRequest) => {
    setError(null);
    try {
      await createService.mutateAsync(values);
      setServiceOpen(false);
      serviceForm.resetFields();
    } catch (err) {
      setError(errorMessage(err, 'Could not register the trusted service'));
    }
  };

  const submitLink = async (values: CreateTenantExternalRefRequest) => {
    setError(null);
    try {
      await createLink.mutateAsync(values);
      setLinkOpen(false);
      linkForm.resetFields();
    } catch (err) {
      setError(errorMessage(err, 'Could not create the company link'));
    }
  };

  const serviceColumns: ColumnsType<TrustedServiceView> = [
    { title: 'Source system', dataIndex: 'sourceSystem', key: 'sourceSystem', width: 150 },
    { title: 'Description', dataIndex: 'description', key: 'description' },
    {
      title: 'JIT',
      dataIndex: 'jitProvisioning',
      key: 'jitProvisioning',
      width: 80,
      render: (v: boolean) => (v ? <Tag color="blue">on</Tag> : <Tag>off</Tag>),
    },
    {
      title: 'Default role',
      dataIndex: 'defaultRole',
      key: 'defaultRole',
      width: 130,
      render: (r: string | null) => r ?? 'ANALYST',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'success' : 'default'}>{s}</Tag>,
    },
    {
      title: '',
      key: 'actions',
      width: 90,
      render: (_, row) => (
        <Popconfirm
          title="Revoke this trusted service?"
          okText="Revoke"
          okButtonProps={{ danger: true }}
          onConfirm={() => deleteService.mutate(row.id)}
        >
          <Button size="small" danger type="link">
            Revoke
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const linkColumns: ColumnsType<TenantExternalRefView> = [
    { title: 'Source system', dataIndex: 'sourceSystem', key: 'sourceSystem', width: 150 },
    { title: 'Company id (org ref)', dataIndex: 'externalOrgRef', key: 'externalOrgRef' },
    { title: 'goAML tenant', dataIndex: 'tenantId', key: 'tenantId', render: (id: string) => tenantName(id) },
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', width: 190, render: formatTimestamp },
    {
      title: '',
      key: 'actions',
      width: 90,
      render: (_, row) => (
        <Popconfirm
          title="Remove this company link?"
          okText="Remove"
          okButtonProps={{ danger: true }}
          onConfirm={() => deleteLink.mutate(row.id)}
        >
          <Button size="small" danger type="link">
            Remove
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card title="Suite Connections" style={{ marginBottom: 16 }}>
      <Typography.Paragraph type="secondary">
        Register a sibling Vyttah app once (its public key) and map each company to a goAML tenant. Users are
        then auto-provisioned on first use — no second login.
      </Typography.Paragraph>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} closable />}

      <Space style={{ marginBottom: 8 }}>
        <Typography.Text strong>Trusted services</Typography.Text>
        <Button size="small" icon={<PlusOutlined />} onClick={() => setServiceOpen(true)}>
          Register
        </Button>
      </Space>
      <Table<TrustedServiceView>
        rowKey="id"
        size="small"
        columns={serviceColumns}
        dataSource={services.data ?? []}
        loading={services.isLoading}
        pagination={false}
        locale={{ emptyText: 'No trusted services yet' }}
        style={{ marginBottom: 24 }}
      />

      <Space style={{ marginBottom: 8 }}>
        <Typography.Text strong>Company links</Typography.Text>
        <Button size="small" icon={<PlusOutlined />} onClick={() => setLinkOpen(true)}>
          Add link
        </Button>
      </Space>
      <Table<TenantExternalRefView>
        rowKey="id"
        size="small"
        columns={linkColumns}
        dataSource={links.data ?? []}
        loading={links.isLoading}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        locale={{ emptyText: 'No company links yet' }}
      />

      <Modal
        title="Register trusted service"
        open={serviceOpen}
        okText="Register"
        onOk={() => serviceForm.submit()}
        confirmLoading={createService.isPending}
        onCancel={() => setServiceOpen(false)}
        destroyOnHidden
      >
        <Form
          form={serviceForm}
          layout="vertical"
          onFinish={submitService}
          initialValues={{ sourceSystem: 'SCREENING', jitProvisioning: true, defaultRole: 'ANALYST' }}
          preserve={false}
        >
          <Form.Item label="Source system" name="sourceSystem" rules={[{ required: true }]}>
            <Select options={SOURCE_SYSTEMS.map((s) => ({ value: s, label: s }))} />
          </Form.Item>
          <Form.Item label="Description" name="description">
            <Input placeholder="AML customer-service" />
          </Form.Item>
          <Form.Item
            label="Public key (PEM)"
            name="publicKeyPem"
            rules={[{ required: true }]}
            extra="The sibling app keeps the matching private key; it never leaves that service."
          >
            <Input.TextArea rows={5} placeholder="-----BEGIN PUBLIC KEY-----" />
          </Form.Item>
          <Form.Item label="JIT-provision users" name="jitProvisioning" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Default role for new users" name="defaultRole" extra="Blank → ANALYST.">
            <Input placeholder="ANALYST" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Add company link"
        open={linkOpen}
        okText="Add"
        onOk={() => linkForm.submit()}
        confirmLoading={createLink.isPending}
        onCancel={() => setLinkOpen(false)}
        destroyOnHidden
      >
        <Form
          form={linkForm}
          layout="vertical"
          onFinish={submitLink}
          initialValues={{ sourceSystem: 'SCREENING' }}
          preserve={false}
        >
          <Form.Item label="Source system" name="sourceSystem" rules={[{ required: true }]}>
            <Select options={SOURCE_SYSTEMS.map((s) => ({ value: s, label: s }))} />
          </Form.Item>
          <Form.Item
            label="Company id (org ref)"
            name="externalOrgRef"
            rules={[{ required: true }]}
            extra="The company id as the sibling app knows it (e.g. the AML companyId)."
          >
            <Input placeholder="aumtech_1" />
          </Form.Item>
          <Form.Item label="goAML tenant" name="tenantId" rules={[{ required: true }]}>
            <Select
              loading={tenants.isLoading}
              options={(tenants.data ?? []).map((t) => ({ value: t.id, label: `${t.name} (${t.slug})` }))}
              placeholder="Select a tenant"
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
