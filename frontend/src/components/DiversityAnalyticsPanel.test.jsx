import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import DiversityAnalyticsPanel from './DiversityAnalyticsPanel';
import api from '../services/api';

vi.mock('../services/api');

describe('DiversityAnalyticsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows a percentage breakdown for a reportable category', async () => {
    api.get.mockResolvedValue({
      data: {
        totalApplicants: 20,
        genderIdentity: { responseCount: 10, reportable: true, percentages: { Woman: 60, Man: 40 } },
        ethnicity: { responseCount: 1, reportable: false, percentages: {} },
        veteranStatus: { responseCount: 0, reportable: false, percentages: {} },
        disabilityStatus: { responseCount: 0, reportable: false, percentages: {} },
      },
    });

    render(<DiversityAnalyticsPanel recruiterId={5} />);

    expect(await screen.findByText('Woman')).toBeInTheDocument();
    expect(screen.getByText('60%')).toBeInTheDocument();
    expect(screen.getAllByText(/not enough responses yet/i).length).toBeGreaterThan(0);
  });

  it('shows an error when the request fails', async () => {
    api.get.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<DiversityAnalyticsPanel recruiterId={5} />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });

  it('calls the diversity-analytics endpoint for the given recruiter', async () => {
    api.get.mockResolvedValue({
      data: { totalApplicants: 0, genderIdentity: { responseCount: 0, reportable: false, percentages: {} } },
    });

    render(<DiversityAnalyticsPanel recruiterId={5} />);

    expect(api.get).toHaveBeenCalledWith('/recruiters/5/diversity-analytics');
  });
});
