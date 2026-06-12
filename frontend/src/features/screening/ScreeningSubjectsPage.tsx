import { useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { useScreeningSubjects, useSeedReport } from './useScreening';
import type { ScreeningSubjectView } from '../../api/screening';

interface SeedForm {
  entityReference: string;
  firstName: string;
  lastName: string;
  itemType: string;
  estimatedValue: number;
  currencyCode?: string;
  reason?: string;
}

/**
 * Screened-subjects browser (Phase 1.5c.4): lists the customers the AML screening software pushed and lets a
 * user seed a DPMSR draft from one — the subject supplies the parties; the user supplies the goods + reporting
 * MLRO + report reference the screening profile can't carry. On success it navigates to the created report.
 */
export function ScreeningSubjectsPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const subjectsQuery = useScreeningSubjects();
  const seed = useSeedReport();
  const [active, setActive] = useState<ScreeningSubjectView | null>(null);
  const [form] = Form.useForm<SeedForm>();

  const openSeed = (subject: ScreeningSubjectView) => {
    setActive(subject);
    form.resetFields();
  };

  const submit = (values: SeedForm) => {
    if (!active) return;
    seed.mutate(
      {
        subjectRef: active.subjectRef,
        body: {
          entityReference: values.entityReference,
          // Today's *local* calendar date pinned to UTC midnight — `new Date().toISOString()` would
          // file the previous day for any positive-offset (e.g. UAE +04:00) user before 04:00.
          submissionDate: `${dayjs().format('YYYY-MM-DD')}T00:00:00Z`,
          reason: values.reason,
          action: 'Filed',
          reportingPerson: { firstName: values.firstName, lastName: values.lastName },
          goods: [
            {
              itemType: values.itemType,
              estimatedValue: Number(values.estimatedValue),
              currencyCode: values.currencyCode || 'AED',
            },
          ],
        },
      },
      {
        onSuccess: (res) => {
          message.success(`Report created (${res.status})`);
          setActive(null);
          navigate(`/reports/${res.reportId}`);
        },
        onError: () => message.error('Could not seed the report'),
      },
    );
  };

  const columns = [
    { title: 'Reference', dataIndex: 'subjectRef', key: 'subjectRef' },
    { title: 'Name', dataIndex: 'displayName', key: 'displayName' },
    {
      title: 'Type',
      dataIndex: 'subjectType',
      key: 'subjectType',
      render: (t: string) => <Tag>{t}</Tag>,
    },
    {
      title: 'Sanctions',
      dataIndex: 'riskFlag',
      key: 'riskFlag',
      render: (flag: boolean) => (flag ? <Tag color="red">Risk</Tag> : <Tag color="green">Clear</Tag>),
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: unknown, row: ScreeningSubjectView) => (
        <Button type="link" onClick={() => openSeed(row)}>
          Seed report
        </Button>
      ),
    },
  ];

  return (
    <>
      <Typography.Title level={3}>Screened customers</Typography.Title>
      <Typography.Paragraph type="secondary">
        Customers pushed by the AML screening software. Seed a DPMSR draft from one — its parties carry over;
        you add the goods and the reporting MLRO.
      </Typography.Paragraph>

      <Table
        rowKey="subjectRef"
        loading={subjectsQuery.isLoading}
        dataSource={subjectsQuery.data ?? []}
        columns={columns}
        locale={{ emptyText: 'No screened customers yet' }}
      />

      <Modal
        open={active !== null}
        title={active ? `Seed report — ${active.displayName}` : 'Seed report'}
        okText="Create draft"
        confirmLoading={seed.isPending}
        onCancel={() => setActive(null)}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        {active?.sanctionsContext && (
          <Typography.Paragraph type="warning">{active.sanctionsContext}</Typography.Paragraph>
        )}
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="entityReference"
            label="Report reference"
            rules={[{ required: true, message: 'A report reference is required' }]}
          >
            <Input placeholder="e.g. SCR-RPT-1" />
          </Form.Item>
          <Space>
            <Form.Item
              name="firstName"
              label="MLRO first name"
              rules={[{ required: true, message: 'Required' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="lastName"
              label="MLRO last name"
              rules={[{ required: true, message: 'Required' }]}
            >
              <Input />
            </Form.Item>
          </Space>
          <Space align="baseline">
            <Form.Item
              name="itemType"
              label="Goods item type"
              rules={[{ required: true, message: 'Required' }]}
            >
              <Input placeholder="e.g. GOLD" />
            </Form.Item>
            <Form.Item
              name="estimatedValue"
              label="Estimated value (AED)"
              rules={[{ required: true, message: 'Required' }]}
            >
              <InputNumber min={0} style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Form.Item name="reason" label="Reason">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
