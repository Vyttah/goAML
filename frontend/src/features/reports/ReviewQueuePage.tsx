import { useState } from 'react';
import { Alert, Button, Card, Input, Modal, Popconfirm, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { StatusTag } from '../../components/StatusTag';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ROLES } from '../../auth/roles';
import { useApproveReport, useRejectReport, useReviewQueue } from './useReviewQueue';
import type { ReportView } from '../../types';

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * Phase D.2 review queue: reports awaiting MLRO review (PENDING_REVIEW). An MLRO can approve (→ APPROVED,
 * then submittable) or reject (→ VALID, with a required remark). Visible to MLRO + TENANT_ADMIN; the
 * decision actions are MLRO-only (the backend enforces this too).
 */
export function ReviewQueuePage() {
  const { can } = useAuth();
  const queue = useReviewQueue();
  const approve = useApproveReport();
  const reject = useRejectReport();

  const isMlro = can(ROLES.MLRO);
  const [actionError, setActionError] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<ReportView | null>(null);
  const [rejectRemark, setRejectRemark] = useState('');

  const onApprove = async (row: ReportView) => {
    setActionError(null);
    try {
      await approve.mutateAsync({ id: row.id });
    } catch (err) {
      setActionError(errorMessage(err, 'Could not approve the report'));
    }
  };

  const onReject = async () => {
    if (!rejecting || !rejectRemark.trim()) return;
    setActionError(null);
    try {
      await reject.mutateAsync({ id: rejecting.id, remark: rejectRemark.trim() });
      setRejecting(null);
      setRejectRemark('');
    } catch (err) {
      setActionError(errorMessage(err, 'Could not reject the report'));
    }
  };

  const columns: ColumnsType<ReportView> = [
    {
      title: 'Reference',
      dataIndex: 'entityReference',
      key: 'entityReference',
      render: (ref: string, row) => <Link to={`/reports/${row.id}`}>{ref}</Link>,
    },
    { title: 'Type', dataIndex: 'reportCode', key: 'reportCode', width: 110 },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 160,
      render: (status: string) => <StatusTag status={status} />,
    },
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', width: 200, render: formatTimestamp },
    ...(isMlro
      ? [
          {
            title: '',
            key: 'actions',
            width: 200,
            render: (_: unknown, row: ReportView) => (
              <Space>
                <Popconfirm
                  title="Approve this report?"
                  description="It becomes APPROVED and can then be submitted to the FIU."
                  okText="Yes, approve"
                  onConfirm={() => onApprove(row)}
                >
                  <Button type="primary" size="small" icon={<CheckOutlined />}>
                    Approve
                  </Button>
                </Popconfirm>
                <Button
                  danger
                  size="small"
                  icon={<CloseOutlined />}
                  onClick={() => {
                    setRejectRemark('');
                    setRejecting(row);
                  }}
                >
                  Reject
                </Button>
              </Space>
            ),
          } as ColumnsType<ReportView>[number],
        ]
      : []),
  ];

  return (
    <>
      <Typography.Title level={3} style={{ marginTop: 0 }}>
        Review queue
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        Reports awaiting MLRO review before they can be submitted to the FIU.
      </Typography.Paragraph>

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

      <Card>
        <Table<ReportView>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={queue.data ?? []}
          loading={queue.isLoading}
          pagination={false}
          locale={{ emptyText: 'Nothing awaiting review' }}
        />
      </Card>

      <Modal
        title={`Reject ${rejecting?.entityReference ?? ''}`}
        open={!!rejecting}
        onCancel={() => setRejecting(null)}
        okText="Reject report"
        okButtonProps={{ danger: true, disabled: !rejectRemark.trim(), loading: reject.isPending }}
        onOk={onReject}
      >
        <Typography.Paragraph type="secondary">
          The report returns to VALID so it can be corrected and resubmitted. A reason is required.
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          value={rejectRemark}
          onChange={(e) => setRejectRemark(e.target.value)}
          placeholder="Reason for rejection"
          aria-label="Rejection reason"
        />
      </Modal>
    </>
  );
}
