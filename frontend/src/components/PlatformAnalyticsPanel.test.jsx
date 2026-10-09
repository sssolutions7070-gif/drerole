import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import PlatformAnalyticsPanel from './PlatformAnalyticsPanel';
import { platformAnalyticsAPI } from '../services/api';

vi.mock('../services/api');

const trend = () => Array.from({ length: 30 }, (_, i) => ({ date: `2026-01-${String(i + 1).padStart(2, '0')}`, count: i }));

const overview = (overrides = {}) => ({
  users: { candidates: 10, recruiters: 2, admins: 1, total: 13 },
  jobPostings: { total: 5, pending: 1, approved: 3, rejected: 1, active: 4 },
  jobPostingsByExperienceLevel: { Senior: 3, Entry: 2 },
  jobPostingsByType: { 'Full-time': 4, Internship: 1 },
  applications: { total: 20, byStatus: { SHORTLISTED: 5 } },
  conversionFunnel: {
    submitted: 20, viewedByRecruiter: 15, shortlisted: 8, interviewScheduled: 4, offerSent: 2, offerAccepted: 1,
  },
  signupsByDay: trend(),
  applicationsByDay: trend(),
  timeToHire: { averageDays: 5.2, sampleSize: 2 },
  proctoringIntegrity: { totalProctoredSessions: 4, cleanSessions: 3, cleanPercentage: 75.0 },
  topMissingSkills: [{ skill: 'Docker', count: 6 }, { skill: 'Kubernetes', count: 3 }],
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
  });

  it('shows real time-to-hire and proctoring-integrity numbers computed from the backend, not a fixed placeholder', async () => {
    platformAnalyticsAPI.getOverview.mockResolvedValue({ data: overview() });

    render(<PlatformAnalyticsPanel />);

    expect(await screen.findByText(/Avg. Time-to-Hire/i)).toBeInTheDocument();
    expect(screen.getByText('5.2 Days')).toBeInTheDocument();
    expect(screen.getByText(/From 2 offers sent/i)).toBeInTheDocument();
    expect(screen.getByText('75% Clean')).toBeInTheDocument();
    expect(screen.getByText(/3 of 4 sessions clean/i)).toBeInTheDocument();
  });

  it('shows a dash instead of a fabricated number when there is no real data yet', async () => {
    platformAnalyticsAPI.getOverview.mockResolvedValue({
      data: overview({
        timeToHire: { averageDays: null, sampleSize: 0 },
        proctoringIntegrity: { totalProctoredSessions: 0, cleanSessions: 0, cleanPercentage: null },
      }),
    });

    render(<PlatformAnalyticsPanel />);

    await screen.findByText(/Avg. Time-to-Hire/i);
    expect(screen.getByText(/No offers sent yet/i)).toBeInTheDocument();
    expect(screen.getByText(/No proctored sessions yet/i)).toBeInTheDocument();
    expect(screen.queryByText(/Days$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/% Clean/i)).not.toBeInTheDocument();
  });

  it('shows the most commonly missing skills platform-wide', async () => {
    platformAnalyticsAPI.getOverview.mockResolvedValue({ data: overview() });

    render(<PlatformAnalyticsPanel />);

    expect(await screen.findByText(/Most commonly missing skills/i)).toBeInTheDocument();
    expect(screen.getByText('Docker')).toBeInTheDocument();
    expect(screen.getByText('(6)')).toBeInTheDocument();
    expect(screen.getByText('Kubernetes')).toBeInTheDocument();
  });

  it('hides the missing-skills section entirely when there is nothing to show', async () => {
    platformAnalyticsAPI.getOverview.mockResolvedValue({ data: overview({ topMissingSkills: [] }) });

    render(<PlatformAnalyticsPanel />);

    await screen.findByText(/hiring funnel/i);
    expect(screen.queryByText(/Most commonly missing skills/i)).not.toBeInTheDocument();
  });

  it('shows job postings broken down by experience level and type', async () => {
    platformAnalyticsAPI.getOverview.mockResolvedValue({ data: overview() });

    render(<PlatformAnalyticsPanel />);

    expect(await screen.findByText(/Job postings by experience level/i)).toBeInTheDocument();
    expect(screen.getByText('Senior: 3')).toBeInTheDocument();
    expect(screen.getByText('Entry: 2')).toBeInTheDocument();
    expect(screen.getByText(/Job postings by type/i)).toBeInTheDocument();
    expect(screen.getByText('Full-time: 4')).toBeInTheDocument();
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
