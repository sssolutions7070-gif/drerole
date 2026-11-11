import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import ProfileStrengthPanel from './ProfileStrengthPanel';
import api from '../services/api';

vi.mock('../services/api');

describe('ProfileStrengthPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows the score and completed count', async () => {
    api.get.mockResolvedValue({
      data: {
        score: 75,
        resumeQualityScore: 80,
        checks: [
          { id: 'headline', passed: true, message: 'Headline is set.' },
          { id: 'bio', passed: false, message: 'Add a short bio so recruiters get a sense of who you are.' },
        ],
      },
    });

    render(<ProfileStrengthPanel userId={1} />);

    expect(await screen.findByText('75')).toBeInTheDocument();
    expect(screen.getByText('1 of 2 complete')).toBeInTheDocument();
  });

  it('sorts unfinished checks ahead of completed ones', async () => {
    api.get.mockResolvedValue({
      data: {
        score: 50,
        checks: [
          { id: 'headline', passed: true, message: 'Headline is set.' },
          { id: 'bio', passed: false, message: 'Add a short bio.' },
        ],
      },
    });

    render(<ProfileStrengthPanel userId={1} />);

    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Add a short bio.');
    expect(items[1]).toHaveTextContent('Headline is set.');
  });

  it('shows an error message instead of crashing when the request fails', async () => {
    api.get.mockRejectedValue(new Error('network error'));

    render(<ProfileStrengthPanel userId={1} />);

    expect(await screen.findByText('Failed to load profile strength')).toBeInTheDocument();
  });
});
