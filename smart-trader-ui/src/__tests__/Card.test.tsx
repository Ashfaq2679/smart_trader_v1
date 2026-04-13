import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Card } from '../components/common/Card';

describe('Card', () => {
  it('renders children', () => {
    render(<Card>Card content</Card>);
    expect(screen.getByText('Card content')).toBeInTheDocument();
  });

  it('renders with title', () => {
    render(<Card title="Test Title">Content</Card>);
    expect(screen.getByText('Test Title')).toBeInTheDocument();
  });

  it('renders without title header when no title or actions', () => {
    const { container } = render(<Card>Content only</Card>);
    expect(container.querySelectorAll('.border-b')).toHaveLength(0);
  });

  it('renders actions in header', () => {
    render(
      <Card title="Title" actions={<button type="button">Action</button>}>
        Content
      </Card>,
    );
    expect(screen.getByText('Action')).toBeInTheDocument();
  });
});
