import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LocationForm from '../../src/components/LocationForm.jsx';

describe('LocationForm', () => {
  it('given valid lat/lon, when submitted, then onSubmit is called with the parsed numbers', async () => {
    const onSubmit = vi.fn();
    render(<LocationForm onSubmit={onSubmit} />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/latitude/i), '28.6139');
    await user.type(screen.getByLabelText(/longitude/i), '77.2090');
    await user.click(screen.getByRole('button', { name: /use these coordinates/i }));

    expect(onSubmit).toHaveBeenCalledWith({ lat: 28.6139, lon: 77.209 });
  });

  it('given lat=91 (just outside boundary), when submitted, then onSubmit is not called and an error is shown', async () => {
    const onSubmit = vi.fn();
    render(<LocationForm onSubmit={onSubmit} />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/latitude/i), '91');
    await user.type(screen.getByLabelText(/longitude/i), '0');
    await user.click(screen.getByRole('button', { name: /use these coordinates/i }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('given lat=90 (exactly on boundary), when submitted, then onSubmit is called', async () => {
    const onSubmit = vi.fn();
    render(<LocationForm onSubmit={onSubmit} />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/latitude/i), '90');
    await user.type(screen.getByLabelText(/longitude/i), '0');
    await user.click(screen.getByRole('button', { name: /use these coordinates/i }));

    expect(onSubmit).toHaveBeenCalledWith({ lat: 90, lon: 0 });
  });

  it('given lon=181 (just outside boundary), when submitted, then onSubmit is not called', async () => {
    const onSubmit = vi.fn();
    render(<LocationForm onSubmit={onSubmit} />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/latitude/i), '0');
    await user.type(screen.getByLabelText(/longitude/i), '181');
    await user.click(screen.getByRole('button', { name: /use these coordinates/i }));

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('given a non-numeric latitude, when submitted, then onSubmit is not called', async () => {
    const onSubmit = vi.fn();
    render(<LocationForm onSubmit={onSubmit} />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/latitude/i), 'abc');
    await user.type(screen.getByLabelText(/longitude/i), '0');
    await user.click(screen.getByRole('button', { name: /use these coordinates/i }));

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('given geolocation is available, when "Use my location" is clicked, then onSubmit is called with the returned coordinates', async () => {
    const onSubmit = vi.fn();
    const getCurrentPosition = vi.fn((success) =>
      success({ coords: { latitude: 12.34, longitude: 56.78 } })
    );
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } });

    render(<LocationForm onSubmit={onSubmit} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /use my location/i }));

    expect(onSubmit).toHaveBeenCalledWith({ lat: 12.34, lon: 56.78 });
    vi.unstubAllGlobals();
  });
});
