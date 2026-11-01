import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import App from '../src/App.jsx';
import * as client from '../src/api/client.js';
import fixture from './fixtures/overview-response.json';
import noPassFixture from './fixtures/overview-no-pass-response.json';

vi.mock('../src/api/client.js', async () => {
  const actual = await vi.importActual('../src/api/client.js');
  return { ...actual, fetchOverview: vi.fn(), fetchPosition: vi.fn() };
});

describe('App (integration, real backend fixture)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    client.fetchPosition.mockResolvedValue({ position: fixture.position, meta: fixture.meta.position });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('given the real /overview fixture with alerts, when the app loads, then alerts, badges and passes are rendered', async () => {
    client.fetchOverview.mockResolvedValue(fixture);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('ISS visible soon - Check sky in NW')).toBeInTheDocument();
    });
    expect(screen.getAllByText('Fallback data').length).toBeGreaterThan(0);
    expect(screen.getAllByRole('row').length).toBeGreaterThan(1);
  });

  it('given the no-pass fixture, when the app loads, then the "no visible pass" message is shown and tleStale warning appears', async () => {
    client.fetchOverview.mockResolvedValue(noPassFixture);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('No visible pass in the returned predictions')).toBeInTheDocument();
    });
    expect(screen.getByText(/TLE.*stale/i)).toBeInTheDocument();
  });
});
