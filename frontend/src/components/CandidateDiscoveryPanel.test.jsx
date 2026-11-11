import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CandidateDiscoveryPanel from './CandidateDiscoveryPanel';
import { candidateDiscoveryAPI, directMessageAPI } from '../services/api';

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
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    expect(candidateDiscoveryAPI.discover).toHaveBeenLastCalledWith('React', undefined);
  });

  it('shows an error when the request fails', async () => {
    candidateDiscoveryAPI.discover.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<CandidateDiscoveryPanel />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });

  it('searches with a natural-language query', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [] });
    candidateDiscoveryAPI.aiSearch.mockResolvedValue({ data: [candidate()] });
    const user = userEvent.setup();

    render(<CandidateDiscoveryPanel />);
    await screen.findByText(/no open-to-work candidates match/i);

    await user.type(screen.getByPlaceholderText(/ask in plain english/i), 'senior react dev in pune');
    await user.click(screen.getByRole('button', { name: /ai search/i }));

    expect(candidateDiscoveryAPI.aiSearch).toHaveBeenCalledWith('senior react dev in pune');
    expect(await screen.findByText('Alice Anderson')).toBeInTheDocument();
  });

  it('shows an error when the AI search fails', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [] });
    candidateDiscoveryAPI.aiSearch.mockRejectedValue({ response: { data: { error: "AI candidate search isn't configured on this server" } } });
    const user = userEvent.setup();

    render(<CandidateDiscoveryPanel />);
    await screen.findByText(/no open-to-work candidates match/i);

    await user.type(screen.getByPlaceholderText(/ask in plain english/i), 'react dev');
    await user.click(screen.getByRole('button', { name: /ai search/i }));

    expect(await screen.findByText(/isn't configured/i)).toBeInTheDocument();
  });

  it('opens a direct-message thread for a candidate', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [candidate()] });
    directMessageAPI.getThread.mockResolvedValue({ data: [] });
    const user = userEvent.setup();

    render(<CandidateDiscoveryPanel recruiterId={100} />);
    await user.click(await screen.findByRole('button', { name: /message/i }));

    expect(await screen.findByText(/no messages yet/i)).toBeInTheDocument();
    expect(directMessageAPI.getThread).toHaveBeenCalledWith(100, 1);
  });

  it('displays saved searches and allows saving a new search', async () => {
    const { savedCandidateSearchAPI } = await import('../services/api');
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [candidate()] });
    savedCandidateSearchAPI.list.mockResolvedValue({
      data: [{ id: 1, name: 'Pune Reacts', skillFilter: 'React', locationFilter: 'Pune', alertEnabled: true }]
    });
    savedCandidateSearchAPI.create.mockResolvedValue({ data: { id: 2, name: 'Java Devs' } });

    const user = userEvent.setup();
    render(<CandidateDiscoveryPanel recruiterId={100} />);

    expect(await screen.findByText('Pune Reacts')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/skill/i), 'Java');
    const saveBtn = screen.getByRole('button', { name: /Save Search & Alert/i });
    await user.click(saveBtn);

    expect(screen.getByText('Save Candidate Search')).toBeInTheDocument();
    const saveTrackBtn = screen.getByRole('button', { name: /Save & Track/i });
    await user.click(saveTrackBtn);

    expect(savedCandidateSearchAPI.create).toHaveBeenCalledWith(100, expect.objectContaining({
      name: expect.any(String),
      alertEnabled: true
    }));
  });

  it('records a profile view and opens a detail modal when View Profile is clicked', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [candidate()] });
    candidateDiscoveryAPI.recordView.mockResolvedValue({});

    const user = userEvent.setup();
    render(<CandidateDiscoveryPanel />);

    await user.click(await screen.findByRole('button', { name: /View Profile/i }));

    expect(candidateDiscoveryAPI.recordView).toHaveBeenCalledWith(1);
    expect(screen.getAllByText('Loves distributed systems').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Alice Anderson').length).toBeGreaterThanOrEqual(1);
  });

  it('does not block opening the profile modal when recording the view fails', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [candidate()] });
    candidateDiscoveryAPI.recordView.mockRejectedValue(new Error('network error'));

    const user = userEvent.setup();
    render(<CandidateDiscoveryPanel />);

    await user.click(await screen.findByRole('button', { name: /View Profile/i }));

    expect((await screen.findAllByText('Loves distributed systems')).length).toBeGreaterThanOrEqual(1);
  });

  it('shows a video intro badge and plays the video in the profile modal', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({
      data: [candidate({ videoIntroUrl: '/uploads/video-intros/1_abc.mp4' })],
    });
    candidateDiscoveryAPI.recordView.mockResolvedValue({});

    const user = userEvent.setup();
    render(<CandidateDiscoveryPanel />);

    expect(await screen.findByText(/video intro/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /View Profile/i }));

    expect(screen.getByTestId('candidate-video-intro')).toBeInTheDocument();
  });

  it('does not show a video intro badge when the candidate has none', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [candidate()] });

    render(<CandidateDiscoveryPanel />);

    await screen.findByText('Alice Anderson');
    expect(screen.queryByText(/video intro/i)).not.toBeInTheDocument();
  });

  it('can message a candidate directly from the profile modal', async () => {
    candidateDiscoveryAPI.discover.mockResolvedValue({ data: [candidate()] });
    candidateDiscoveryAPI.recordView.mockResolvedValue({});
    const { directMessageAPI: dmAPI } = await import('../services/api');
    dmAPI.getThread.mockResolvedValue({ data: [] });

    const user = userEvent.setup();
    render(<CandidateDiscoveryPanel recruiterId={100} />);

    await user.click(await screen.findByRole('button', { name: /View Profile/i }));
    await user.click(screen.getByRole('button', { name: /Message Candidate/i }));

    expect(dmAPI.getThread).toHaveBeenCalledWith(100, 1);
  });
});
