import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TalentPoolPanel from './TalentPoolPanel';
import { talentPoolAPI } from '../services/api';

vi.mock('../services/api');

const candidate = (overrides = {}) => ({
  candidateId: 1, fullName: 'Alice Anderson', email: 'alice@example.com',
  applications: [{ applicationId: 100, jobTitle: 'Backend Engineer', status: 'SHORTLISTED', appliedAt: '2026-01-01' }],
  notes: null, tags: null, ...overrides,
});

describe('TalentPoolPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows a message when the pool is empty', async () => {
    talentPoolAPI.search.mockResolvedValue({ data: [] });

    render(<TalentPoolPanel recruiterId={10} />);

    expect(await screen.findByText(/no candidates found/i)).toBeInTheDocument();
  });

  it('lists candidates with their application status', async () => {
    talentPoolAPI.search.mockResolvedValue({ data: [candidate()] });

    render(<TalentPoolPanel recruiterId={10} />);

    expect(await screen.findByText('Alice Anderson')).toBeInTheDocument();
    expect(screen.getByText('alice@example.com')).toBeInTheDocument();
    expect(screen.getByText('Backend Engineer: SHORTLISTED')).toBeInTheDocument();
  });

  it('shows existing tags and notes', async () => {
    talentPoolAPI.search.mockResolvedValue({
      data: [candidate({ notes: 'Great communicator', tags: '["strong-backend","keep-warm"]' })]
    });

    render(<TalentPoolPanel recruiterId={10} />);

    expect(await screen.findByText('strong-backend')).toBeInTheDocument();
    expect(screen.getByText('keep-warm')).toBeInTheDocument();
    expect(screen.getByText('"Great communicator"')).toBeInTheDocument();
  });

  it('searches by the typed term', async () => {
    talentPoolAPI.search.mockResolvedValue({ data: [candidate()] });
    const user = userEvent.setup();

    render(<TalentPoolPanel recruiterId={10} />);
    await screen.findByText('Alice Anderson');

    await user.type(screen.getByPlaceholderText(/search by name/i), 'alice');
    await user.click(screen.getByRole('button', { name: /search/i }));

    expect(talentPoolAPI.search).toHaveBeenLastCalledWith(10, 'alice');
  });

  it('edits and saves a note as a JSON tag array', async () => {
    talentPoolAPI.search.mockResolvedValue({ data: [candidate()] });
    talentPoolAPI.upsertNote.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    render(<TalentPoolPanel recruiterId={10} />);
    await screen.findByText('Alice Anderson');

    await user.click(screen.getByRole('button', { name: /edit note/i }));
    await user.type(screen.getByPlaceholderText(/keep-warm/i), 'backend, senior');
    await user.type(screen.getByPlaceholderText(/private note about this candidate/i), 'Impressive system design answer');
    await user.click(screen.getByRole('button', { name: /^save$/i }));

    expect(talentPoolAPI.upsertNote).toHaveBeenCalledWith(
      10, 1, 'Impressive system design answer', JSON.stringify(['backend', 'senior'])
    );
  });

  it('shows an error when the pool fails to load', async () => {
    talentPoolAPI.search.mockRejectedValue({ response: { data: { error: 'Something went wrong' } } });

    render(<TalentPoolPanel recruiterId={10} />);

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
  });
});
