import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TeamPanel from './TeamPanel';
import { recruiterTeamAPI } from '../services/api';

vi.mock('../services/api');

const member = (overrides = {}) => ({
  membershipId: 5, userId: 2, fullName: 'Priya Sharma', email: 'priya@example.com',
  role: 'HIRING_MANAGER', invitedAt: '2026-01-01T10:00:00', ...overrides,
});

const owner = () => ({
  membershipId: null, userId: 1, fullName: 'Asha Owner', email: 'asha@example.com', role: 'OWNER', invitedAt: null,
});

describe('TeamPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('lists the owner and invited members', async () => {
    recruiterTeamAPI.list.mockResolvedValue({ data: [owner(), member()] });

    render(<TeamPanel recruiterId={100} />);

    expect(await screen.findByText('Asha Owner')).toBeInTheDocument();
    expect(screen.getByText('Priya Sharma')).toBeInTheDocument();
    expect(screen.getByText('HIRING MANAGER')).toBeInTheDocument();
  });

  it('does not show a remove button for the owner', async () => {
    recruiterTeamAPI.list.mockResolvedValue({ data: [owner()] });

    render(<TeamPanel recruiterId={100} />);

    await screen.findByText('Asha Owner');
    expect(screen.queryByRole('button', { name: /remove asha owner/i })).not.toBeInTheDocument();
  });

  it('invites a new teammate', async () => {
    recruiterTeamAPI.list.mockResolvedValueOnce({ data: [owner()] }).mockResolvedValueOnce({ data: [owner(), member()] });
    recruiterTeamAPI.invite.mockResolvedValue({ data: member() });
    const user = userEvent.setup();

    render(<TeamPanel recruiterId={100} />);
    await screen.findByText('Asha Owner');

    await user.type(screen.getByPlaceholderText(/teammate@company.com/i), 'priya@example.com');
    await user.selectOptions(screen.getByRole('combobox'), 'HIRING_MANAGER');
    await user.click(screen.getByRole('button', { name: /invite/i }));

    expect(recruiterTeamAPI.invite).toHaveBeenCalledWith(100, 'priya@example.com', 'HIRING_MANAGER');
    expect(await screen.findByText('Priya Sharma')).toBeInTheDocument();
  });

  it('shows an error when inviting fails', async () => {
    recruiterTeamAPI.list.mockResolvedValue({ data: [owner()] });
    recruiterTeamAPI.invite.mockRejectedValue({ response: { data: { error: 'No DreamRole account found for x@example.com' } } });
    const user = userEvent.setup();

    render(<TeamPanel recruiterId={100} />);
    await screen.findByText('Asha Owner');

    await user.type(screen.getByPlaceholderText(/teammate@company.com/i), 'x@example.com');
    await user.click(screen.getByRole('button', { name: /invite/i }));

    expect(await screen.findByText(/no dreamrole account found/i)).toBeInTheDocument();
  });

  it('removes a team member', async () => {
    recruiterTeamAPI.list.mockResolvedValueOnce({ data: [owner(), member()] }).mockResolvedValueOnce({ data: [owner()] });
    recruiterTeamAPI.remove.mockResolvedValue({});
    const user = userEvent.setup();

    render(<TeamPanel recruiterId={100} />);
    await screen.findByText('Priya Sharma');

    await user.click(screen.getByRole('button', { name: /remove priya sharma/i }));

    expect(recruiterTeamAPI.remove).toHaveBeenCalledWith(100, 5);
  });
});
