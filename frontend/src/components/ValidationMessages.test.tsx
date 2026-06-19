import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ValidationMessages } from './ValidationMessages';
import type { ValidationMessage } from '../types';

// Pure display component: empty → success note; otherwise a pluralised count + per-finding rows with a
// severity tag. Shown after create + on the report detail page, so the error/warning split is load-bearing.
describe('ValidationMessages', () => {
  it('shows a success note when there are no messages', () => {
    render(<ValidationMessages messages={[]} />);
    expect(screen.getByText('No validation issues.')).toBeInTheDocument();
  });

  it('summarises one error and one warning (singular)', () => {
    const messages: ValidationMessage[] = [
      { severity: 'ERROR', path: 'report.x', message: 'bad x' },
      { severity: 'WARNING', path: 'report.y', message: 'maybe y' },
    ];
    const { container } = render(<ValidationMessages messages={messages} />);
    expect(container.textContent).toContain('1 error, 1 warning');
  });

  it('pluralises multiple errors and zero warnings', () => {
    const messages: ValidationMessage[] = [
      { severity: 'ERROR', message: 'a' },
      { severity: 'ERROR', message: 'b' },
    ];
    const { container } = render(<ValidationMessages messages={messages} />);
    expect(container.textContent).toContain('2 errors, 0 warnings');
  });

  it('renders each finding with its severity tag, path and message', () => {
    render(<ValidationMessages messages={[{ severity: 'ERROR', path: 'p.q', message: 'boom' }]} />);
    expect(screen.getByText('ERROR')).toBeInTheDocument();
    expect(screen.getByText('p.q')).toBeInTheDocument();
    expect(screen.getByText('boom')).toBeInTheDocument();
  });
});
