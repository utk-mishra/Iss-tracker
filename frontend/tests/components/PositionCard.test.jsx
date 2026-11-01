import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PositionCard from '../../src/components/PositionCard.jsx';
import fixture from '../fixtures/overview-response.json';

describe('PositionCard', () => {
  it('given a real position, when rendered, then latitude, longitude, altitude and velocity are shown', () => {
    render(<PositionCard position={fixture.position} meta={fixture.meta.position} />);

    expect(screen.getByText(`${fixture.position.latitude}\u00b0`)).toBeInTheDocument();
    expect(screen.getByText(`${fixture.position.altitudeKm} km`)).toBeInTheDocument();
    expect(screen.getByText(`${fixture.position.velocityKmh} km/h`)).toBeInTheDocument();
  });

  it('given no position (null), when rendered, then it does not crash', () => {
    expect(() => render(<PositionCard position={null} meta={null} />)).not.toThrow();
  });
});
