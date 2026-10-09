import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import CandidateInsightsPanel from './CandidateInsightsPanel';
import { applicationStatusAPI } from '../services/api';

vi.mock('../services/api');

const insights = (overrides = {}) => ({
  totalApplications: 3,
  byStatus: { SHORTLISTED: 2, REJECTED: 1 },
  averageMatchPercentage: 72.5,
  topMissingSkills: [{ skill: 'Docker', count: 2 }, { skill: 'Kubernetes', count: 1 }],
  profileViewCount: 4,
  ...overrides,
});

describe('CandidateInsightsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows the headline stats', async () => {
    applicationStatusAPI.getUserInsights.mockResolvedValue({ data: insights() });

    render(<CandidateInsightsPanel userId={1} />);

    expect(await screen.findByText('Applications')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('72.5%')).toBeInTheDocument();
    expect(screen.getByText('Profile Views')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
  });

  it('shows a status breakdown of the candidate\'s own applications', async () => {
    applicationStatusAPI.getUserInsights.mockResolvedValue({ data: insights() });

    render(<CandidateInsightsPanel userId={1} />);

    expect(await screen.findByText(/where your applications stand/i)).toBeInTheDocument();
    expect(screen.getByText('Shortlisted: 2')).toBeInTheDocument();
    expect(screen.getByText('Rejected: 1')).toBeInTheDocument();
  });

  it('shows the skills most often missing across the candidate\'s applications', async () => {
    applicationStatusAPI.getUserInsights.mockResolvedValue({ data: insights() });

    render(<CandidateInsightsPanel userId={1} />);

    expect(await screen.findByText(/skills worth adding/i)).toBeInTheDocument();
    expect(screen.getByText('Docker')).toBeInTheDocument();
    expect(screen.getByText('(2)')).toBeInTheDocument();
  });

  it('hides the missing-skills section when there are none', async () => {
    applicationStatusAPI.getUserInsights.mockResolvedValue({ data: insights({ topMissingSkills: [] }) });

    render(<CandidateInsightsPanel userId={1} />);

    await screen.findByText('Applications');
    expect(screen.queryByText(/skills worth adding/i)).not.toBeInTheDocument();
  });

  it('shows an empty state for a candidate with no applications yet', async () => {
    applicationStatusAPI.getUserInsights.mockResolvedValue({ data: insights({ totalApplications: 0, byStatus: {}, topMissingSkills: [] }) });

    render(<CandidateInsightsPanel userId={1} />);

    expect(await screen.findByText(/no insights yet/i)).toBeInTheDocument();
  });

  it('shows an error message when the request fails', async () => {
    applicationStatusAPI.getUserInsights.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<CandidateInsightsPanel userId={1} />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});
