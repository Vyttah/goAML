import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Collapse,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
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
import { CodeSelect } from '../../components/lookups/CodeSelect';
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

/** The editable fields of the reporting person (shared by the primary form and the "add" dialog). */
function PersonFields() {
  return (
    <>
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
        <CodeSelect set="countries" placeholder="Select country" />
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
    </>
  );
}

/**
 * TENANT_ADMIN: manage the tenant's goAML reporting person (the filing MLRO). A tenant almost always has ONE,
 * so the panel shows a single edit-in-place form for the *active* person — Save updates it (or creates it if
 * none exists). The active person is the default goAML auto-injects into every report. Rotating/extra MLROs are
 * tucked behind an "additional persons" section that most admins never need.
 */
export function GoamlPersonsPanel() {
  const persons = useGoamlPersons();
  const create = useCreateGoamlPerson();
  const update = useUpdateGoamlPerson();
  const remove = useDeleteGoamlPerson();
  const [error, setError] = useState<string | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [addForm] = Form.useForm<GoamlPersonRequest>();

  const list = persons.data ?? [];
  const active = list.find((p) => p.active) ?? null;
  const others = list.filter((p) => !p.active);

  // Primary form: save edits to the active person, or create the first one (active) if none exists yet.
  const onSavePrimary = async (values: GoamlPersonRequest) => {
    setError(null);
    try {
      if (active) {
        await update.mutateAsync({ id: active.id, body: { ...values, active: true } });
      } else {
        await create.mutateAsync({ ...values, active: true });
      }
    } catch (err) {
      setError(errorMessage(err, 'Could not save reporting person'));
    }
  };

  // Advanced: add an extra (inactive) person, activate one, or delete.
  const onAdd = async (values: GoamlPersonRequest) => {
    setError(null);
    try {
      await create.mutateAsync({ ...values, active: false });
      setAddOpen(false);
      addForm.resetFields();
    } catch (err) {
      setError(errorMessage(err, 'Could not add person'));
    }
  };

  const makeActive = (p: GoamlPersonView) => {
    setError(null);
    update.mutate(
      { id: p.id, body: { ...p, active: true } },
      { onError: (err) => setError(errorMessage(err, 'Could not activate person')) },
    );
  };

  const onRemove = (p: GoamlPersonView) => {
    setError(null);
    remove.mutate(p.id, { onError: (err) => setError(errorMessage(err, 'Could not delete person')) });
  };

  const otherColumns: ColumnsType<GoamlPersonView> = [
    { title: 'Name', key: 'name', render: (_, p) => `${p.firstName} ${p.lastName}` },
    { title: 'Nationality', dataIndex: 'nationality', key: 'nationality', width: 110 },
    { title: 'Email', dataIndex: 'email', key: 'email' },
    { title: 'Updated', dataIndex: 'updatedAt', key: 'updatedAt', width: 170, render: formatTimestamp },
    {
      title: 'Actions',
      key: 'actions',
      width: 200,
      render: (_, p) => (
        <Space size="small">
          <Button size="small" type="primary" ghost onClick={() => makeActive(p)} loading={update.isPending}>
            Make active
          </Button>
          <Popconfirm title="Delete this person?" okText="Delete" okButtonProps={{ danger: true }} onConfirm={() => onRemove(p)}>
            <Button size="small" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card title="goAML reporting person (MLRO)" style={{ marginBottom: 16 }} loading={persons.isLoading}>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={
          active
            ? 'This is the active reporting person — goAML auto-fills it as every report’s MLRO. Edit and Save to update it.'
            : 'No reporting person yet. Fill this in and Save — it becomes the MLRO goAML auto-fills on every report.'
        }
      />
      {error && <Alert type="error" showIcon closable message={error} style={{ marginBottom: 16 }} onClose={() => setError(null)} />}

      {/* Single edit-in-place form for the active person (keyed so it re-inits when the active person changes). */}
      <Form
        key={active?.id ?? 'new'}
        name="reportingPerson"
        layout="vertical"
        style={{ maxWidth: 520 }}
        initialValues={active ?? {}}
        onFinish={onSavePrimary}
      >
        <PersonFields />
        <Space>
          <Button type="primary" htmlType="submit" loading={create.isPending || update.isPending}>
            {active ? 'Save' : 'Create reporting person'}
          </Button>
          {active && <Tag color="success">Active (default)</Tag>}
        </Space>
      </Form>

      {/* Advanced: rotating / additional MLROs. Most tenants never open this. */}
      <Collapse
        ghost
        style={{ marginTop: 16 }}
        items={[
          {
            key: 'others',
            label: `Manage additional reporting persons${others.length ? ` (${others.length})` : ''}`,
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button icon={<PlusOutlined />} onClick={() => { setError(null); setAddOpen(true); }}>
                    Add another person
                  </Button>
                </Space>
                <Table<GoamlPersonView>
                  rowKey="id"
                  size="small"
                  columns={otherColumns}
                  dataSource={others}
                  pagination={{ pageSize: 5, hideOnSinglePage: true }}
                  locale={{ emptyText: 'No additional persons' }}
                />
              </>
            ),
          },
        ]}
      />

      <Modal
        title="Add reporting person"
        open={addOpen}
        okText="Add"
        onOk={() => addForm.submit()}
        confirmLoading={create.isPending}
        onCancel={() => setAddOpen(false)}
        destroyOnHidden
      >
        <Form form={addForm} name="reportingPersonAdd" layout="vertical" onFinish={onAdd} preserve={false}>
          <PersonFields />
        </Form>
      </Modal>
    </Card>
  );
}
