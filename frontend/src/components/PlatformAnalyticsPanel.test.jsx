import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import PlatformAnalyticsPanel from './PlatformAnalyticsPanel';
import { platformAnalyticsAPI } from '../services/api';

vi.mock('../services/api');

const trend = () => Array.from({ length: 30 }, (_, i) => ({ date: `2026-01-${String(i + 1).padStart(2, '0')}`, count: i }));

const overview = (overrides = {}) => ({
  users: { candidates: 10, recruiters: 2, admins: 1, total: 13 },
  jobPostings: { total: 5, pending: 1, approved: 3, rejected: 1, active: 4 },
  applications: { total: 20, byStatus: { SHORTLISTED: 5 } },
  conversionFunnel: {
    submitted: 20, viewedByRecruiter: 15, shortlisted: 8, interviewScheduled: 4, offerSent: 2, offerAccepted: 1,
  },
  signupsByDay: trend(),
  applicationsByDay: trend(),
  ...overrides,
});

describe('PlatformAnalyticsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows the headline stat cards', async () => {
    platformAnalyticsAPI.getOverview.mockResolvedValue({ data: overview() });

    render(<PlatformAnalyticsPanel />);

    const candidatesCard = (await screen.findByText('Candidates')).closest('div').parentElement;
    expect(within(candidatesCard).getByText('10')).toBeInTheDocument();

    const jobsCard = screen.getByText('Job Postings').closest('div').parentElement;
    expect(within(jobsCard).getByText('5')).toBeInTheDocument();
  });

  it('shows the conversion funnel stages', async () => {
    platformAnalyticsAPI.getOverview.mockResolvedValue({ data: overview() });

    render(<PlatformAnalyticsPanel />);

    expect(await screen.findByText(/hiring funnel/i)).toBeInTheDocument();
    expect(screen.getByText('Shortlisted')).toBeInTheDocument();
    expect(screen.getByText('Offer accepted')).toBeInTheDocument();
    expect(screen.getByText(/Avg. Time-to-Hire/i)).toBeInTheDocument();
    expect(screen.getByText(/14.2 Days/i)).toBeInTheDocument();
    expect(screen.getByText(/99.1% Clean/i)).toBeInTheDocument();
  });

  it('shows the source-of-hire breakdown', async () => {
    platformAnalyticsAPI.getOverview.mockResolvedValue({
      data: overview({ applications: { total: 20, byStatus: {}, bySource: { DIRECT: 12, REFERRAL: 5, SILVER_MEDALIST: 3 } } }),
    });

    render(<PlatformAnalyticsPanel />);

    expect(await screen.findByText(/Source of hire/i)).toBeInTheDocument();
    expect(screen.getByText('Direct: 12')).toBeInTheDocument();
    expect(screen.getByText('Referral: 5')).toBeInTheDocument();
    expect(screen.getByText('Silver Medalist: 3')).toBeInTheDocument();
  });

  it('shows an error message when the request fails', async () => {
    platformAnalyticsAPI.getOverview.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<PlatformAnalyticsPanel />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});
