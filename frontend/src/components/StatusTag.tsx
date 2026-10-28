import { Tag } from 'antd';

/**
 * Colored chip for a report/submission status. Unknown statuses fall back to a neutral tag so the UI
 * never breaks if the backend adds one.
 */
const COLORS: Record<string, string> = {
  DRAFT: 'default',
  VALID: 'blue',
  INVALID: 'red',
  SUBMITTED: 'processing',
  ACCEPTED: 'success',
  REJECTED: 'error',
  FAILED: 'error',
};

export function StatusTag({ status }: { status: string }) {
  return <Tag color={COLORS[status] ?? 'default'}>{status}</Tag>;
}
