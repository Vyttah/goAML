import { useMemo, useState } from 'react';
import { Alert, Button, Empty, Input, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useReports } from './useReports';
import { StatusTag } from '../../components/StatusTag';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ROLES } from '../../auth/roles';
import { REPORT_STATUSES, type ReportView } from '../../types';

const ALL = 'ALL';

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/** Report list: status chips, status filter + reference search, row → detail, and (for authors) "New report". */
export function DashboardPage() {
  const { data, isLoading, isError, error, refetch, isFetching } = useReports();
  const { can } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState<string>(ALL);
  const [search, setSearch] = useState('');

  const rows = useMemo(() => {
    const all = data ?? [];
    const term = search.trim().toLowerCase();
    return all.filter((r) => {
      const statusOk = status === ALL || r.status === status;
      const termOk =
        term === '' ||
        r.entityReference.toLowerCase().includes(term) ||
        r.reportCode.toLowerCase().includes(term);
      return statusOk && termOk;
    });
  }, [data, status, search]);

  const columns: ColumnsType<ReportView> = [
    { title: 'Reference', dataIndex: 'entityReference', key: 'entityReference' },
    { title: 'Type', dataIndex: 'reportCode', key: 'reportCode', width: 100 },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (s: string) => <StatusTag status={s} />,
    },
    { title: 'Entity ID', dataIndex: 'rentityId', key: 'rentityId', width: 120 },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 200,
      render: formatTimestamp,
    },
  ];

  return (
    <>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Reports
        </Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => refetch()} loading={isFetching}>
            Refresh
          </Button>
          {can(ROLES.ANALYST, ROLES.MLRO) && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/reports/new')}>
              New report
            </Button>
          )}
        </Space>
      </Space>

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Search reference or type"
          allowClear
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: 280 }}
          aria-label="Search reports"
        />
        <Select
          value={status}
          onChange={setStatus}
          style={{ width: 180 }}
          aria-label="Filter by status"
          options={[
            { value: ALL, label: 'All statuses' },
            ...REPORT_STATUSES.map((s) => ({ value: s, label: s })),
          ]}
        />
      </Space>

      {isError ? (
        <Alert
          type="error"
          showIcon
          message="Couldn't load reports"
          description={errorMessage(error)}
          action={
            <Button size="small" onClick={() => refetch()}>
              Retry
            </Button>
          }
        />
      ) : (
        <Table<ReportView>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={isLoading}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="No reports yet" /> }}
          onRow={(record) => ({
            onClick: () => navigate(`/reports/${record.id}`),
            style: { cursor: 'pointer' },
          })}
        />
      )}
    </>
  );
}
