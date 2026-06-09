import { Col, DatePicker, Form, Input, InputNumber, Row } from 'antd';
import { CodeSelect } from '../lookups/CodeSelect';

/** Goods (item) sub-form. `name` is the goods list field index. */
export function GoodsFields({ name }: { name: number }) {
  return (
    <Row gutter={12}>
      <Col span={8}>
        <Form.Item label="Item type" name={[name, 'itemType']} rules={[{ required: true }]}>
          <CodeSelect set="item_types" placeholder="Item type" />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="Make" name={[name, 'itemMake']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="Status code" name={[name, 'statusCode']}>
          <CodeSelect set="item_status" placeholder="Status" />
        </Form.Item>
      </Col>
      <Col span={24}>
        <Form.Item label="Description" name={[name, 'description']}>
          <Input.TextArea rows={2} />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="Registered to" name={[name, 'presentlyRegisteredTo']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item
          label="Estimated value"
          name={[name, 'estimatedValue']}
          rules={[{ required: true }]}
        >
          <InputNumber style={{ width: '100%' }} min={0} />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="Currency" name={[name, 'currencyCode']}>
          <CodeSelect set="currencies" placeholder="Currency" />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="Size" name={[name, 'size']}>
          <InputNumber style={{ width: '100%' }} min={0} />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="Size UoM" name={[name, 'sizeUom']}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item label="Registration date" name={[name, 'registrationDate']}>
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item
          label="Disposed value"
          name={[name, 'disposedValue']}
          tooltip="The actual value/cash disposed against this item"
        >
          <InputNumber style={{ width: '100%' }} min={0} />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item
          label="Registration number"
          name={[name, 'registrationNumber']}
          tooltip="e.g. the invoice number"
        >
          <Input />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item
          label="Identification number"
          name={[name, 'identificationNumber']}
          tooltip="e.g. the receipt number"
        >
          <Input />
        </Form.Item>
      </Col>
      <Col span={24}>
        <Form.Item label="Status comments" name={[name, 'statusComments']}>
          <Input.TextArea rows={2} />
        </Form.Item>
      </Col>
    </Row>
  );
}
