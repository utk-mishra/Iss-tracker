import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SourceBadge from '../../src/components/SourceBadge.jsx';

describe('SourceBadge', () => {
  it('given source FRESH, when rendered, then it shows a green "Live" badge', () => {
    render(<SourceBadge source="FRESH" ageMs={0} />);

    const badge = screen.getByText('Live');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveClass('badge-fresh');
  });

  it('given source STALE with an age, when rendered, then it shows an amber "Cached N min ago" badge', () => {
    render(<SourceBadge source="STALE" ageMs={180000} />);

    const badge = screen.getByText('Cached 3 min ago');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveClass('badge-stale');
  });

  it('given source STALE with age under a minute, when rendered, then it shows "Cached 0 min ago"', () => {
    render(<SourceBadge source="STALE" ageMs={5000} />);

    expect(screen.getByText('Cached 0 min ago')).toBeInTheDocument();
  });

  it('given source MOCK, when rendered, then it shows a red "Fallback data" badge', () => {
    render(<SourceBadge source="MOCK" ageMs={0} />);

    const badge = screen.getByText('Fallback data');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveClass('badge-mock');
  });
});
