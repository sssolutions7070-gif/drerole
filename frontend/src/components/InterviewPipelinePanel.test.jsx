import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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

  describe('candidate round feedback', () => {
    it('lets a candidate rate a completed round', async () => {
      interviewRoundsAPI.list.mockResolvedValue({ data: [round({ status: 'COMPLETED' })] });

      renderPanel({ isRecruiter: false });

      await screen.findByText(/round 1: phone screen/i);
      expect(screen.getByRole('button', { name: /rate this interview/i })).toBeInTheDocument();
    });

    it('does not offer feedback for a round that has not happened yet', async () => {
      interviewRoundsAPI.list.mockResolvedValue({ data: [round({ status: 'SCHEDULED' })] });

      renderPanel({ isRecruiter: false });

      await screen.findByText(/round 1: phone screen/i);
      expect(screen.queryByRole('button', { name: /rate this interview/i })).not.toBeInTheDocument();
    });

    it('never shows the feedback button to the recruiter', async () => {
      interviewRoundsAPI.list.mockResolvedValue({ data: [round({ status: 'COMPLETED' })] });
      scorecardAPI.getForApplication.mockResolvedValue({ data: [] });

      renderPanel({ isRecruiter: true });

      await screen.findByText(/round 1: phone screen/i);
      expect(screen.queryByRole('button', { name: /rate this interview/i })).not.toBeInTheDocument();
    });

    it('opens the feedback form and submits a rating', async () => {
      interviewRoundsAPI.list.mockResolvedValue({ data: [round({ status: 'COMPLETED' })] });
      interviewRoundsAPI.getFeedback.mockResolvedValue({ data: null });
      interviewRoundsAPI.submitFeedback.mockResolvedValue({ data: { rating: 5 } });
      const user = userEvent.setup();

      renderPanel({ isRecruiter: false });
      await user.click(await screen.findByRole('button', { name: /rate this interview/i }));

      await user.click(await screen.findByRole('button', { name: '5 stars' }));
      await user.click(screen.getByRole('button', { name: /submit feedback/i }));

      await waitFor(() => expect(interviewRoundsAPI.submitFeedback).toHaveBeenCalledWith(5000, 5, null));
    });
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

  describe('proposing a choice of times', () => {
    it('sends multiple time options instead of scheduling a fixed one', async () => {
      interviewRoundsAPI.list.mockResolvedValueOnce({ data: [] }).mockResolvedValueOnce({ data: [round({ status: 'PROPOSED', scheduledAt: null })] });
      scorecardAPI.getForApplication.mockResolvedValue({ data: [] });
      interviewRoundsAPI.propose.mockResolvedValue({ data: round({ status: 'PROPOSED', scheduledAt: null }) });
      const user = userEvent.setup();

      renderPanel({ isRecruiter: true });
      await screen.findByText(/no interview rounds scheduled yet/i);
      await user.click(screen.getByRole('button', { name: /add round/i }));
      await user.type(screen.getByPlaceholderText(/technical round with backend team/i), 'Phone screen');
      await user.click(screen.getByLabelText(/offer the candidate a choice of times/i));

      const dateInputs = document.querySelectorAll('input[type="date"]');
      const timeInputs = document.querySelectorAll('input[type="time"]');
      await user.type(dateInputs[0], '2026-05-01');
      await user.type(timeInputs[0], '10:00');
      await user.type(dateInputs[1], '2026-05-01');
      await user.type(timeInputs[1], '14:00');
      await user.click(screen.getByRole('button', { name: /send time options/i }));

      expect(interviewRoundsAPI.propose).toHaveBeenCalledWith(
        1000, 'Phone screen', 'PHONE_SCREEN', ['2026-05-01T10:00:00', '2026-05-01T14:00:00']);
    });

    it('shows each proposed time to the candidate as a choosable button', async () => {
      const proposedRound = round({ status: 'PROPOSED', scheduledAt: null, proposedSlots: JSON.stringify(['2026-05-01T10:00:00', '2026-05-01T14:00:00']) });
      interviewRoundsAPI.list.mockResolvedValue({ data: [proposedRound] });

      renderPanel({ isRecruiter: false });

      expect(await screen.findByText(/pick a time that works for you/i)).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: /2026/i })).toHaveLength(2);
      expect(screen.queryByRole('button', { name: /^join$/i })).not.toBeInTheDocument();
    });

    it('shows the recruiter a read-only list while awaiting the candidate', async () => {
      const proposedRound = round({ status: 'PROPOSED', scheduledAt: null, proposedSlots: JSON.stringify(['2026-05-01T10:00:00']) });
      interviewRoundsAPI.list.mockResolvedValue({ data: [proposedRound] });
      scorecardAPI.getForApplication.mockResolvedValue({ data: [] });

      renderPanel({ isRecruiter: true });

      expect(await screen.findByText(/awaiting the candidate's choice/i)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /2026/i })).not.toBeInTheDocument();
    });

    it('confirms the chosen slot when the candidate clicks it', async () => {
      const proposedRound = round({ status: 'PROPOSED', scheduledAt: null, proposedSlots: JSON.stringify(['2026-05-01T10:00:00']) });
      interviewRoundsAPI.list.mockResolvedValueOnce({ data: [proposedRound] })
        .mockResolvedValueOnce({ data: [round({ status: 'SCHEDULED', scheduledAt: '2026-05-01T10:00:00' })] });
      interviewRoundsAPI.confirmSlot.mockResolvedValue({ data: {} });
      const user = userEvent.setup();

      renderPanel({ isRecruiter: false });
      await user.click(await screen.findByRole('button', { name: /2026/i }));

      expect(interviewRoundsAPI.confirmSlot).toHaveBeenCalledWith(5000, '2026-05-01T10:00:00');
    });
  });

  describe('recording playback & AI insights', () => {
    it('opens recording modal and loads speech & sentiment insights', async () => {
      const recRound = round({
        recordingUrl: '/uploads/recordings/round_5000_123.webm',
        recordedAt: '2026-01-01T11:00:00'
      });
      interviewRoundsAPI.list.mockResolvedValue({ data: [recRound] });
      interviewRoundsAPI.getRecordingUrl.mockReturnValue('/api/interview-rounds/5000/recording');
      interviewRoundsAPI.getRecordingInsights.mockResolvedValue({
        data: {
          clarityScore: 94,
          wordsPerMinute: 142,
          cadenceEvaluation: 'Optimal & Confident Pace',
          fillerWordFrequency: '< 1.0%',
          sentiment: { confident: 88, thoughtful: 10, hesitant: 2 },
          technicalKeywords: ['Microservices', 'Distributed Systems'],
          keyTakeaways: ['Articulated trade-offs with structured rationale'],
          growthSuggestions: ['Maintain comfortable eye contact']
        }
      });
      const user = userEvent.setup();

      renderPanel({ isRecruiter: true });

      const watchButton = await screen.findByRole('button', { name: /watch recording/i });
      expect(watchButton).toBeInTheDocument();
      await user.click(watchButton);

      expect(interviewRoundsAPI.getRecordingInsights).toHaveBeenCalledWith(5000);
      expect(await screen.findByText(/AI Vocal & Sentiment Insights/i)).toBeInTheDocument();
      expect(screen.getByText('94%')).toBeInTheDocument();
      expect(screen.getByText('142')).toBeInTheDocument();
      expect(screen.getByText('Microservices')).toBeInTheDocument();
      expect(screen.getByText(/Articulated trade-offs with structured rationale/i)).toBeInTheDocument();

      // Can close modal
      const closeBtn = screen.getByRole('button', { name: /close recording modal/i });
      await user.click(closeBtn);
      expect(screen.queryByText(/AI Vocal & Sentiment Insights/i)).not.toBeInTheDocument();
    });
  });
});
