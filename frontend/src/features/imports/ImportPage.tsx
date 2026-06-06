import { useState } from 'react';
import { Alert, Card, Segmented, Space, Table, Tag, Typography, Upload } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { ImportResultsTable } from '../../components/imports/ImportResultsTable';
import { useImport, useImports } from './useImports';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ROLES } from '../../auth/roles';
import type { ImportJobView } from '../../types';

const JOB_COLORS: Record<string, string> = {
  COMPLETED: 'success',
  PARTIAL: 'warning',
  FAILED: 'error',
};

function JobStatusTag({ status }: { status: string }) {
  return <Tag color={JOB_COLORS[status] ?? 'default'}>{status}</Tag>;
}

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * File import: upload a goAML XML or DPMSR CSV → render the resulting job's per-row outcomes, with a
 * history of past jobs (expandable to their results). Whole-file rejections (400) surface as an error.
 */
export function ImportPage() {
  const { can } = useAuth();
  const canImport = can(ROLES.ANALYST, ROLES.MLRO);
  const [format, setFormat] = useState<'csv' | 'xml'>('csv');
  const importMutation = useImport(format);
  const historyQuery = useImports();
  const [lastJob, setLastJob] = useState<ImportJobView | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const uploadProps: UploadProps = {
    showUploadList: false,
    multiple: false,
    accept: format === 'csv' ? '.csv,text/csv' : '.xml,text/xml,application/xml',
    customRequest: ({ file, onSuccess, onError }) => {
      setUploadError(null);
      importMutation
        .mutateAsync(file as File)
        .then((job) => {
          setLastJob(job);
          onSuccess?.(job);
        })
        .catch((err) => {
          setUploadError(errorMessage(err, 'Import failed'));
          onError?.(err as Error);
        });
    },
  };

  const historyColumns: ColumnsType<ImportJobView> = [
    { title: 'File', dataIndex: 'filename', key: 'filename' },
    { title: 'Source', dataIndex: 'sourceType', key: 'sourceType', width: 130 },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (s: string) => <JobStatusTag status={s} />,
    },
    { title: 'Rows', dataIndex: 'totalRows', key: 'totalRows', width: 80 },
    { title: 'OK', dataIndex: 'succeeded', key: 'succeeded', width: 70 },
    { title: 'Failed', dataIndex: 'failed', key: 'failed', width: 80 },
    { title: 'When', dataIndex: 'createdAt', key: 'createdAt', width: 200, render: formatTimestamp },
  ];

  return (
    <>
      <Typography.Title level={3}>Import</Typography.Title>

      {canImport && (
        <Card style={{ marginBottom: 16 }}>
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Segmented
              value={format}
              onChange={(v) => setFormat(v as 'csv' | 'xml')}
              options={[
                { label: 'DPMSR CSV', value: 'csv' },
                { label: 'goAML XML', value: 'xml' },
              ]}
            />
            <Upload.Dragger {...uploadProps} disabled={importMutation.isPending}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">
                Click or drag a {format.toUpperCase()} file to import
              </p>
              <p className="ant-upload-hint">
                {format === 'csv'
                  ? 'Flat DPMSR CSV — one report per row.'
                  : 'A goAML XML report (re-validated on import).'}
              </p>
            </Upload.Dragger>
            {uploadError && <Alert type="error" showIcon message={uploadError} />}
          </Space>
        </Card>
      )}

      {lastJob && (
        <Card
          title={
            <Space>
              Import result <JobStatusTag status={lastJob.status} />
            </Space>
          }
          style={{ marginBottom: 16 }}
        >
          <Typography.Paragraph>
            <strong>{lastJob.filename}</strong> — {lastJob.succeeded} succeeded, {lastJob.failed} failed
            of {lastJob.totalRows} row{lastJob.totalRows === 1 ? '' : 's'}.
          </Typography.Paragraph>
          <ImportResultsTable results={lastJob.results} />
        </Card>
      )}

      <Card title="Import history">
        <Table<ImportJobView>
          rowKey="id"
          size="small"
          columns={historyColumns}
          dataSource={historyQuery.data ?? []}
          loading={historyQuery.isLoading}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: 'No imports yet' }}
          expandable={{
            expandedRowRender: (job) => <ImportResultsTable results={job.results} />,
            rowExpandable: (job) => job.results.length > 0,
          }}
        />
      </Card>
    </>
  );
}
