import { Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Link } from 'react-router-dom';
import { StatusTag } from '../StatusTag';
import type { ImportRowResult } from '../../types';

/** Per-row outcome table for an import job (reused by the upload result panel + history expansion). */
export function ImportResultsTable({ results }: { results: ImportRowResult[] }) {
  const columns: ColumnsType<ImportRowResult> = [
    { title: 'Row', dataIndex: 'row', key: 'row', width: 70 },
    {
      title: 'Reference',
      dataIndex: 'entityReference',
      key: 'entityReference',
      render: (ref: string | null) => ref || '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (s: string) => <StatusTag status={s} />,
    },
    {
      title: 'Report',
      dataIndex: 'reportId',
      key: 'reportId',
      width: 90,
      render: (reportId: string | null) =>
        reportId ? <Link to={`/reports/${reportId}`}>open</Link> : '—',
    },
    {
      title: 'Messages',
      dataIndex: 'errors',
      key: 'errors',
      render: (errors: string[]) =>
        errors.length ? (
          <Typography.Text type="secondary">{errors.join('; ')}</Typography.Text>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <Table<ImportRowResult>
      rowKey="row"
      size="small"
      columns={columns}
      dataSource={results}
      pagination={false}
      locale={{ emptyText: 'No rows' }}
    />
  );
}
