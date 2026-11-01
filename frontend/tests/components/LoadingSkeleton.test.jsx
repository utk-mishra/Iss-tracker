import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import LoadingSkeleton from '../../src/components/LoadingSkeleton.jsx';

describe('LoadingSkeleton', () => {
  it('given it is rendered, then it exposes a status role for accessibility', () => {
    render(<LoadingSkeleton />);

    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});
