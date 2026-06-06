import { useState } from 'react';
import { Badge, Button, Card, List, Space, Switch, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useNotifications, useMarkNotificationRead } from './useNotifications';
import type { NotificationView } from '../../types';

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/** Notifications center: the caller's notifications with unread emphasis, mark-read, and report links. */
export function NotificationsPage() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const { data, isLoading } = useNotifications(unreadOnly);
  const markRead = useMarkNotificationRead();
  const navigate = useNavigate();

  const goToReport = (n: NotificationView) => {
    if (!n.readAt) markRead.mutate(n.id);
    if (n.reportId) navigate(`/reports/${n.reportId}`);
  };

  return (
    <>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Notifications
        </Typography.Title>
        <Space>
          <Typography.Text type="secondary">Unread only</Typography.Text>
          <Switch checked={unreadOnly} onChange={setUnreadOnly} aria-label="Unread only" />
        </Space>
      </Space>

      <Card>
        <List
          loading={isLoading}
          dataSource={data ?? []}
          locale={{ emptyText: 'No notifications' }}
          renderItem={(n) => {
            const isUnread = !n.readAt;
            return (
              <List.Item
                actions={[
                  n.reportId ? (
                    <Button type="link" size="small" onClick={() => goToReport(n)} key="open">
                      Open report
                    </Button>
                  ) : null,
                  isUnread ? (
                    <Button
                      type="link"
                      size="small"
                      onClick={() => markRead.mutate(n.id)}
                      key="read"
                    >
                      Mark read
                    </Button>
                  ) : null,
                ].filter(Boolean)}
              >
                <List.Item.Meta
                  avatar={isUnread ? <Badge status="processing" /> : <Badge status="default" />}
                  title={
                    <Space>
                      {n.title}
                      {isUnread && <Tag color="blue">new</Tag>}
                    </Space>
                  }
                  description={
                    <>
                      <div>{n.body}</div>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {formatTimestamp(n.createdAt)}
                      </Typography.Text>
                    </>
                  }
                />
              </List.Item>
            );
          }}
        />
      </Card>
    </>
  );
}
