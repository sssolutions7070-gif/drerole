import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import SalaryInsights from './SalaryInsights';
import { salaryInsightsAPI } from '../services/api';

vi.mock('../services/api');

const insights = (overrides = {}) => ({
  matchingPostings: 2,
  overall: { count: 2, minSalary: 80000, maxSalary: 120000, averageSalary: 100000, medianSalary: 105000 },
  byExperienceLevel: {
    Senior: { count: 1, minSalary: 100000, maxSalary: 120000, averageSalary: 110000, medianSalary: 110000 },
    Entry: { count: 1, minSalary: 80000, maxSalary: 80000, averageSalary: 80000, medianSalary: 80000 },
  },
  ...overrides,
});

const renderPage = () => render(
  <MemoryRouter>
    <SalaryInsights />
  </MemoryRouter>
);

describe('SalaryInsights', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads and shows overall stats on mount', async () => {
    salaryInsightsAPI.get.mockResolvedValue({ data: insights() });

    renderPage();

    expect(await screen.findByText('$100,000')).toBeInTheDocument();
    expect(screen.getByText(/based on 2 matching job postings/i)).toBeInTheDocument();
  });

  it('shows the per-experience-level breakdown', async () => {
    salaryInsightsAPI.get.mockResolvedValue({ data: insights() });

    renderPage();

    const section = within((await screen.findByText(/by experience level/i)).closest('div'));
    expect(section.getByText('Senior')).toBeInTheDocument();
    expect(section.getByText('Entry')).toBeInTheDocument();
  });

  it('shows an empty state when nothing matches', async () => {
    salaryInsightsAPI.get.mockResolvedValue({
      data: { matchingPostings: 0, overall: { count: 0 }, byExperienceLevel: {} },
    });

    renderPage();

    expect(await screen.findByText(/no posted salary data matches/i)).toBeInTheDocument();
  });

  it('re-fetches with the entered filters on search', async () => {
    salaryInsightsAPI.get.mockResolvedValue({ data: insights() });
    const user = userEvent.setup();

    renderPage();
    await screen.findByText('$100,000');

    await user.type(screen.getByPlaceholderText(/job title/i), 'Backend Engineer');
    await user.click(screen.getByRole('button', { name: /search/i }));

    expect(salaryInsightsAPI.get).toHaveBeenLastCalledWith('Backend Engineer', undefined, undefined);
  });

  it('shows an error message when the request fails', async () => {
    salaryInsightsAPI.get.mockRejectedValue(new Error('network error'));

    renderPage();

    expect(await screen.findByText(/failed to load salary insights/i)).toBeInTheDocument();
  });
});

describe('SalaryInsights - negotiation assistant', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    salaryInsightsAPI.get.mockResolvedValue({ data: insights() });
  });

  it('prompts a signed-out visitor to sign in instead of showing the form', async () => {
    renderPage();

    expect(await screen.findByText(/negotiation assistant/i)).toBeInTheDocument();
    expect(screen.getByText(/sign in to get personalized negotiation advice/i)).toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/e\.g\. \$95,000/i)).not.toBeInTheDocument();
  });

  it('lets a signed-in candidate request and see negotiation advice', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha Rao' }));
    salaryInsightsAPI.getNegotiationAdvice.mockResolvedValue({
      data: {
        assessment: 'This offer is below the market median.',
        suggestedTargetMin: 105000,
        suggestedTargetMax: 125000,
        talkingPoints: ['Cite the market median', 'Ask directly for more'],
        openingLine: "Thanks for the offer, I'd like to discuss compensation.",
      },
    });
    const user = userEvent.setup();

    renderPage();
    await screen.findByText(/negotiation assistant/i);

    await user.type(screen.getByPlaceholderText(/e\.g\. \$95,000/i), '$95,000');
    await user.click(screen.getByRole('button', { name: /get negotiation advice/i }));

    await waitFor(() => expect(salaryInsightsAPI.getNegotiationAdvice).toHaveBeenCalledWith(
      undefined, undefined, undefined, '$95,000'));
    expect(await screen.findByText('This offer is below the market median.')).toBeInTheDocument();
    expect(screen.getByText('Cite the market median')).toBeInTheDocument();
    expect(screen.getByText(/thanks for the offer/i)).toBeInTheDocument();
  });

  it('shows an error instead of silently failing', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha Rao' }));
    salaryInsightsAPI.getNegotiationAdvice.mockRejectedValue({ response: { data: { error: 'AI service unavailable' } } });
    const user = userEvent.setup();

    renderPage();
    await screen.findByText(/negotiation assistant/i);

    await user.click(screen.getByRole('button', { name: /get negotiation advice/i }));

    expect(await screen.findByText('AI service unavailable')).toBeInTheDocument();
  });
});
