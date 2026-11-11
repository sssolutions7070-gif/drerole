import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OutreachSequenceModal from './OutreachSequenceModal';
import { outreachSequenceAPI } from '../services/api';

vi.mock('../services/api');

const candidate = { userId: 1, fullName: 'Alice Anderson' };

describe('OutreachSequenceModal', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('rejects submitting with no message filled in', async () => {
    const user = userEvent.setup();
    render(<OutreachSequenceModal recruiterId={100} candidate={candidate} onClose={vi.fn()} onStarted={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /start sequence/i }));

    expect(await screen.findByText(/add at least one step/i)).toBeInTheDocument();
    expect(outreachSequenceAPI.start).not.toHaveBeenCalled();
  });

  it('drops blank steps and submits the filled-in ones', async () => {
    outreachSequenceAPI.start.mockResolvedValue({ data: { id: 1 } });
    const onStarted = vi.fn();
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(<OutreachSequenceModal recruiterId={100} candidate={candidate} onClose={onClose} onStarted={onStarted} />);

    const textareas = screen.getAllByPlaceholderText(/message to send/i);
    await user.type(textareas[0], 'Hi there!');
    await user.click(screen.getByRole('button', { name: /start sequence/i }));

    expect(outreachSequenceAPI.start).toHaveBeenCalledWith(100, 1, [{ delayDays: 0, message: 'Hi there!' }]);
    expect(onStarted).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('adds and removes a step', async () => {
    const user = userEvent.setup();
    render(<OutreachSequenceModal recruiterId={100} candidate={candidate} onClose={vi.fn()} onStarted={vi.fn()} />);

    expect(screen.getAllByPlaceholderText(/message to send/i)).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: /add step/i }));
    expect(screen.getAllByPlaceholderText(/message to send/i)).toHaveLength(3);

    await user.click(screen.getByLabelText('Remove step 3'));
    expect(screen.getAllByPlaceholderText(/message to send/i)).toHaveLength(2);
  });

  it('shows an error when starting the sequence fails', async () => {
    outreachSequenceAPI.start.mockRejectedValue({ response: { data: { error: 'already active' } } });
    const user = userEvent.setup();

    render(<OutreachSequenceModal recruiterId={100} candidate={candidate} onClose={vi.fn()} onStarted={vi.fn()} />);

    const textareas = screen.getAllByPlaceholderText(/message to send/i);
    await user.type(textareas[0], 'Hi there!');
    await user.click(screen.getByRole('button', { name: /start sequence/i }));

    expect(await screen.findByText('already active')).toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(<OutreachSequenceModal recruiterId={100} candidate={candidate} onClose={onClose} onStarted={vi.fn()} />);
    await user.click(screen.getByLabelText('Close'));

    expect(onClose).toHaveBeenCalled();
  });
});
