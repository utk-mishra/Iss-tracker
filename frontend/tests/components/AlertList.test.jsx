import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import AlertList from '../../src/components/AlertList.jsx';
import fixture from '../fixtures/overview-response.json';

describe('AlertList', () => {
  it('given alerts from the real fixture, when rendered, then every alert text is shown', () => {
    render(<AlertList alerts={fixture.alerts} />);

    fixture.alerts.forEach((alertText) => {
      expect(screen.getByText(alertText)).toBeInTheDocument();
    });
  });

  it('given an empty alerts array, when rendered, then nothing is shown', () => {
    const { container } = render(<AlertList alerts={[]} />);

    expect(container.textContent).toBe('');
  });
});
