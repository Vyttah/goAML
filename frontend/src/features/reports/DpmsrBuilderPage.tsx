import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  Radio,
  Row,
  Select,
  Space,
  Typography,
} from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { PersonFields } from '../../components/forms/PersonFields';
import { EntityFields } from '../../components/forms/EntityFields';
import { GoodsFields } from '../../components/forms/GoodsFields';
import { AddressFields } from '../../components/forms/CommonFields';
import { ValidationMessages } from '../../components/ValidationMessages';
import { StatusTag } from '../../components/StatusTag';
import { useCreateReport } from './useCreateReport';
import { buildDpmsrPayload } from '../../lib/dpmsrForm';
import { validateDpmsr } from '../../lib/dpmsrSchema';
import { errorMessage } from '../../api/client';
import type { CreateReportResponse } from '../../types';
import type { DpmsrCreateRequest } from '../../types/dpmsr';

const INITIAL_VALUES = {
  submissionDate: dayjs(),
  reportingPerson: {},
  parties: [{ _type: 'person', person: {} }],
  goods: [{ currencyCode: 'AED' }],
};

/**
 * DPMSR report builder — a guided form mirroring `DpmsrCreateRequest`. On submit it normalizes the
 * values (dates → ISO, party person/entity branch, prune empties), checks the Zod mirror, then POSTs.
 * The server's validation result (status + messages) is rendered inline; the backend stays authoritative.
 */
export function DpmsrBuilderPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const createReport = useCreateReport();
  const [result, setResult] = useState<CreateReportResponse | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [zodIssues, setZodIssues] = useState<{ path: string; message: string }[]>([]);

  const onFinish = async (values: Record<string, unknown>) => {
    setSubmitError(null);
    setZodIssues([]);
    setResult(null);

    const payload = buildDpmsrPayload(values);
    const check = validateDpmsr(payload);
    if (!check.ok) {
      setZodIssues(check.issues);
      return;
    }

    try {
      const response = await createReport.mutateAsync(payload as DpmsrCreateRequest);
      setResult(response);
    } catch (err) {
      setSubmitError(errorMessage(err, 'Failed to create report'));
    }
  };

  return (
    <>
      <Typography.Title level={3}>New DPMSR report</Typography.Title>
      <Typography.Paragraph type="secondary">
        Dealers in precious metals &amp; stones report. The platform applies the fixed header
        (report code DPMSR, currency AED) and your entity ID automatically.
      </Typography.Paragraph>

      <Form form={form} layout="vertical" initialValues={INITIAL_VALUES} onFinish={onFinish}>
        <Card title="Report header" style={{ marginBottom: 16 }}>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item
                label="Entity reference"
                name="entityReference"
                rules={[{ required: true }]}
                tooltip="Your unique reference for this filing"
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="Submission date" name="submissionDate" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="Branch" name="rentityBranch">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="FIU ref. number" name="fiuRefNumber">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="Reason" name="reason">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="Action" name="action">
                <Input />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="Indicators" name="indicators" tooltip="Type and press enter to add">
                <Select mode="tags" tokenSeparators={[',']} placeholder="Add indicators" />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card title="Reporting person (MLRO)" style={{ marginBottom: 16 }}>
          <PersonFields name={['reportingPerson']} />
        </Card>

        <Card title="Location" style={{ marginBottom: 16 }}>
          <AddressFields name={['location']} />
        </Card>

        <Card title="Parties" style={{ marginBottom: 16 }}>
          <Form.List name="parties">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Card
                    key={field.key}
                    type="inner"
                    title={`Party ${field.name + 1}`}
                    style={{ marginBottom: 16 }}
                    extra={
                      fields.length > 1 && (
                        <Button
                          type="text"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                          aria-label="Remove party"
                        />
                      )
                    }
                  >
                    <Row gutter={12}>
                      <Col span={8}>
                        <Form.Item
                          label="Party type"
                          name={[field.name, '_type']}
                          initialValue="person"
                        >
                          <Radio.Group>
                            <Radio value="person">Person</Radio>
                            <Radio value="entity">Entity</Radio>
                          </Radio.Group>
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item label="Reason" name={[field.name, 'reason']}>
                          <Input />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item label="Comments" name={[field.name, 'comments']}>
                          <Input />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Divider style={{ margin: '8px 0 16px' }} />
                    <Form.Item noStyle shouldUpdate>
                      {() =>
                        form.getFieldValue(['parties', field.name, '_type']) === 'entity' ? (
                          <EntityFields name={[field.name, 'entity']} />
                        ) : (
                          <PersonFields name={[field.name, 'person']} />
                        )
                      }
                    </Form.Item>
                  </Card>
                ))}
                <Button
                  type="dashed"
                  onClick={() => add({ _type: 'person', person: {} })}
                  icon={<PlusOutlined />}
                  block
                >
                  Add party
                </Button>
              </>
            )}
          </Form.List>
        </Card>

        <Card title="Goods" style={{ marginBottom: 16 }}>
          <Form.List name="goods">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Card
                    key={field.key}
                    type="inner"
                    title={`Item ${field.name + 1}`}
                    style={{ marginBottom: 16 }}
                    extra={
                      fields.length > 1 && (
                        <Button
                          type="text"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                          aria-label="Remove goods item"
                        />
                      )
                    }
                  >
                    <GoodsFields name={field.name} />
                  </Card>
                ))}
                <Button
                  type="dashed"
                  onClick={() => add({ currencyCode: 'AED' })}
                  icon={<PlusOutlined />}
                  block
                >
                  Add goods item
                </Button>
              </>
            )}
          </Form.List>
        </Card>

        {zodIssues.length > 0 && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            message="Please fix the highlighted fields"
            description={
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {zodIssues.map((i) => (
                  <li key={i.path}>
                    <code>{i.path}</code> — {i.message}
                  </li>
                ))}
              </ul>
            }
          />
        )}

        {submitError && (
          <Alert type="error" showIcon style={{ marginBottom: 16 }} message={submitError} />
        )}

        <Space>
          <Button type="primary" htmlType="submit" loading={createReport.isPending}>
            Create &amp; validate
          </Button>
          <Button onClick={() => navigate('/dashboard')}>Cancel</Button>
        </Space>
      </Form>

      {result && (
        <Card style={{ marginTop: 24 }} title={<>Report created <StatusTag status={result.status} /></>}>
          <ValidationMessages messages={result.validationMessages} />
          <Space style={{ marginTop: 16 }}>
            <Button type="primary" onClick={() => navigate(`/reports/${result.reportId}`)}>
              View report
            </Button>
            <Button onClick={() => navigate('/dashboard')}>Back to dashboard</Button>
          </Space>
        </Card>
      )}
    </>
  );
}
