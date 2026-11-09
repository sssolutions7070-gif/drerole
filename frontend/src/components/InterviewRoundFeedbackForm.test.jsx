import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import InterviewRoundFeedbackForm from './InterviewRoundFeedbackForm';
import { interviewRoundsAPI } from '../services/api';

vi.mock('../services/api');

describe('InterviewRoundFeedbackForm', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('starts blank when no feedback has been submitted yet', async () => {
    interviewRoundsAPI.getFeedback.mockResolvedValue({ data: null });

    render(<InterviewRoundFeedbackForm roundId={5000} onSaved={() => {}} onCancel={() => {}} />);

    expect(await screen.findByRole('button', { name: /submit feedback/i })).toBeInTheDocument();
  });

  it('pre-fills the form when feedback already exists', async () => {
    interviewRoundsAPI.getFeedback.mockResolvedValue({ data: { rating: 4, comment: 'Went well' } });

    render(<InterviewRoundFeedbackForm roundId={5000} onSaved={() => {}} onCancel={() => {}} />);

    expect(await screen.findByDisplayValue('Went well')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /update feedback/i })).toBeInTheDocument();
  });

  it('requires a star rating before submitting', async () => {
    interviewRoundsAPI.getFeedback.mockResolvedValue({ data: null });
    const user = userEvent.setup();

    render(<InterviewRoundFeedbackForm roundId={5000} onSaved={() => {}} onCancel={() => {}} />);
    await user.click(await screen.findByRole('button', { name: /submit feedback/i }));

    expect(await screen.findByText(/pick a star rating/i)).toBeInTheDocument();
    expect(interviewRoundsAPI.submitFeedback).not.toHaveBeenCalled();
  });

  it('submits the chosen rating and comment, then calls onSaved', async () => {
    interviewRoundsAPI.getFeedback.mockResolvedValue({ data: null });
    interviewRoundsAPI.submitFeedback.mockResolvedValue({ data: { rating: 3, comment: 'Fine' } });
    const onSaved = vi.fn();
    const user = userEvent.setup();

    render(<InterviewRoundFeedbackForm roundId={5000} onSaved={onSaved} onCancel={() => {}} />);
    await user.click(await screen.findByRole('button', { name: '3 stars' }));
    await user.type(screen.getByPlaceholderText(/anything about how this specific interview/i), 'Fine');
    await user.click(screen.getByRole('button', { name: /submit feedback/i }));

    await waitFor(() => expect(interviewRoundsAPI.submitFeedback).toHaveBeenCalledWith(5000, 3, 'Fine'));
    expect(onSaved).toHaveBeenCalledWith({ rating: 3, comment: 'Fine' });
  });

  it('shows a server error instead of silently failing', async () => {
    interviewRoundsAPI.getFeedback.mockResolvedValue({ data: null });
    interviewRoundsAPI.submitFeedback.mockRejectedValue({ response: { data: { error: "This round hasn't happened yet" } } });
    const user = userEvent.setup();

    render(<InterviewRoundFeedbackForm roundId={5000} onSaved={() => {}} onCancel={() => {}} />);
    await user.click(await screen.findByRole('button', { name: '1 star' }));
    await user.click(screen.getByRole('button', { name: /submit feedback/i }));

    expect(await screen.findByText("This round hasn't happened yet")).toBeInTheDocument();
  });
});
