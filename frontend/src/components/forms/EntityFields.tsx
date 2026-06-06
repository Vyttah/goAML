import { Button, Card, Col, DatePicker, Divider, Form, Input, Row, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { PhoneFields } from './CommonFields';
import { CodeSelect } from '../lookups/CodeSelect';

type NamePath = (string | number)[];

/**
 * Entity (legal-person) sub-form: identity + incorporation, phone, and a dynamic list of directors
 * (each a mini-person). `name` is the path prefix to the entity object (e.g. `[partyIndex, 'entity']`).
 */
export function EntityFields({ name }: { name: NamePath }) {
  return (
    <>
      <Row gutter={12}>
        <Col span={12}>
          <Form.Item label="Name" name={[...name, 'name']} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="Commercial name" name={[...name, 'commercialName']}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="Incorporation no." name={[...name, 'incorporationNumber']}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="Incorporation state" name={[...name, 'incorporationState']}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="Incorporation country" name={[...name, 'incorporationCountryCode']}>
            <CodeSelect set="countries" placeholder="Country" />
          </Form.Item>
        </Col>
      </Row>

      <Typography.Text type="secondary">Phone</Typography.Text>
      <PhoneFields name={[...name, 'phone']} />

      <Divider orientation="left" plain>
        Directors
      </Divider>
      <Form.List name={[...name, 'directors']}>
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <Card
                size="small"
                key={field.key}
                style={{ marginBottom: 12 }}
                title={`Director ${field.name + 1}`}
                extra={
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => remove(field.name)}
                    aria-label="Remove director"
                  />
                }
              >
                <Row gutter={12}>
                  <Col span={6}>
                    <Form.Item label="Gender" name={[field.name, 'gender']}>
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col span={9}>
                    <Form.Item
                      label="First name"
                      name={[field.name, 'firstName']}
                      rules={[{ required: true }]}
                    >
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col span={9}>
                    <Form.Item
                      label="Last name"
                      name={[field.name, 'lastName']}
                      rules={[{ required: true }]}
                    >
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item label="Birthdate" name={[field.name, 'birthdate']}>
                      <DatePicker style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item label="Role" name={[field.name, 'role']}>
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item label="Nationality" name={[field.name, 'nationality']}>
                      <CodeSelect set="countries" />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item label="Residence" name={[field.name, 'residence']}>
                      <CodeSelect set="countries" />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item label="ID number" name={[field.name, 'idNumber']}>
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item label="Passport no." name={[field.name, 'passportNumber']}>
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item label="Passport country" name={[field.name, 'passportCountry']}>
                      <CodeSelect set="countries" />
                    </Form.Item>
                  </Col>
                </Row>
                <Typography.Text type="secondary">Phone</Typography.Text>
                <PhoneFields name={[field.name, 'phone']} />
              </Card>
            ))}
            <Button type="dashed" onClick={() => add()} icon={<PlusOutlined />} block>
              Add director
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}
