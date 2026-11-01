import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ErrorBanner from '../../src/components/ErrorBanner.jsx';

describe('ErrorBanner', () => {
  it('given no error, when rendered, then nothing is shown', () => {
    const { container } = render(<ErrorBanner error={null} />);

    expect(container.textContent).toBe('');
  });

  it('given a generic error, when rendered, then its message is shown', () => {
    render(<ErrorBanner error={{ message: 'Network error: could not reach the server.' }} />);

    expect(screen.getByText(/could not reach the server/i)).toBeInTheDocument();
  });

  it('given a 429 error with retryAfter, when rendered, then the Retry-After value is shown', () => {
    render(<ErrorBanner error={{ message: 'Rate limit exceeded', status: 429, retryAfter: '42' }} />);

    expect(screen.getByText(/42/)).toBeInTheDocument();
  });
});
