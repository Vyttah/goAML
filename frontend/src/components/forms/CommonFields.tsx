import { Col, Form, Input, Row } from 'antd';
import { CodeSelect } from '../lookups/CodeSelect';

type NamePath = (string | number)[];

/** Phone sub-form (all optional). `name` is the path prefix to the phone object. */
export function PhoneFields({ name }: { name: NamePath }) {
  return (
    <Row gutter={12}>
      <Col span={6}>
        <Form.Item label="Contact type" name={[...name, 'contactType']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={6}>
        <Form.Item label="Comm. type" name={[...name, 'communicationType']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={6}>
        <Form.Item label="Country prefix" name={[...name, 'countryPrefix']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={6}>
        <Form.Item label="Number" name={[...name, 'number']}>
          <Input />
        </Form.Item>
      </Col>
    </Row>
  );
}

/** Address sub-form (all optional). `name` is the path prefix to the address object. */
export function AddressFields({ name }: { name: NamePath }) {
  return (
    <Row gutter={12}>
      <Col span={8}>
        <Form.Item label="Address type" name={[...name, 'addressType']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={16}>
        <Form.Item label="Address" name={[...name, 'address']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="City" name={[...name, 'city']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="Country" name={[...name, 'countryCode']}>
          <CodeSelect set="countries" placeholder="Country" />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="State" name={[...name, 'state']}>
          <Input />
        </Form.Item>
      </Col>
    </Row>
  );
}
