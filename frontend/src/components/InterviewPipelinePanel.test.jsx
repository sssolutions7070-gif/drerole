import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import InterviewPipelinePanel from './InterviewPipelinePanel';
import { interviewRoundsAPI, scorecardAPI } from '../services/api';

vi.mock('../services/api');

const round = (overrides = {}) => ({
  id: 5000, roundNumber: 1, title: 'Phone screen', roundType: 'PHONE_SCREEN', status: 'SCHEDULED',
  scheduledAt: '2026-01-01T10:00:00', ...overrides,
});

describe('InterviewPipelinePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  const renderPanel = (props = {}) =>
    render(
      <MemoryRouter>
        <InterviewPipelinePanel applicationId={1000} isRecruiter={false} onClose={() => {}} {...props} />
      </MemoryRouter>
    );

  it('shows a message when there are no rounds yet', async () => {
    interviewRoundsAPI.list.mockResolvedValue({ data: [] });

    renderPanel();

    expect(await screen.findByText(/no interview rounds scheduled yet/i)).toBeInTheDocument();
  });

  it('lists rounds with their status and a join button', async () => {
    interviewRoundsAPI.list.mockResolvedValue({ data: [round()] });

    renderPanel();

    expect(await screen.findByText(/round 1: phone screen/i)).toBeInTheDocument();
    expect(screen.getByText('SCHEDULED')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /join/i })).toBeInTheDocument();
  });

  it('does not show recruiter-only actions to a candidate', async () => {
    interviewRoundsAPI.list.mockResolvedValue({ data: [round()] });

    renderPanel({ isRecruiter: false });

    await screen.findByText(/round 1: phone screen/i);
    expect(screen.queryByRole('button', { name: /add round/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /reschedule/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /fill scorecard/i })).not.toBeInTheDocument();
  });

  it('shows recruiter actions and fetches scorecards for the recruiter', async () => {
    interviewRoundsAPI.list.mockResolvedValue({ data: [round()] });
    scorecardAPI.getForApplication.mockResolvedValue({ data: [{ roundId: 5000, scorecard: null }] });

    renderPanel({ isRecruiter: true });

    await screen.findByText(/round 1: phone screen/i);
    expect(screen.getByRole('button', { name: /add round/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reschedule/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /fill scorecard/i })).toBeInTheDocument();
  });

  it('shows the recommendation once a scorecard exists', async () => {
    interviewRoundsAPI.list.mockResolvedValue({ data: [round()] });
    scorecardAPI.getForApplication.mockResolvedValue({
      data: [{ roundId: 5000, scorecard: { recommendation: 'STRONG_YES' } }]
    });

    renderPanel({ isRecruiter: true });

    expect(await screen.findByText(/recommendation:/i)).toBeInTheDocument();
    expect(screen.getByText('Strong yes')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /edit scorecard/i })).toBeInTheDocument();
  });

  it('schedules a new round', async () => {
    interviewRoundsAPI.list.mockResolvedValueOnce({ data: [] }).mockResolvedValueOnce({ data: [round()] });
    scorecardAPI.getForApplication.mockResolvedValue({ data: [] });
    interviewRoundsAPI.schedule.mockResolvedValue({ data: round() });
    const user = userEvent.setup();

    renderPanel({ isRecruiter: true });
    await screen.findByText(/no interview rounds scheduled yet/i);

    await user.click(screen.getByRole('button', { name: /add round/i }));
    await user.type(screen.getByPlaceholderText(/technical round with backend team/i), 'Phone screen');
    await user.type(document.querySelector('input[type="date"]'), '2026-05-01');
    await user.type(document.querySelector('input[type="time"]'), '10:00');
    await user.click(screen.getByRole('button', { name: /schedule round/i }));

    expect(interviewRoundsAPI.schedule).toHaveBeenCalledWith(1000, 'Phone screen', 'PHONE_SCREEN', '2026-05-01T10:00:00');
  });

  it('shows an error when loading the pipeline fails', async () => {
    interviewRoundsAPI.list.mockRejectedValue({ response: { data: { error: 'Application not found: 1000' } } });

    renderPanel();

    expect(await screen.findByText('Application not found: 1000')).toBeInTheDocument();
  });

  describe('calendar sync', () => {
    beforeEach(() => {
      window.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
      window.URL.revokeObjectURL = vi.fn();
    });

    it('shows calendar buttons for a scheduled round', async () => {
      interviewRoundsAPI.list.mockResolvedValue({ data: [round()] });

      renderPanel();

      expect(await screen.findByRole('button', { name: /\.ics/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /google calendar/i })).toBeInTheDocument();
    });

    it('does not show calendar buttons for a round with no scheduled time', async () => {
      interviewRoundsAPI.list.mockResolvedValue({ data: [round({ scheduledAt: null })] });

      renderPanel();

      await screen.findByText(/round 1: phone screen/i);
      expect(screen.queryByRole('button', { name: /\.ics/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('link', { name: /google calendar/i })).not.toBeInTheDocument();
    });

    it('downloads the .ics file for the round', async () => {
      interviewRoundsAPI.list.mockResolvedValue({ data: [round()] });
      interviewRoundsAPI.downloadCalendarInvite.mockResolvedValue({ data: 'BEGIN:VCALENDAR...' });
      const user = userEvent.setup();

      renderPanel();
      await user.click(await screen.findByRole('button', { name: /\.ics/i }));

      expect(interviewRoundsAPI.downloadCalendarInvite).toHaveBeenCalledWith(5000);
      expect(window.URL.createObjectURL).toHaveBeenCalled();
    });

    it('builds a Google Calendar link with the round title and join url', async () => {
      interviewRoundsAPI.list.mockResolvedValue({ data: [round()] });

      renderPanel();

      const link = await screen.findByRole('link', { name: /google calendar/i });
      expect(link.href).toContain('calendar.google.com');
      expect(link.href).toContain('text=Phone+screen');
      expect(link.href).toContain('interview-rounds%2F5000');
    });
  });
});
