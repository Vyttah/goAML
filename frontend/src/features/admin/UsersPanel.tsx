import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useCreateUser, useDeleteUser, useUpdateUser, useUsers } from './useAdmin';
import { errorMessage } from '../../api/client';
import type { CreateUserRequest, UpdateUserRequest, UserView } from '../../types';

const ASSIGNABLE_ROLES = ['ANALYST', 'MLRO', 'TENANT_ADMIN'];

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * TENANT_ADMIN: list + create users, and manage existing ones — edit (name/role), enable/disable, or delete.
 * Disabling keeps the user (and their audit trail) but blocks login; delete is a hard remove and the backend
 * refuses it for a user who authored/reviewed reports (disable instead). Email is the login identity (immutable).
 */
export function UsersPanel() {
  const users = useUsers();
  const createUser = useCreateUser();
  const updateUser = useUpdateUser();
  const deleteUser = useDeleteUser();
  const [addOpen, setAddOpen] = useState(false);
  const [editing, setEditing] = useState<UserView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [addForm] = Form.useForm<CreateUserRequest>();
  const [editForm] = Form.useForm<UpdateUserRequest>();

  const onCreate = async (values: CreateUserRequest) => {
    setError(null);
    try {
      await createUser.mutateAsync(values);
      setAddOpen(false);
      addForm.resetFields();
    } catch (err) {
      setError(errorMessage(err, 'Could not create user'));
    }
  };

  const openEdit = (u: UserView) => {
    setError(null);
    setEditing(u);
    editForm.setFieldsValue({
      firstName: u.firstName,
      lastName: u.lastName,
      role: u.roles[0] ?? 'ANALYST',
      status: u.status,
    });
  };

  const onEdit = async (values: UpdateUserRequest) => {
    if (!editing) return;
    setError(null);
    try {
      await updateUser.mutateAsync({ id: editing.id, body: values });
      setEditing(null);
    } catch (err) {
      setError(errorMessage(err, 'Could not update user'));
    }
  };

  // Quick enable/disable without opening the edit dialog — keeps the user's current name + role.
  const toggleStatus = (u: UserView) => {
    setError(null);
    const next = u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    updateUser.mutate(
      { id: u.id, body: { firstName: u.firstName, lastName: u.lastName, role: u.roles[0] ?? 'ANALYST', status: next } },
      { onError: (err) => setError(errorMessage(err, `Could not ${next === 'ACTIVE' ? 'enable' : 'disable'} user`)) },
    );
  };

  const onDelete = (u: UserView) => {
    setError(null);
    deleteUser.mutate(u.id, {
      onError: (err) => setError(errorMessage(err, 'Could not delete user')),
    });
  };

  const columns: ColumnsType<UserView> = [
    { title: 'Email', dataIndex: 'email', key: 'email' },
    { title: 'Name', key: 'name', render: (_, u) => `${u.firstName} ${u.lastName}` },
    {
      title: 'Roles',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles: string[]) => (
        <Space wrap>
          {roles.map((r) => (
            <Tag key={r} color="blue">{r}</Tag>
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
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', width: 180, render: formatTimestamp },
    {
      title: 'Actions',
      key: 'actions',
      width: 260,
      render: (_, u) => (
        <Space size="small">
          <Button size="small" onClick={() => openEdit(u)}>Edit</Button>
          <Button size="small" onClick={() => toggleStatus(u)} loading={updateUser.isPending}>
            {u.status === 'ACTIVE' ? 'Disable' : 'Enable'}
          </Button>
          <Popconfirm
            title="Delete this user?"
            description="Hard delete. If they authored/reviewed reports, disable them instead."
            okText="Delete"
            okButtonProps={{ danger: true }}
            onConfirm={() => onDelete(u)}
          >
            <Button size="small" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Users"
      style={{ marginBottom: 16 }}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setError(null); setAddOpen(true); }}>
          Add user
        </Button>
      }
    >
      {error && <Alert type="error" showIcon closable message={error} style={{ marginBottom: 16 }} onClose={() => setError(null)} />}
      <Table<UserView>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={users.data ?? []}
        loading={users.isLoading}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        locale={{ emptyText: 'No users' }}
      />

      {/* Add user */}
      <Modal
        title="Add user"
        open={addOpen}
        okText="Create"
        onOk={() => addForm.submit()}
        confirmLoading={createUser.isPending}
        onCancel={() => setAddOpen(false)}
        destroyOnHidden
      >
        <Form form={addForm} name="addUser" layout="vertical" onFinish={onCreate} preserve={false}>
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
            <Select placeholder="Select a role" options={ASSIGNABLE_ROLES.map((r) => ({ value: r, label: r }))} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit user — email is the login identity and cannot change. */}
      <Modal
        title={editing ? `Edit ${editing.email}` : 'Edit user'}
        open={!!editing}
        okText="Save"
        onOk={() => editForm.submit()}
        confirmLoading={updateUser.isPending}
        onCancel={() => setEditing(null)}
        destroyOnHidden
      >
        <Form form={editForm} name="editUser" layout="vertical" onFinish={onEdit} preserve={false}>
          <Form.Item label="First name" name="firstName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Last name" name="lastName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Role" name="role" rules={[{ required: true }]}>
            <Select options={ASSIGNABLE_ROLES.map((r) => ({ value: r, label: r }))} />
          </Form.Item>
          <Form.Item
            label="Status"
            name="status"
            rules={[{ required: true }]}
            tooltip="A disabled user is kept for audit but cannot log in."
          >
            <Select
              options={[
                { value: 'ACTIVE', label: 'Active' },
                { value: 'DISABLED', label: 'Disabled' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
