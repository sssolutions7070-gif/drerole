import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MockInterviewHistory from './MockInterviewHistory';
import { mockInterviewAPI } from '../services/api';

vi.mock('../services/api');

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.setItem('user', JSON.stringify({ id: 42, fullName: 'Test Candidate' }));
});

const history = (overrides = {}) => ({
  totalSessions: 2,
  totalQuestionsAnswered: 4,
  overallAverageScore: 3.5,
  sessions: [
    { sessionId: 2, jobTitle: 'Frontend Engineer', status: 'COMPLETED', createdAt: '2026-01-05', questionCount: 2, answeredCount: 2, averageScore: 4.0 },
    { sessionId: 1, jobTitle: 'Backend Engineer', status: 'COMPLETED', createdAt: '2026-01-01', questionCount: 2, answeredCount: 2, averageScore: 3.0 },
  ],
  scoreTrend: [
    { sessionId: 1, jobTitle: 'Backend Engineer', createdAt: '2026-01-01', averageScore: 3.0 },
    { sessionId: 2, jobTitle: 'Frontend Engineer', createdAt: '2026-01-05', averageScore: 4.0 },
  ],
  questionsToRevisit: [
    { sessionId: 1, jobTitle: 'Backend Engineer', questionText: 'Explain CAP theorem', answerText: 'idk', score: 1, feedback: 'Review distributed systems basics.', answeredAt: '2026-01-01' },
  ],
  ...overrides,
});

const renderPage = () => render(<MemoryRouter><MockInterviewHistory /></MemoryRouter>);

describe('MockInterviewHistory', () => {
  it('shows the headline stats', async () => {
    mockInterviewAPI.getHistory.mockResolvedValue({ data: history() });

    renderPage();

    expect(await screen.findByText('My Practice History')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument(); // total sessions
    expect(screen.getByText('4')).toBeInTheDocument(); // questions answered
    expect(screen.getByText('3.5 / 5')).toBeInTheDocument();
  });

  it('lists every session, newest first, linking to its own practice page', async () => {
    mockInterviewAPI.getHistory.mockResolvedValue({ data: history() });

    renderPage();

    const frontendLink = await screen.findByRole('link', { name: /Frontend Engineer/i });
    expect(frontendLink).toHaveAttribute('href', '/mock-interviews/2');
    expect(screen.getByRole('link', { name: /Backend Engineer/i })).toHaveAttribute('href', '/mock-interviews/1');
  });

  it('shows the lowest-scoring questions worth revisiting', async () => {
    mockInterviewAPI.getHistory.mockResolvedValue({ data: history() });

    renderPage();

    expect(await screen.findByText(/questions worth revisiting/i)).toBeInTheDocument();
    expect(screen.getByText('Explain CAP theorem')).toBeInTheDocument();
    expect(screen.getByText('Review distributed systems basics.')).toBeInTheDocument();
  });

  it('hides the revisit section when there is nothing to revisit', async () => {
    mockInterviewAPI.getHistory.mockResolvedValue({ data: history({ questionsToRevisit: [] }) });

    renderPage();

    await screen.findByText('My Practice History');
    expect(screen.queryByText(/questions worth revisiting/i)).not.toBeInTheDocument();
  });

  it('shows an empty state for a candidate with no practice sessions yet', async () => {
    mockInterviewAPI.getHistory.mockResolvedValue({
      data: { totalSessions: 0, totalQuestionsAnswered: 0, overallAverageScore: null, sessions: [], scoreTrend: [], questionsToRevisit: [] },
    });

    renderPage();

    expect(await screen.findByText(/no practice sessions yet/i)).toBeInTheDocument();
  });

  it('shows an error message when the request fails', async () => {
    mockInterviewAPI.getHistory.mockRejectedValue({ response: { data: { error: 'boom' } } });

    renderPage();

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});
