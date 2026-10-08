import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActiveSessionsPanel from './ActiveSessionsPanel';
import { accountAPI } from '../services/api';

vi.mock('../services/api');

const sessions = [
  { id: 1, device: 'Chrome on Windows', ipAddress: '127.0.0.1', lastUsedAt: '2026-10-07T10:00:00', current: true },
  { id: 2, device: 'Safari on iOS', ipAddress: '10.0.0.5', lastUsedAt: '2026-10-06T09:00:00', current: false },
];

describe('ActiveSessionsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.confirm = vi.fn(() => true);
  });

  it('lists sessions and marks the current one', async () => {
    accountAPI.getSessions.mockResolvedValue({ data: sessions });

    render(<ActiveSessionsPanel userId={10} />);

    expect(await screen.findByText('Chrome on Windows')).toBeInTheDocument();
    expect(screen.getByText('This device')).toBeInTheDocument();
    expect(screen.getByText('Safari on iOS')).toBeInTheDocument();
  });

  it('does not show a sign-out button for the current session', async () => {
    accountAPI.getSessions.mockResolvedValue({ data: sessions });

    render(<ActiveSessionsPanel userId={10} />);

    await screen.findByText('Chrome on Windows');
    expect(screen.getAllByRole('button', { name: /Sign out/i }).length).toBe(1);
  });

  it('revokes a non-current session and removes it from the list', async () => {
    accountAPI.getSessions.mockResolvedValue({ data: sessions });
    accountAPI.revokeSession.mockResolvedValue({});
    const user = userEvent.setup();

    render(<ActiveSessionsPanel userId={10} />);
    await screen.findByText('Safari on iOS');

    await user.click(screen.getByRole('button', { name: /Sign out/i }));

    await waitFor(() => {
      expect(accountAPI.revokeSession).toHaveBeenCalledWith(10, 2);
    });
    expect(screen.queryByText('Safari on iOS')).not.toBeInTheDocument();
  });

  it('shows a message when there are no sessions', async () => {
    accountAPI.getSessions.mockResolvedValue({ data: [] });

    render(<ActiveSessionsPanel userId={10} />);

    expect(await screen.findByText(/No active sessions/i)).toBeInTheDocument();
  });
});
