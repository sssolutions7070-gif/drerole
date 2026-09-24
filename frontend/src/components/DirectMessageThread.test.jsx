import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DirectMessageThread from './DirectMessageThread';
import { directMessageAPI } from '../services/api';

vi.mock('../services/api');

describe('DirectMessageThread', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Recruiter' }));
  });

  it('shows an empty state when there are no messages yet', async () => {
    directMessageAPI.getThread.mockResolvedValue({ data: [] });

    render(<DirectMessageThread recruiterId={100} candidateUserId={50} title="Alice" onClose={() => {}} />);

    expect(await screen.findByText(/no messages yet/i)).toBeInTheDocument();
    expect(screen.getByText('Alice')).toBeInTheDocument();
  });

  it('lists existing messages, distinguishing mine from theirs', async () => {
    directMessageAPI.getThread.mockResolvedValue({
      data: [
        { id: 1, senderId: 1, body: 'Hi, are you open to new roles?', sentAt: '2026-01-01T10:00:00' },
        { id: 2, senderId: 50, body: 'Sure, tell me more', sentAt: '2026-01-01T10:05:00' },
      ],
    });

    render(<DirectMessageThread recruiterId={100} candidateUserId={50} onClose={() => {}} />);

    expect(await screen.findByText('Hi, are you open to new roles?')).toBeInTheDocument();
    expect(screen.getByText('Sure, tell me more')).toBeInTheDocument();
  });

  it('sends a new message', async () => {
    directMessageAPI.getThread.mockResolvedValue({ data: [] });
    directMessageAPI.send.mockResolvedValue({
      data: { id: 3, senderId: 1, body: 'Interested in chatting?', sentAt: '2026-01-01T10:10:00' },
    });
    const user = userEvent.setup();

    render(<DirectMessageThread recruiterId={100} candidateUserId={50} onClose={() => {}} />);
    await screen.findByText(/no messages yet/i);

    await user.type(screen.getByPlaceholderText(/type a message/i), 'Interested in chatting?');
    const sendButton = screen.getAllByRole('button').at(-1);
    await user.click(sendButton);

    expect(directMessageAPI.send).toHaveBeenCalledWith(100, 50, 'Interested in chatting?');
    expect(await screen.findByText('Interested in chatting?')).toBeInTheDocument();
  });

  it('shows an error and restores the draft when sending fails', async () => {
    directMessageAPI.getThread.mockResolvedValue({ data: [] });
    directMessageAPI.send.mockRejectedValue({ response: { data: { error: 'Recruiter not found' } } });
    const user = userEvent.setup();

    render(<DirectMessageThread recruiterId={100} candidateUserId={50} onClose={() => {}} />);
    await screen.findByText(/no messages yet/i);

    await user.type(screen.getByPlaceholderText(/type a message/i), 'hello');
    await user.click(screen.getAllByRole('button').at(-1));

    expect(await screen.findByText('Recruiter not found')).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/type a message/i)).toHaveValue('hello');
  });

  it('calls onClose when the close button is clicked', async () => {
    directMessageAPI.getThread.mockResolvedValue({ data: [] });
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(<DirectMessageThread recruiterId={100} candidateUserId={50} onClose={onClose} />);
    await screen.findByText(/no messages yet/i);

    const buttons = screen.getAllByRole('button');
    await user.click(buttons[0]);

    expect(onClose).toHaveBeenCalled();
  });
});
