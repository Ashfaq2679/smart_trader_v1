import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Badge } from '../components/common/Badge';

describe('Badge', () => {
  it('renders with label', () => {
    render(<Badge label="BUY" />);
    expect(screen.getByText('BUY')).toBeInTheDocument();
  });

  it('applies custom className', () => {
    render(<Badge label="SELL" className="bg-red-100 text-red-800" />);
    const badge = screen.getByText('SELL');
    expect(badge.className).toContain('bg-red-100');
    expect(badge.className).toContain('text-red-800');
  });
});
