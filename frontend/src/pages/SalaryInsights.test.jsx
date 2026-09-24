import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
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

describe('SalaryInsights', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  const renderPage = () => render(
    <MemoryRouter>
      <SalaryInsights />
    </MemoryRouter>
  );

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
