import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useCreateUser, useUsers } from './useAdmin';
import { errorMessage } from '../../api/client';
import type { CreateUserRequest, UserView } from '../../types';

const ASSIGNABLE_ROLES = ['ANALYST', 'MLRO', 'TENANT_ADMIN'];

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/** TENANT_ADMIN: list + create users in the caller's own tenant. */
export function UsersPanel() {
  const users = useUsers();
  const createUser = useCreateUser();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm<CreateUserRequest>();

  const onFinish = async (values: CreateUserRequest) => {
    setError(null);
    try {
      await createUser.mutateAsync(values);
      setOpen(false);
      form.resetFields();
    } catch (err) {
      setError(errorMessage(err, 'Could not create user'));
    }
  };

  const columns: ColumnsType<UserView> = [
    { title: 'Email', dataIndex: 'email', key: 'email' },
    {
      title: 'Name',
      key: 'name',
      render: (_, u) => `${u.firstName} ${u.lastName}`,
    },
    {
      title: 'Roles',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles: string[]) => (
        <Space wrap>
          {roles.map((r) => (
            <Tag key={r} color="blue">
              {r}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'success' : 'default'}>{s}</Tag>,
    },
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', width: 190, render: formatTimestamp },
  ];

  return (
    <Card
      title="Users"
      style={{ marginBottom: 16 }}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
          Add user
        </Button>
      }
    >
      <Table<UserView>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={users.data ?? []}
        loading={users.isLoading}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        locale={{ emptyText: 'No users' }}
      />

      <Modal
        title="Add user"
        open={open}
        okText="Create"
        onOk={() => form.submit()}
        confirmLoading={createUser.isPending}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form form={form} layout="vertical" onFinish={onFinish} preserve={false}>
          <Form.Item label="Email" name="email" rules={[{ required: true, type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Password" name="password" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item label="First name" name="firstName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Last name" name="lastName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Role" name="role" rules={[{ required: true }]}>
            <Select
              placeholder="Select a role"
              options={ASSIGNABLE_ROLES.map((r) => ({ value: r, label: r }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
