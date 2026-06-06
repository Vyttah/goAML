import { Badge, Button, List, Popover, Typography } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useUnreadNotifications, useMarkNotificationRead } from '../../features/notifications/useNotifications';
import type { NotificationView } from '../../types';

/** Header bell: unread badge + a popover of unread items; clicking one marks it read and opens its report. */
export function NotificationBell() {
  const { data } = useUnreadNotifications();
  const markRead = useMarkNotificationRead();
  const navigate = useNavigate();
  const unread = data ?? [];

  const open = (n: NotificationView) => {
    markRead.mutate(n.id);
    if (n.reportId) navigate(`/reports/${n.reportId}`);
  };

  const content = (
    <div style={{ width: 320 }}>
      {unread.length === 0 ? (
        <Typography.Text type="secondary">No new notifications</Typography.Text>
      ) : (
        <List
          size="small"
          dataSource={unread.slice(0, 6)}
          renderItem={(n) => (
            <List.Item
              style={{ cursor: 'pointer' }}
              onClick={() => open(n)}
              aria-label={`notification ${n.title}`}
            >
              <List.Item.Meta title={n.title} description={n.body} />
            </List.Item>
          )}
        />
      )}
      <div style={{ textAlign: 'center', marginTop: 8 }}>
        <Button type="link" size="small" onClick={() => navigate('/notifications')}>
          View all
        </Button>
      </div>
    </div>
  );

  return (
    <Popover content={content} trigger="click" placement="bottomRight">
      <Badge count={unread.length} size="small">
        <Button
          type="text"
          icon={<BellOutlined style={{ color: '#fff', fontSize: 18 }} />}
          aria-label="Notifications"
        />
      </Badge>
    </Popover>
  );
}
