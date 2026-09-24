import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import SkillBadgesPanel from './SkillBadgesPanel';
import { skillBadgeAPI } from '../services/api';

vi.mock('../services/api');

describe('SkillBadgesPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows an encouragement message when there are no badges yet', async () => {
    skillBadgeAPI.list.mockResolvedValue({ data: [] });

    render(<SkillBadgesPanel userId={1} />);

    expect(await screen.findByText(/pass an aptitude or dream role test/i)).toBeInTheDocument();
  });

  it('lists earned badges with their source and score', async () => {
    skillBadgeAPI.list.mockResolvedValue({
      data: [
        { id: 1, badgeName: 'Logic Test', sourceType: 'APTITUDE_TEST', score: 85, earnedAt: '2026-01-01T00:00:00' },
        { id: 2, badgeName: 'Frontend Mastery', sourceType: 'DREAM_ROLE_TEST', score: 92, earnedAt: '2026-02-01T00:00:00' },
      ]
    });

    render(<SkillBadgesPanel userId={1} />);

    expect(await screen.findByText('Logic Test')).toBeInTheDocument();
    expect(screen.getByText('Frontend Mastery')).toBeInTheDocument();
    expect(screen.getByText(/Aptitude Test - 85%/)).toBeInTheDocument();
    expect(screen.getByText(/Dream Role Test - 92%/)).toBeInTheDocument();
  });

  it('renders nothing distressing when the request fails, just an empty state', async () => {
    skillBadgeAPI.list.mockRejectedValue(new Error('network error'));

    render(<SkillBadgesPanel userId={1} />);

    expect(await screen.findByText(/pass an aptitude or dream role test/i)).toBeInTheDocument();
  });
});
