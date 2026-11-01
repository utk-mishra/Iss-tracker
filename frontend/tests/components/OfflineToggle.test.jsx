import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OfflineToggle from '../../src/components/OfflineToggle.jsx';

describe('OfflineToggle', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('given the toggle is off, when clicked, then onChange is called with true and persisted to localStorage', async () => {
    let offline = false;
    const onChange = (value) => { offline = value; };
    render(<OfflineToggle offline={offline} onChange={onChange} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole('checkbox'));

    expect(offline).toBe(true);
  });

  it('given offline=true is passed in, when rendered, then the checkbox is checked', () => {
    render(<OfflineToggle offline={true} onChange={() => {}} />);

    expect(screen.getByRole('checkbox')).toBeChecked();
  });
});
