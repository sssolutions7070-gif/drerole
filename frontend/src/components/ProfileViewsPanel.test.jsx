import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import ProfileViewsPanel from './ProfileViewsPanel';
import { accountAPI } from '../services/api';

vi.mock('../services/api');

describe('ProfileViewsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows the view count and list of viewing companies', async () => {
    accountAPI.getProfileViews.mockResolvedValue({
      data: { count: 2, views: [{ companyName: 'Acme', viewedAt: '2026-10-01T00:00:00' }, { companyName: 'Globex', viewedAt: '2026-10-02T00:00:00' }] },
    });

    render(<ProfileViewsPanel userId={5} />);

    expect(await screen.findByText('2')).toBeInTheDocument();
    expect(screen.getByText('Acme')).toBeInTheDocument();
    expect(screen.getByText('Globex')).toBeInTheDocument();
  });

  it('prompts to enable open-to-work when there are no views', async () => {
    accountAPI.getProfileViews.mockResolvedValue({ data: { count: 0, views: [] } });

    render(<ProfileViewsPanel userId={5} />);

    expect(await screen.findByText(/Turn on "Open to work"/i)).toBeInTheDocument();
  });
});
