import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ApplicationCommentsPanel from './ApplicationCommentsPanel';
import api from '../services/api';

vi.mock('../services/api');

describe('ApplicationCommentsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 10, fullName: 'Priya' }));
  });

  it('shows existing notes with author and timestamp', async () => {
    api.get.mockResolvedValue({
      data: [
        { id: 1, authorUserId: 10, authorName: 'Priya', body: 'Strong candidate', createdAt: '2026-01-01T10:00:00' },
      ],
    });

    render(<ApplicationCommentsPanel applicationId={1} onClose={vi.fn()} />);

    expect(await screen.findByText('Strong candidate')).toBeInTheDocument();
    expect(screen.getByText('Priya')).toBeInTheDocument();
  });

  it('shows an empty state when there are no notes yet', async () => {
    api.get.mockResolvedValue({ data: [] });

    render(<ApplicationCommentsPanel applicationId={1} onClose={vi.fn()} />);

    expect(await screen.findByText(/no notes yet/i)).toBeInTheDocument();
  });

  it('posts a new note and appends it to the list', async () => {
    api.get.mockResolvedValue({ data: [] });
    api.post.mockResolvedValue({
      data: { id: 2, authorUserId: 10, authorName: 'Priya', body: 'Needs a follow-up call', createdAt: '2026-01-02T10:00:00' },
    });
    const user = userEvent.setup();

    render(<ApplicationCommentsPanel applicationId={1} onClose={vi.fn()} />);
    await screen.findByText(/no notes yet/i);

    await user.type(screen.getByPlaceholderText(/add a note/i), 'Needs a follow-up call');
    await user.click(screen.getByRole('button', { name: /post/i }));

    expect(api.post).toHaveBeenCalledWith('/applications/1/comments', { body: 'Needs a follow-up call' });
    expect(await screen.findByText('Needs a follow-up call')).toBeInTheDocument();
  });

  it('shows the delete button only for the current user\'s own notes', async () => {
    api.get.mockResolvedValue({
      data: [
        { id: 1, authorUserId: 10, authorName: 'Priya', body: 'Mine', createdAt: '2026-01-01T10:00:00' },
        { id: 2, authorUserId: 20, authorName: 'Dev', body: 'Theirs', createdAt: '2026-01-01T11:00:00' },
      ],
    });

    render(<ApplicationCommentsPanel applicationId={1} onClose={vi.fn()} />);
    await screen.findByText('Mine');

    expect(screen.getAllByLabelText('Delete note')).toHaveLength(1);
  });

  it('deletes a note when its author clicks delete', async () => {
    api.get.mockResolvedValue({
      data: [{ id: 1, authorUserId: 10, authorName: 'Priya', body: 'Mine', createdAt: '2026-01-01T10:00:00' }],
    });
    api.delete.mockResolvedValue({});
    const user = userEvent.setup();

    render(<ApplicationCommentsPanel applicationId={1} onClose={vi.fn()} />);
    await user.click(await screen.findByLabelText('Delete note'));

    expect(api.delete).toHaveBeenCalledWith('/applications/1/comments/1');
  });

  it('shows an error when loading comments fails', async () => {
    api.get.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<ApplicationCommentsPanel applicationId={1} onClose={vi.fn()} />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', async () => {
    api.get.mockResolvedValue({ data: [] });
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(<ApplicationCommentsPanel applicationId={1} onClose={onClose} />);
    await user.click(screen.getByLabelText('Close'));

    expect(onClose).toHaveBeenCalled();
  });
});
