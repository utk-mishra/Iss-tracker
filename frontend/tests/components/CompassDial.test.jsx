import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import CompassDial from '../../src/components/CompassDial.jsx';

describe('CompassDial', () => {
  it('given rise and set azimuths, when rendered, then an SVG element is produced', () => {
    const { container } = render(<CompassDial riseAzimuthDeg={315.6} setAzimuthDeg={139.9} />);

    expect(container.querySelector('svg')).toBeInTheDocument();
  });

  it('given null azimuths, when rendered, then it does not crash', () => {
    expect(() => render(<CompassDial riseAzimuthDeg={null} setAzimuthDeg={null} />)).not.toThrow();
  });
});
