import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import DegradedBanner from '../../src/components/DegradedBanner.jsx';

describe('DegradedBanner', () => {
  it('given all sources are FRESH, when rendered, then no banner is shown', () => {
    const meta = {
      position: { source: 'FRESH', degraded: false, ageMs: 0 },
      passes: { meta: { source: 'FRESH', degraded: false, ageMs: 0 } },
      cloud: { source: 'FRESH', degraded: false, ageMs: 0 },
    };

    render(<DegradedBanner meta={meta} />);

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('given any source is degraded, when rendered, then a banner is shown', () => {
    const meta = {
      position: { source: 'MOCK', degraded: true, ageMs: 0 },
      passes: { meta: { source: 'FRESH', degraded: false, ageMs: 0 } },
      cloud: { source: 'FRESH', degraded: false, ageMs: 0 },
    };

    render(<DegradedBanner meta={meta} />);

    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});
