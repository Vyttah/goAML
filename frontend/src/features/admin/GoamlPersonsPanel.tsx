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
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import {
  useCreateGoamlPerson,
  useDeleteGoamlPerson,
  useGoamlPersons,
  useUpdateGoamlPerson,
} from './useAdmin';
import { errorMessage } from '../../api/client';
import type { GoamlPersonRequest, GoamlPersonView } from '../../types';

const GENDERS = [
  { value: 'M', label: 'Male' },
  { value: 'F', label: 'Female' },
  { value: '-', label: 'Not provided' },
];

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * TENANT_ADMIN: manage the tenant's goAML reporting person(s) (the filing MLRO). The <b>active</b> person is
 * the default goAML auto-injects into every report, so the AML cockpit / feeds need not send it. At most one
 * is active; activating one demotes the rest.
 */
export function GoamlPersonsPanel() {
  const persons = useGoamlPersons();
  const create = useCreateGoamlPerson();
  const update = useUpdateGoamlPerson();
  const remove = useDeleteGoamlPerson();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GoamlPersonView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm<GoamlPersonRequest>();

  const openAdd = () => {
    setEditing(null);
    setError(null);
    form.resetFields();
    setOpen(true);
  };

  const openEdit = (p: GoamlPersonView) => {
    setEditing(p);
    setError(null);
    form.setFieldsValue(p);
    setOpen(true);
  };

  const onFinish = async (values: GoamlPersonRequest) => {
    setError(null);
    try {
      if (editing) {
        await update.mutateAsync({ id: editing.id, body: values });
      } else {
        await create.mutateAsync(values);
      }
      setOpen(false);
      form.resetFields();
    } catch (err) {
      setError(errorMessage(err, 'Could not save reporting person'));
    }
  };

  const makeActive = (p: GoamlPersonView) =>
    update.mutate({ id: p.id, body: { ...p, active: true } });

  const columns: ColumnsType<GoamlPersonView> = [
    { title: 'Name', key: 'name', render: (_, p) => `${p.firstName} ${p.lastName}` },
    { title: 'Nationality', dataIndex: 'nationality', key: 'nationality', width: 110 },
    { title: 'ID number', dataIndex: 'idNumber', key: 'idNumber' },
    { title: 'Email', dataIndex: 'email', key: 'email' },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 120,
      render: (active: boolean) =>
        active ? <Tag color="success">Active (default)</Tag> : <Tag>Inactive</Tag>,
    },
    { title: 'Updated', dataIndex: 'updatedAt', key: 'updatedAt', width: 180, render: formatTimestamp },
    {
      title: 'Actions',
      key: 'actions',
      width: 220,
      render: (_, p) => (
        <Space size="small">
          <Button size="small" onClick={() => openEdit(p)}>
            Edit
          </Button>
          {!p.active && (
            <Button size="small" onClick={() => makeActive(p)} loading={update.isPending}>
              Make active
            </Button>
          )}
          <Popconfirm
            title="Delete this reporting person?"
            okText="Delete"
            okButtonProps={{ danger: true }}
            onConfirm={() => remove.mutate(p.id)}
          >
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="goAML reporting person (MLRO)"
      style={{ marginBottom: 16 }}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
          Add person
        </Button>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="The active person is auto-filled as the report's MLRO when a report is created without one — so imports and integrations need not supply it."
      />
      <Table<GoamlPersonView>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={persons.data ?? []}
        loading={persons.isLoading}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        locale={{ emptyText: 'No reporting person configured yet' }}
      />

      <Modal
        title={editing ? 'Edit reporting person' : 'Add reporting person'}
        open={open}
        okText={editing ? 'Save' : 'Create'}
        onOk={() => form.submit()}
        confirmLoading={create.isPending || update.isPending}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          preserve={false}
          initialValues={{ active: true }}
        >
          <Form.Item label="First name" name="firstName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Last name" name="lastName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Gender" name="gender">
            <Select allowClear options={GENDERS} placeholder="Select" />
          </Form.Item>
          <Form.Item label="Nationality" name="nationality" tooltip="ISO country code, e.g. AE">
            <Input placeholder="AE" />
          </Form.Item>
          <Form.Item label="ID number" name="idNumber">
            <Input />
          </Form.Item>
          <Form.Item label="SSN" name="ssn">
            <Input />
          </Form.Item>
          <Form.Item label="Email" name="email" rules={[{ type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Occupation" name="occupation">
            <Input />
          </Form.Item>
          <Form.Item
            label="Active (the auto-injected default)"
            name="active"
            valuePropName="checked"
            tooltip="Only one person can be active; activating this one demotes the others."
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
