import { Alert, List, Space, Tag, Typography } from 'antd';
import type { ValidationMessage } from '../types';

/**
 * Renders the server's validation findings (errors block submission; warnings don't). Used after
 * create (13.6) and on the report detail page (13.7). Empty list → a success note.
 */
export function ValidationMessages({ messages }: { messages: ValidationMessage[] }) {
  if (messages.length === 0) {
    return <Alert type="success" showIcon message="No validation issues." />;
  }

  const errors = messages.filter((m) => m.severity === 'ERROR');
  const warnings = messages.filter((m) => m.severity === 'WARNING');

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Typography.Text>
        {errors.length} error{errors.length === 1 ? '' : 's'}, {warnings.length} warning
        {warnings.length === 1 ? '' : 's'}
      </Typography.Text>
      <List
        size="small"
        bordered
        dataSource={messages}
        renderItem={(m) => (
          <List.Item>
            <Space align="start">
              <Tag color={m.severity === 'ERROR' ? 'error' : 'warning'}>{m.severity}</Tag>
              <div>
                <Typography.Text code>{m.path || m.code}</Typography.Text>{' '}
                <Typography.Text>{m.message}</Typography.Text>
              </div>
            </Space>
          </List.Item>
        )}
      />
    </Space>
  );
}
