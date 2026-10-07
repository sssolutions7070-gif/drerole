import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ScorecardForm from './ScorecardForm';
import { scorecardAPI } from '../services/api';

vi.mock('../services/api');

describe('ScorecardForm', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('requires every rating and a recommendation before saving', async () => {
    const user = userEvent.setup();
    render(<ScorecardForm roundId={5000} existingScorecard={null} onSaved={() => {}} onCancel={() => {}} />);

    await user.click(screen.getByRole('button', { name: /save scorecard/i }));

    expect(await screen.findByText(/rate every category/i)).toBeInTheDocument();
    expect(scorecardAPI.submit).not.toHaveBeenCalled();
  });

  it('submits the chosen ratings and recommendation', async () => {
    scorecardAPI.submit.mockResolvedValue({ data: { id: 1, recommendation: 'YES' } });
    const onSaved = vi.fn();
    const user = userEvent.setup();
    render(<ScorecardForm roundId={5000} existingScorecard={null} onSaved={onSaved} onCancel={() => {}} />);

    const fours = screen.getAllByRole('button', { name: '4' });
    const fives = screen.getAllByRole('button', { name: '5' });
    await user.click(fours[0]);
    await user.click(fives[1]);
    await user.click(fives[2]);
    await user.selectOptions(screen.getByRole('combobox'), 'YES');
    await user.type(screen.getByPlaceholderText(/anything else/i), 'Solid round');
    await user.click(screen.getByRole('button', { name: /save scorecard/i }));

    expect(scorecardAPI.submit).toHaveBeenCalledWith(5000, {
      communicationRating: 4, technicalRating: 5, cultureFitRating: 5, recommendation: 'YES', notes: 'Solid round',
    });
    expect(onSaved).toHaveBeenCalledWith({ id: 1, recommendation: 'YES' });
  });

  it('pre-fills from an existing scorecard', () => {
    render(<ScorecardForm roundId={5000} existingScorecard={{
      communicationRating: 3, technicalRating: 4, cultureFitRating: 5, recommendation: 'STRONG_YES', notes: 'Great fit',
    }} onSaved={() => {}} onCancel={() => {}} />);

    expect(screen.getByDisplayValue('Great fit')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toHaveValue('STRONG_YES');
  });

  it('shows an error message when saving fails', async () => {
    scorecardAPI.submit.mockRejectedValue({ response: { data: { error: 'Interview round not found: 5000' } } });
    const user = userEvent.setup();
    render(<ScorecardForm roundId={5000} existingScorecard={null} onSaved={() => {}} onCancel={() => {}} />);

    const threes = screen.getAllByRole('button', { name: '3' });
    await user.click(threes[0]);
    await user.click(threes[1]);
    await user.click(threes[2]);
    await user.selectOptions(screen.getByRole('combobox'), 'NEUTRAL');
    await user.click(screen.getByRole('button', { name: /save scorecard/i }));

    expect(await screen.findByText('Interview round not found: 5000')).toBeInTheDocument();
  });

  it('calls onCancel when cancel is clicked', async () => {
    const onCancel = vi.fn();
    const user = userEvent.setup();
    render(<ScorecardForm roundId={5000} existingScorecard={null} onSaved={() => {}} onCancel={onCancel} />);

    await user.click(screen.getByRole('button', { name: /^cancel$/i }));

    expect(onCancel).toHaveBeenCalled();
  });

  it('generates an AI scorecard draft and populates the rubric and summary', async () => {
    scorecardAPI.generateDraft = vi.fn().mockResolvedValue({
      data: {
        communicationRating: 4,
        technicalRating: 5,
        cultureFitRating: 4,
        recommendation: 'STRONG_YES',
        notes: 'Candidate demonstrated exceptional problem solving in binary trees.',
        executiveSummary: 'Very strong engineer with clear architectural communication.',
        strengths: ['Algorithmic reasoning', 'System design'],
        areasForImprovement: ['Concurrency nuance'],
      },
    });

    const user = userEvent.setup();
    render(<ScorecardForm roundId={5000} existingScorecard={null} onSaved={() => {}} onCancel={() => {}} />);

    await user.click(screen.getByRole('button', { name: /auto-fill with ai/i }));

    expect(scorecardAPI.generateDraft).toHaveBeenCalledWith(5000, expect.any(Object));

    expect(await screen.findByText(/AI Evaluation Summary/i)).toBeInTheDocument();
    expect(screen.getByText(/Very strong engineer with clear architectural communication/i)).toBeInTheDocument();
    expect(screen.getByText(/✓ Algorithmic reasoning/i)).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toHaveValue('STRONG_YES');
    expect(screen.getByDisplayValue(/exceptional problem solving in binary trees/i)).toBeInTheDocument();
  });
});
