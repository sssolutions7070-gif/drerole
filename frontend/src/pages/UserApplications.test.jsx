import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UserApplications from './UserApplications';
import api, { applicationStatusAPI } from '../services/api';

vi.mock('../services/api');

const application = (overrides = {}) => ({
  id: 1,
  status: 'APPLICATION_SUBMITTED',
  applicationDate: '2026-01-01T10:00:00',
  job: { title: 'Backend Engineer', company: 'Acme', location: 'Remote', jobType: 'Full-time' },
  ...overrides,
});

describe('UserApplications withdrawal', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
  });

  it('shows a Withdraw button for a withdrawable application', async () => {
    api.get.mockResolvedValue({ data: { applications: [application()] } });

    render(<UserApplications />);

    expect(await screen.findByRole('button', { name: /withdraw/i })).toBeInTheDocument();
  });

  it('hides the Withdraw button once an application is rejected', async () => {
    api.get.mockResolvedValue({ data: { applications: [application({ status: 'REJECTED' })] } });

    render(<UserApplications />);

    await screen.findByText('Backend Engineer');
    expect(screen.queryByRole('button', { name: /withdraw/i })).not.toBeInTheDocument();
  });

  it('hides the Withdraw button once an offer has been accepted', async () => {
    api.get.mockResolvedValue({ data: { applications: [application({ status: 'OFFER_ACCEPTED' })] } });

    render(<UserApplications />);

    await screen.findByText('Backend Engineer');
    expect(screen.queryByRole('button', { name: /withdraw/i })).not.toBeInTheDocument();
  });

  it('withdraws the application after confirming', async () => {
    api.get.mockResolvedValueOnce({ data: { applications: [application()] } })
      .mockResolvedValueOnce({ data: { applications: [application({ status: 'WITHDRAWN' })] } });
    applicationStatusAPI.withdrawApplication.mockResolvedValue({ data: {} });
    vi.stubGlobal('confirm', vi.fn(() => true));
    const user = userEvent.setup();

    render(<UserApplications />);
    await user.click(await screen.findByRole('button', { name: /withdraw/i }));

    expect(applicationStatusAPI.withdrawApplication).toHaveBeenCalledWith(1);
    vi.unstubAllGlobals();
  });

  it('does not withdraw when the confirmation is cancelled', async () => {
    api.get.mockResolvedValue({ data: { applications: [application()] } });
    vi.stubGlobal('confirm', vi.fn(() => false));
    const user = userEvent.setup();

    render(<UserApplications />);
    await user.click(await screen.findByRole('button', { name: /withdraw/i }));

    expect(applicationStatusAPI.withdrawApplication).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('shows an error when withdrawal fails', async () => {
    api.get.mockResolvedValue({ data: { applications: [application()] } });
    applicationStatusAPI.withdrawApplication.mockRejectedValue({
      response: { data: { error: "An application can't be withdrawn once it's offer accepted" } },
    });
    vi.stubGlobal('confirm', vi.fn(() => true));
    const user = userEvent.setup();

    render(<UserApplications />);
    await user.click(await screen.findByRole('button', { name: /withdraw/i }));

    expect(await screen.findByText(/can't be withdrawn/i)).toBeInTheDocument();
    vi.unstubAllGlobals();
  });
});
