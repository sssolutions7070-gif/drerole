import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import GoogleCalendarPanel from './GoogleCalendarPanel';
import { calendarAPI } from '../services/api';

vi.mock('../services/api');

const originalLocation = window.location;

describe('GoogleCalendarPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.confirm = vi.fn(() => true);
    delete window.location;
    window.location = { ...originalLocation, href: '', search: '' };
  });

  afterEach(() => {
    window.location = originalLocation;
  });

  it('shows a connect button when not connected', async () => {
    calendarAPI.getGoogleStatus.mockResolvedValue({ data: { connected: false } });

    render(<GoogleCalendarPanel />);

    expect(await screen.findByRole('button', { name: /connect google calendar/i })).toBeInTheDocument();
  });

  it('shows a connected badge and disconnect button when already connected', async () => {
    calendarAPI.getGoogleStatus.mockResolvedValue({ data: { connected: true } });

    render(<GoogleCalendarPanel />);

    expect(await screen.findByText('Connected')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /disconnect/i })).toBeInTheDocument();
  });

  it('navigates the browser to the authorize URL when connecting', async () => {
    calendarAPI.getGoogleStatus.mockResolvedValue({ data: { connected: false } });
    calendarAPI.getGoogleConnectUrl.mockResolvedValue({ data: { url: 'https://accounts.google.com/authorize?x=1' } });
    const user = userEvent.setup();

    render(<GoogleCalendarPanel />);
    await user.click(await screen.findByRole('button', { name: /connect google calendar/i }));

    await waitFor(() => expect(window.location.href).toBe('https://accounts.google.com/authorize?x=1'));
  });

  it('shows an error instead of navigating when starting the connection fails', async () => {
    calendarAPI.getGoogleStatus.mockResolvedValue({ data: { connected: false } });
    calendarAPI.getGoogleConnectUrl.mockRejectedValue({ response: { data: { error: 'Google Calendar sync is not configured on this server' } } });
    const user = userEvent.setup();

    render(<GoogleCalendarPanel />);
    await user.click(await screen.findByRole('button', { name: /connect google calendar/i }));

    expect(await screen.findByText('Google Calendar sync is not configured on this server')).toBeInTheDocument();
  });

  it('disconnects after confirming, flipping back to the connect button', async () => {
    calendarAPI.getGoogleStatus.mockResolvedValue({ data: { connected: true } });
    calendarAPI.disconnectGoogle.mockResolvedValue({ data: { disconnected: true } });
    const user = userEvent.setup();

    render(<GoogleCalendarPanel />);
    await user.click(await screen.findByRole('button', { name: /disconnect/i }));

    expect(await screen.findByRole('button', { name: /connect google calendar/i })).toBeInTheDocument();
  });

  it('does not disconnect when the confirmation is cancelled', async () => {
    window.confirm = vi.fn(() => false);
    calendarAPI.getGoogleStatus.mockResolvedValue({ data: { connected: true } });
    const user = userEvent.setup();

    render(<GoogleCalendarPanel />);
    await user.click(await screen.findByRole('button', { name: /disconnect/i }));

    expect(calendarAPI.disconnectGoogle).not.toHaveBeenCalled();
    expect(screen.getByText('Connected')).toBeInTheDocument();
  });

  it('shows a success banner when landing back from a successful Google redirect', async () => {
    window.location.search = '?googleCalendar=connected';
    calendarAPI.getGoogleStatus.mockResolvedValue({ data: { connected: true } });

    render(<GoogleCalendarPanel />);

    expect(await screen.findByText('Google Calendar connected.')).toBeInTheDocument();
  });

  it('shows the server-provided error message when the Google redirect reports a failure', async () => {
    window.location.search = '?googleCalendar=error&message=This%20connection%20link%20has%20expired';
    calendarAPI.getGoogleStatus.mockResolvedValue({ data: { connected: false } });

    render(<GoogleCalendarPanel />);

    expect(await screen.findByText('This connection link has expired')).toBeInTheDocument();
  });
});
