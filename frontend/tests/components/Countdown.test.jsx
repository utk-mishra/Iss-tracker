import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Countdown from '../../src/components/Countdown.jsx';

describe('Countdown', () => {
  it('given a future target time, when rendered, then a countdown string is shown', () => {
    const future = new Date(Date.now() + 90 * 60 * 1000).toISOString();
    render(<Countdown targetIso={future} />);

    expect(screen.getByText(/h|m|s/)).toBeInTheDocument();
  });

  it('given no target (null), when rendered, then it does not crash and shows a placeholder', () => {
    render(<Countdown targetIso={null} />);

    expect(screen.getByText('\u2014')).toBeInTheDocument();
  });
});
