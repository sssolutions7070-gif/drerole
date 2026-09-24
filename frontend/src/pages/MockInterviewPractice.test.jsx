import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import MockInterviewPractice from './MockInterviewPractice';
import { mockInterviewAPI } from '../services/api';

vi.mock('../services/api');

describe('MockInterviewPractice', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Candidate' }));
  });

  const renderPage = () =>
    render(
      <MemoryRouter initialEntries={['/mock-interviews/500']}>
        <Routes>
          <Route path="/mock-interviews/:sessionId" element={<MockInterviewPractice />} />
        </Routes>
      </MemoryRouter>
    );

  it('shows the job title and unanswered questions', async () => {
    mockInterviewAPI.get.mockResolvedValue({
      data: {
        jobTitle: 'Backend Engineer',
        questions: [
          { id: 10, questionNumber: 1, questionText: 'Tell me about yourself.', answerText: null, feedback: null, score: null },
        ],
      },
    });

    renderPage();

    expect(await screen.findByText(/mock interview: backend engineer/i)).toBeInTheDocument();
    expect(screen.getByText(/tell me about yourself/i)).toBeInTheDocument();
    expect(screen.getByText('0 of 1 questions answered')).toBeInTheDocument();
  });

  it('submits an answer and shows the score and feedback', async () => {
    mockInterviewAPI.get
      .mockResolvedValueOnce({
        data: { jobTitle: 'Backend Engineer', questions: [
          { id: 10, questionNumber: 1, questionText: 'Tell me about yourself.', answerText: null, feedback: null, score: null },
        ] }
      })
      .mockResolvedValueOnce({
        data: { jobTitle: 'Backend Engineer', questions: [
          { id: 10, questionNumber: 1, questionText: 'Tell me about yourself.', answerText: 'I am a backend engineer.', feedback: 'Good start, add specifics.', score: 3 },
        ] }
      });
    mockInterviewAPI.answer.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderPage();
    await user.type(await screen.findByPlaceholderText(/type your answer/i), 'I am a backend engineer.');
    await user.click(screen.getByRole('button', { name: /submit answer/i }));

    expect(mockInterviewAPI.answer).toHaveBeenCalledWith(1, '500', 10, 'I am a backend engineer.');
    expect(await screen.findByText(/score: 3\/5/i)).toBeInTheDocument();
    expect(screen.getByText('Good start, add specifics.')).toBeInTheDocument();
  });

  it('requires an answer before submitting', async () => {
    mockInterviewAPI.get.mockResolvedValue({
      data: { jobTitle: 'Backend Engineer', questions: [
        { id: 10, questionNumber: 1, questionText: 'Q?', answerText: null, feedback: null, score: null },
      ] }
    });
    const user = userEvent.setup();

    renderPage();
    await user.click(await screen.findByRole('button', { name: /submit answer/i }));

    expect(await screen.findByText(/type an answer before submitting/i)).toBeInTheDocument();
    expect(mockInterviewAPI.answer).not.toHaveBeenCalled();
  });

  it('shows a completion banner once every question is answered', async () => {
    mockInterviewAPI.get.mockResolvedValue({
      data: { jobTitle: 'Backend Engineer', questions: [
        { id: 10, questionNumber: 1, questionText: 'Q?', answerText: 'An answer', feedback: 'Nice.', score: 4 },
      ] }
    });

    renderPage();

    expect(await screen.findByText(/you've answered every question/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /resubmit answer/i })).toBeInTheDocument();
  });

  it('shows an error when the session cannot be loaded', async () => {
    mockInterviewAPI.get.mockRejectedValue({ response: { data: { error: 'Mock interview session not found: 500' } } });

    renderPage();

    expect(await screen.findByText('Mock interview session not found: 500')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /go back/i })).toBeInTheDocument();
  });
});
