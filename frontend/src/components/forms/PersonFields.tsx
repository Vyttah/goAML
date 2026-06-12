import { Button, Col, DatePicker, Divider, Form, Input, Row, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { AddressFields, PhoneFields } from './CommonFields';
import { CodeSelect } from '../lookups/CodeSelect';

type NamePath = (string | number)[];

/**
 * Person sub-form: identity + nationality/residence (country lookups), phone, address, and a dynamic
 * list of identifications. `name` is the path prefix to the person object (e.g. `['reportingPerson']`
 * or `[partyIndex, 'person']`). `requireName` (default true) marks first/last name required — the
 * reporting-person section turns it off because the whole section is optional (the server fills the
 * tenant's configured MLRO when omitted).
 */
export function PersonFields({ name, requireName = true }: { name: NamePath; requireName?: boolean }) {
  return (
    <>
      <Row gutter={12}>
        <Col span={6}>
          <Form.Item label="Gender" name={[...name, 'gender']}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={9}>
          <Form.Item label="First name" name={[...name, 'firstName']} rules={[{ required: requireName }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={9}>
          <Form.Item label="Last name" name={[...name, 'lastName']} rules={[{ required: requireName }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="Birthdate" name={[...name, 'birthdate']}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="Nationality" name={[...name, 'nationality']}>
            <CodeSelect set="countries" placeholder="Country" />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="Residence" name={[...name, 'residence']}>
            <CodeSelect set="countries" placeholder="Country" />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="ID number" name={[...name, 'idNumber']}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="SSN / Emirates ID" name={[...name, 'ssn']}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="Passport no." name={[...name, 'passportNumber']}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="Passport country" name={[...name, 'passportCountry']}>
            <CodeSelect set="countries" placeholder="Country" />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="Tax reg. number" name={[...name, 'taxRegNumber']}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="Occupation" name={[...name, 'occupation']}>
            <Input />
          </Form.Item>
        </Col>
      </Row>

      <Typography.Text type="secondary">Phone</Typography.Text>
      <PhoneFields name={[...name, 'phone']} />

      <Typography.Text type="secondary">Address</Typography.Text>
      <AddressFields name={[...name, 'address']} />

      <Divider orientation="left" plain>
        Identifications
      </Divider>
      <Form.List name={[...name, 'identifications']}>
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <Row gutter={12} key={field.key} align="bottom">
                <Col span={5}>
                  <Form.Item label="Type" name={[field.name, 'type']}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col span={5}>
                  <Form.Item label="Number" name={[field.name, 'number']}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item label="Issue date" name={[field.name, 'issueDate']}>
                    <DatePicker style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item label="Expiry date" name={[field.name, 'expiryDate']}>
                    <DatePicker style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item label="Issue country" name={[field.name, 'issueCountry']}>
                    <CodeSelect set="countries" />
                  </Form.Item>
                </Col>
                <Col span={2}>
                  <Form.Item label=" ">
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => remove(field.name)}
                      aria-label="Remove identification"
                    />
                  </Form.Item>
                </Col>
              </Row>
            ))}
            <Button type="dashed" onClick={() => add()} icon={<PlusOutlined />} block>
              Add identification
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}
