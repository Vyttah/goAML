import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ReportFilingDetails } from './ReportFilingDetails';

describe('ReportFilingDetails', () => {
  it('renders typed DPMSR sections from a full-fidelity payload', () => {
    render(
      <ReportFilingDetails
        input={{
          reason: 'DPMS threshold met',
          action: 'Filed',
          indicators: ['DPMSJ', 'ACTRC'],
          reportingPerson: { firstName: 'Sara', lastName: 'Khan', nationality1: 'AE' },
          parties: [{ reason: 'Seller', entity: { name: 'Minimal Trading FZE' } }],
          goods: [{ itemType: 'GOLD', estimatedValue: 90000, currencyCode: 'AED' }],
        }}
      />,
    );

    expect(screen.getByText('DPMS threshold met')).toBeInTheDocument();
    expect(screen.getByText('DPMSJ')).toBeInTheDocument();
    expect(screen.getByText('Sara Khan')).toBeInTheDocument();
    expect(screen.getByText('Minimal Trading FZE')).toBeInTheDocument();
    expect(screen.getByText('GOLD')).toBeInTheDocument();
    expect(screen.getByText('90000 AED')).toBeInTheDocument();
    expect(screen.getByText('Parties (1)')).toBeInTheDocument();
    expect(screen.getByText('Goods (1)')).toBeInTheDocument();
  });

  it('renders a person party name from the curated shape', () => {
    render(
      <ReportFilingDetails
        input={{ parties: [{ person: { firstName: 'Ali', lastName: 'Noor' } }], goods: [] }}
      />,
    );
    expect(screen.getByText('Ali Noor')).toBeInTheDocument();
    expect(screen.getByText('No goods')).toBeInTheDocument();
  });

  it('shows an empty state when there is no input', () => {
    render(<ReportFilingDetails input={null} />);
    expect(screen.getByText('No stored filing input')).toBeInTheDocument();
  });
});
