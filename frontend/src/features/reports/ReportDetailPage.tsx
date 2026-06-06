import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Popconfirm,
  Result,
  Skeleton,
  Space,
  Table,
  Typography,
  Upload,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import { DeleteOutlined, ReloadOutlined, SendOutlined, UploadOutlined } from '@ant-design/icons';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { StatusTag } from '../../components/StatusTag';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ROLES } from '../../auth/roles';
import {
  useAddAttachment,
  useAttachments,
  useCheckStatus,
  useRemoveAttachment,
  useReport,
  useSubmitReport,
} from './useReportDetail';
import type { AttachmentView, StatusView } from '../../types';

const SUBMITTED_STATUSES = ['SUBMITTED', 'ACCEPTED', 'REJECTED', 'FAILED'];

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toFixed(1)} KB`;
}

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * Report detail + lifecycle: summary, MLRO submit (VALID-only, confirmed), on-demand FIU status, and
 * attachment management. Note: there is no backend endpoint for the report XML or for re-fetching the
 * validation result of an existing report, so those panels aren't shown here (small future backend adds).
 */
export function ReportDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { can } = useAuth();

  const reportQuery = useReport(id);
  const submit = useSubmitReport(id);
  const checkStatus = useCheckStatus(id);
  const attachmentsQuery = useAttachments(id);
  const addAttachment = useAddAttachment(id);
  const removeAttachment = useRemoveAttachment(id);

  const [actionError, setActionError] = useState<string | null>(null);
  const [fiuStatus, setFiuStatus] = useState<StatusView | null>(null);

  if (reportQuery.isLoading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }
  if (reportQuery.isError || !reportQuery.data) {
    return (
      <Result
        status="404"
        title="Report not found"
        extra={
          <Button type="primary" onClick={() => navigate('/dashboard')}>
            Back to dashboard
          </Button>
        }
      />
    );
  }

  const report = reportQuery.data;
  const canSubmit = report.status === 'VALID' && can(ROLES.MLRO);
  const canEditAttachments = can(ROLES.ANALYST, ROLES.MLRO);
  const hasSubmission = SUBMITTED_STATUSES.includes(report.status);

  const onSubmit = async () => {
    setActionError(null);
    try {
      await submit.mutateAsync();
    } catch (err) {
      setActionError(errorMessage(err, 'Submission failed'));
    }
  };

  const onCheckStatus = async () => {
    setActionError(null);
    setFiuStatus(null);
    try {
      setFiuStatus(await checkStatus.mutateAsync());
    } catch (err) {
      setActionError(errorMessage(err, 'Could not fetch FIU status'));
    }
  };

  const uploadProps: UploadProps = {
    showUploadList: false,
    multiple: false,
    customRequest: ({ file, onSuccess, onError }) => {
      addAttachment
        .mutateAsync(file as File)
        .then((res) => onSuccess?.(res))
        .catch((err) => {
          setActionError(errorMessage(err, 'Upload failed'));
          onError?.(err as Error);
        });
    },
  };

  const attachmentColumns: ColumnsType<AttachmentView> = [
    { title: 'File', dataIndex: 'filename', key: 'filename' },
    { title: 'Type', dataIndex: 'contentType', key: 'contentType', width: 200 },
    {
      title: 'Size',
      dataIndex: 'sizeBytes',
      key: 'sizeBytes',
      width: 110,
      render: formatBytes,
    },
    { title: 'Added', dataIndex: 'createdAt', key: 'createdAt', width: 200, render: formatTimestamp },
    ...(canEditAttachments
      ? [
          {
            title: '',
            key: 'actions',
            width: 100,
            render: (_: unknown, row: AttachmentView) => (
              <Popconfirm
                title="Remove this attachment?"
                okText="Remove"
                onConfirm={() => removeAttachment.mutate(row.id)}
              >
                <Button type="text" danger icon={<DeleteOutlined />} aria-label="Remove attachment" />
              </Popconfirm>
            ),
          },
        ]
      : []),
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Link to="/dashboard">← Reports</Link>
      </Space>

      <Card
        title={
          <Space>
            {report.entityReference}
            <StatusTag status={report.status} />
          </Space>
        }
        style={{ marginBottom: 16 }}
      >
        <Descriptions column={2} size="small">
          <Descriptions.Item label="Report type">{report.reportCode}</Descriptions.Item>
          <Descriptions.Item label="Entity ID">{report.rentityId ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Created">{formatTimestamp(report.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="Report ID">
            <Typography.Text code copyable>
              {report.id}
            </Typography.Text>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Lifecycle" style={{ marginBottom: 16 }}>
        {actionError && (
          <Alert
            type="error"
            showIcon
            message={actionError}
            closable
            onClose={() => setActionError(null)}
            style={{ marginBottom: 16 }}
          />
        )}
        <Space wrap>
          {can(ROLES.MLRO) && (
            <Popconfirm
              title="Submit this report to the FIU?"
              description="This files the report with the UAE FIU and can't be undone."
              okText="Submit"
              onConfirm={onSubmit}
              disabled={!canSubmit}
            >
              <Button
                type="primary"
                icon={<SendOutlined />}
                loading={submit.isPending}
                disabled={!canSubmit}
              >
                Submit to FIU
              </Button>
            </Popconfirm>
          )}
          {hasSubmission && (
            <Button
              icon={<ReloadOutlined />}
              onClick={onCheckStatus}
              loading={checkStatus.isPending}
            >
              Check FIU status
            </Button>
          )}
        </Space>

        {!canSubmit && report.status !== 'VALID' && can(ROLES.MLRO) && (
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
            Only a <strong>VALID</strong> report can be submitted (this one is {report.status}).
          </Typography.Paragraph>
        )}

        {fiuStatus && (
          <Descriptions column={1} size="small" style={{ marginTop: 16 }} bordered title="FIU status">
            <Descriptions.Item label="Report key">{fiuStatus.reportKey ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Status">
              <StatusTag status={fiuStatus.status} />
            </Descriptions.Item>
            <Descriptions.Item label="Errors">{fiuStatus.errors || '—'}</Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      <Card
        title="Attachments"
        style={{ marginBottom: 16 }}
        extra={
          canEditAttachments && (
            <Upload {...uploadProps}>
              <Button icon={<UploadOutlined />} loading={addAttachment.isPending}>
                Upload
              </Button>
            </Upload>
          )
        }
      >
        <Table<AttachmentView>
          rowKey="id"
          size="small"
          columns={attachmentColumns}
          dataSource={attachmentsQuery.data ?? []}
          loading={attachmentsQuery.isLoading}
          pagination={false}
          locale={{ emptyText: 'No attachments' }}
        />
        <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
          Attachments are bundled into the submission ZIP. Download isn't available (no endpoint yet).
        </Typography.Paragraph>
      </Card>
    </>
  );
}
