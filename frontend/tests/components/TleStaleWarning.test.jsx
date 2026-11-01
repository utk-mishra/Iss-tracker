import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import TleStaleWarning from '../../src/components/TleStaleWarning.jsx';

describe('TleStaleWarning', () => {
  it('given tleStale is true, when rendered, then a warning is shown', () => {
    render(<TleStaleWarning tleStale={true} />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('given tleStale is false, when rendered, then no warning is shown', () => {
    render(<TleStaleWarning tleStale={false} />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
