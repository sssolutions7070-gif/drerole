import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CandidateDiscoveryPanel from './CandidateDiscoveryPanel';
import { candidateDiscoveryAPI } from '../services/api';

vi.mock('../services/api');

const candidate = (overrides = {}) => ({
  userId: 1, fullName: 'Alice Anderson', headline: 'Backend Engineer', location: 'Remote',
  bio: 'Loves distributed systems', skills: ['Java', 'Spring'], hasResume: true, ...overrides,
});

describe('CandidateDiscoveryPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows a message when no candidates match', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [] });

    render(<CandidateDiscoveryPanel />);

    expect(await screen.findByText(/no open-to-work candidates match/i)).toBeInTheDocument();
  });

  it('lists candidates with their skills and resume badge', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [candidate()] });

    render(<CandidateDiscoveryPanel />);

    expect(await screen.findByText('Alice Anderson')).toBeInTheDocument();
    expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('Java')).toBeInTheDocument();
    expect(screen.getByText(/resume on file/i)).toBeInTheDocument();
  });

  it('searches with the entered filters', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [candidate()] });
    const user = userEvent.setup();

    render(<CandidateDiscoveryPanel />);
    await screen.findByText('Alice Anderson');

    await user.type(screen.getByPlaceholderText(/skill/i), 'React');
    await user.click(screen.getByRole('button', { name: /search/i }));

    expect(candidateDiscoveryAPI.discover).toHaveBeenLastCalledWith('React', undefined);
  });

  it('shows an error when the request fails', async () => {
    candidateDiscoveryAPI.discover.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<CandidateDiscoveryPanel />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});
