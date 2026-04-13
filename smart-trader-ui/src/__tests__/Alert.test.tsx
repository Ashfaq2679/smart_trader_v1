import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Alert } from '../components/common/Alert';

describe('Alert', () => {
  it('renders error alert with message', () => {
    render(<Alert type="error" message="Something went wrong" />);
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('renders success alert', () => {
    render(<Alert type="success" message="Operation successful" />);
    expect(screen.getByText('Operation successful')).toBeInTheDocument();
  });

  it('calls onDismiss when dismiss button is clicked', async () => {
    const onDismiss = vi.fn();
    const user = userEvent.setup();
    render(<Alert type="info" message="Info message" onDismiss={onDismiss} />);

    await user.click(screen.getByLabelText('Dismiss'));
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it('does not render dismiss button when onDismiss is not provided', () => {
    render(<Alert type="warning" message="Warning message" />);
    expect(screen.queryByLabelText('Dismiss')).not.toBeInTheDocument();
  });
});
