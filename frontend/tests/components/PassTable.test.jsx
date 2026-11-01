import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PassTable from '../../src/components/PassTable.jsx';
import fixture from '../fixtures/overview-response.json';

describe('PassTable', () => {
  it('given passes from the real fixture, when rendered, then both rows are shown', () => {
    render(<PassTable passes={fixture.passes} />);

    expect(screen.getAllByRole('row')).toHaveLength(fixture.passes.length + 1); // +1 header row
  });

  it('given an observable pass, when rendered, then its row is highlighted', () => {
    render(<PassTable passes={fixture.passes} />);

    const rows = screen.getAllByRole('row');
    expect(rows[1]).toHaveClass('pass-observable');
  });

  it('given a non-observable pass, when rendered, then its row is muted and not highlighted', () => {
    render(<PassTable passes={fixture.passes} />);

    const rows = screen.getAllByRole('row');
    expect(rows[2]).toHaveClass('pass-muted');
    expect(rows[2]).not.toHaveClass('pass-observable');
  });

  it('given a pass with null magnitude and visibleStart, when rendered, then it does not crash and shows placeholders', () => {
    const nonObservablePass = fixture.passes[1];
    expect(nonObservablePass.magnitude).toBeNull();
    expect(nonObservablePass.visibleStart).toBeNull();

    expect(() => render(<PassTable passes={[nonObservablePass]} />)).not.toThrow();
  });

  it('given a visible pass with zero visible duration, when rendered, then it is labelled "visible (instant)" and not highlighted as observable', () => {
    const zeroDurationPass = {
      ...fixture.passes[0],
      visible: true,
      observable: false,
      visibleDurationSec: 0,
      visibleStart: fixture.passes[0].set,
      visibleEnd: fixture.passes[0].set,
    };

    render(<PassTable passes={[zeroDurationPass]} />);

    expect(screen.getByText(/visible \(instant\)/i)).toBeInTheDocument();
    const rows = screen.getAllByRole('row');
    expect(rows[1]).not.toHaveClass('pass-observable');
  });

  it('given an empty passes array, when rendered, then no data rows are shown', () => {
    render(<PassTable passes={[]} />);

    expect(screen.getAllByRole('row')).toHaveLength(1); // header row only
  });
});
