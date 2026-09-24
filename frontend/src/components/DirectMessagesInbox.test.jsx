import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DirectMessagesInbox from './DirectMessagesInbox';
import { directMessageAPI } from '../services/api';

vi.mock('../services/api');

describe('DirectMessagesInbox', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows an empty state for a recruiter with no conversations', async () => {
    directMessageAPI.listForRecruiter.mockResolvedValue({ data: [] });

    render(<DirectMessagesInbox role="recruiter" id={100} />);

    expect(await screen.findByText(/no conversations yet/i)).toBeInTheDocument();
  });

  it('lists a recruiter\'s threads by candidate', async () => {
    directMessageAPI.listForRecruiter.mockResolvedValue({
      data: [{ candidateUserId: 50, candidateName: 'Alice', lastMessage: 'Sure, tell me more', lastMessageAt: '2026-01-01T10:00:00' }],
    });

    render(<DirectMessagesInbox role="recruiter" id={100} />);

    expect(await screen.findByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Sure, tell me more')).toBeInTheDocument();
  });

  it('lists a candidate\'s threads by company', async () => {
    directMessageAPI.listForCandidate.mockResolvedValue({
      data: [{ recruiterId: 200, companyName: 'Acme', lastMessage: 'Interested in chatting?', lastMessageAt: '2026-01-01T10:00:00' }],
    });

    render(<DirectMessagesInbox role="candidate" id={50} />);

    expect(await screen.findByText('Acme')).toBeInTheDocument();
  });

  it('opens the thread when a conversation is clicked', async () => {
    directMessageAPI.listForRecruiter.mockResolvedValue({
      data: [{ candidateUserId: 50, candidateName: 'Alice', lastMessage: 'hi', lastMessageAt: '2026-01-01T10:00:00' }],
    });
    directMessageAPI.getThread.mockResolvedValue({ data: [] });
    const user = userEvent.setup();

    render(<DirectMessagesInbox role="recruiter" id={100} />);
    await user.click(await screen.findByText('Alice'));

    expect(directMessageAPI.getThread).toHaveBeenCalledWith(100, 50);
  });

  it('shows an error when loading threads fails', async () => {
    directMessageAPI.listForCandidate.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<DirectMessagesInbox role="candidate" id={50} />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});
