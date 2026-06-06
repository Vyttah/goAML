import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusTag } from './StatusTag';

describe('StatusTag', () => {
  it('renders the status text', () => {
    render(<StatusTag status="ACCEPTED" />);
    expect(screen.getByText('ACCEPTED')).toBeInTheDocument();
  });

  it('falls back gracefully for an unknown status', () => {
    render(<StatusTag status="WIBBLE" />);
    expect(screen.getByText('WIBBLE')).toBeInTheDocument();
  });
});
