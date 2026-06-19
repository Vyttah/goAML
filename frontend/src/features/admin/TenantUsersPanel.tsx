import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import {
  useCreateTenantUser,
  useDeleteTenantUser,
  useResetTenantUserPassword,
  useTenantUsers,
  useTenants,
  useUpdateTenantUser,
} from './useAdmin';
import { errorMessage } from '../../api/client';
import type { CreateUserRequest, ResetPasswordRequest, UpdateUserRequest, UserView } from '../../types';

const ASSIGNABLE_ROLES = ['ANALYST', 'MLRO', 'TENANT_ADMIN'];

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * SUPER_ADMIN cross-tenant user management: pick any tenant, then create users, edit (name/role), enable/
 * disable, delete, or reset a password. Reuses the same tenant-scoped backend ops the TENANT_ADMIN uses,
 * but targets a tenant by id — so the platform operator can onboard a client's whole team (incl. giving a
 * federated/SSO user a direct goAML password).
 */
export function TenantUsersPanel() {
  const tenants = useTenants();
  const [tenantId, setTenantId] = useState<string | undefined>(undefined);
  const users = useTenantUsers(tenantId);
  const createUser = useCreateTenantUser();
  const updateUser = useUpdateTenantUser();
  const deleteUser = useDeleteTenantUser();
  const resetPassword = useResetTenantUserPassword();

  const [addOpen, setAddOpen] = useState(false);
  const [editing, setEditing] = useState<UserView | null>(null);
  const [resetting, setResetting] = useState<UserView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [addForm] = Form.useForm<CreateUserRequest>();
  const [editForm] = Form.useForm<UpdateUserRequest>();
  const [resetForm] = Form.useForm<ResetPasswordRequest>();

  const onCreate = async (values: CreateUserRequest) => {
    if (!tenantId) return;
    setError(null);
    try {
      await createUser.mutateAsync({ tenantId, body: values });
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
    if (!editing || !tenantId) return;
    setError(null);
    try {
      await updateUser.mutateAsync({ tenantId, userId: editing.id, body: values });
      setEditing(null);
    } catch (err) {
      setError(errorMessage(err, 'Could not update user'));
    }
  };

  const onReset = async (values: ResetPasswordRequest) => {
    if (!resetting || !tenantId) return;
    setError(null);
    try {
      await resetPassword.mutateAsync({ tenantId, userId: resetting.id, password: values.password });
      setResetting(null);
      resetForm.resetFields();
    } catch (err) {
      setError(errorMessage(err, 'Could not reset password'));
    }
  };

  const toggleStatus = (u: UserView) => {
    if (!tenantId) return;
    setError(null);
    const next = u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    updateUser.mutate(
      { tenantId, userId: u.id, body: { firstName: u.firstName, lastName: u.lastName, role: u.roles[0] ?? 'ANALYST', status: next } },
      { onError: (err) => setError(errorMessage(err, `Could not ${next === 'ACTIVE' ? 'enable' : 'disable'} user`)) },
    );
  };

  const onDelete = (u: UserView) => {
    if (!tenantId) return;
    setError(null);
    deleteUser.mutate({ tenantId, userId: u.id }, {
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
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatTimestamp },
    {
      title: 'Actions',
      key: 'actions',
      width: 340,
      render: (_, u) => (
        <Space size="small">
          <Button size="small" onClick={() => openEdit(u)}>Edit</Button>
          <Button size="small" onClick={() => toggleStatus(u)} loading={updateUser.isPending}>
            {u.status === 'ACTIVE' ? 'Disable' : 'Enable'}
          </Button>
          <Button size="small" onClick={() => { setError(null); setResetting(u); }}>Reset password</Button>
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
      title="Tenant Users"
      style={{ marginBottom: 16 }}
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          disabled={!tenantId}
          onClick={() => { setError(null); setAddOpen(true); }}
        >
          Add user
        </Button>
      }
    >
      <Space style={{ marginBottom: 16 }}>
        <span>Tenant:</span>
        <Select
          style={{ minWidth: 280 }}
          loading={tenants.isLoading}
          value={tenantId}
          placeholder="Select a tenant to manage its users"
          onChange={(v) => { setError(null); setTenantId(v); }}
          options={(tenants.data ?? []).map((t) => ({ value: t.id, label: `${t.name} (${t.slug})` }))}
        />
      </Space>

      {error && <Alert type="error" showIcon closable message={error} style={{ marginBottom: 16 }} onClose={() => setError(null)} />}

      {tenantId ? (
        <Table<UserView>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={users.data ?? []}
          loading={users.isLoading}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: 'No users in this tenant' }}
        />
      ) : (
        <Alert type="info" showIcon message="Pick a tenant above to view and manage its users." />
      )}

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
        <Form form={addForm} name="addTenantUser" layout="vertical" onFinish={onCreate} preserve={false}>
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
        <Form form={editForm} name="editTenantUser" layout="vertical" onFinish={onEdit} preserve={false}>
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

      {/* Reset password */}
      <Modal
        title={resetting ? `Reset password — ${resetting.email}` : 'Reset password'}
        open={!!resetting}
        okText="Set password"
        onOk={() => resetForm.submit()}
        confirmLoading={resetPassword.isPending}
        onCancel={() => setResetting(null)}
        destroyOnHidden
      >
        <Form form={resetForm} name="resetTenantUserPassword" layout="vertical" onFinish={onReset} preserve={false}>
          <Form.Item
            label="New password"
            name="password"
            rules={[{ required: true }]}
            extra="The user can then log into the goAML SPA directly with this password."
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
