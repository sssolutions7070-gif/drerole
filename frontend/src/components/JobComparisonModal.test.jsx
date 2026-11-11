import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import JobComparisonModal from './JobComparisonModal';

const applications = [
  {
    id: 1,
    status: 'APPLICATION_SUBMITTED',
    matchPercentage: 85,
    job: {
      title: 'Backend Engineer',
      company: 'Acme',
      location: 'Remote',
      jobType: 'Full-time',
      experienceLevel: 'Mid',
      salaryRange: '$100k-$120k',
      requiredSkills: '["Java", "Spring"]',
      averageRating: 4.5,
      totalRatings: 10,
    },
  },
  {
    id: 2,
    status: 'SHORTLISTED',
    matchPercentage: 0,
    job: {
      title: 'Frontend Engineer',
      company: 'Beta',
      location: 'NYC',
      jobType: 'Contract',
      experienceLevel: 'Senior',
      salaryRange: null,
      requiredSkills: null,
      averageRating: null,
      totalRatings: 0,
    },
  },
];

describe('JobComparisonModal', () => {
  it('renders each selected application as a column with its job details', () => {
    render(<JobComparisonModal applications={applications} onClose={vi.fn()} />);

    expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('Frontend Engineer')).toBeInTheDocument();
    expect(screen.getByText('$100k-$120k')).toBeInTheDocument();
    expect(screen.getByText('Not specified')).toBeInTheDocument();
    expect(screen.getByText('Java')).toBeInTheDocument();
    expect(screen.getByText('Spring')).toBeInTheDocument();
    expect(screen.getByText('85%')).toBeInTheDocument();
    expect(screen.getByText('No ratings yet')).toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(<JobComparisonModal applications={applications} onClose={onClose} />);
    await user.click(screen.getByLabelText('Close'));

    expect(onClose).toHaveBeenCalled();
  });
});
