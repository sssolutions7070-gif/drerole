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

describe('UserApplications rejection explanation', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
  });

  it('shows a "Why was I rejected?" button only for a rejected application', async () => {
    api.get.mockResolvedValue({ data: { applications: [application({ status: 'REJECTED' })] } });

    render(<UserApplications />);

    expect(await screen.findByRole('button', { name: /why was i rejected/i })).toBeInTheDocument();
  });

  it('does not show the button for an application still in progress', async () => {
    api.get.mockResolvedValue({ data: { applications: [application()] } });

    render(<UserApplications />);

    await screen.findByText('Backend Engineer');
    expect(screen.queryByRole('button', { name: /why was i rejected/i })).not.toBeInTheDocument();
  });

  it('opens the explanation modal and shows the AI-generated text', async () => {
    api.get.mockResolvedValue({ data: { applications: [application({ status: 'REJECTED' })] } });
    applicationStatusAPI.getRejectionExplanation.mockResolvedValue({
      data: { explanation: 'You matched on Java but the role needed Kubernetes.' },
    });
    const user = userEvent.setup();

    render(<UserApplications />);
    await user.click(await screen.findByRole('button', { name: /why was i rejected/i }));

    expect(await screen.findByText('You matched on Java but the role needed Kubernetes.')).toBeInTheDocument();
    expect(applicationStatusAPI.getRejectionExplanation).toHaveBeenCalledWith(1);
  });

  it('shows an error instead of silently failing', async () => {
    api.get.mockResolvedValue({ data: { applications: [application({ status: 'REJECTED' })] } });
    applicationStatusAPI.getRejectionExplanation.mockRejectedValue({
      response: { data: { error: "This application hasn't been rejected - there's nothing to explain yet" } },
    });
    const user = userEvent.setup();

    render(<UserApplications />);
    await user.click(await screen.findByRole('button', { name: /why was i rejected/i }));

    expect(await screen.findByText(/nothing to explain yet/i)).toBeInTheDocument();
  });
});

describe('UserApplications comparison', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
  });

  it('does not show a Compare button with fewer than two selections', async () => {
    api.get.mockResolvedValue({
      data: { applications: [application(), application({ id: 2, job: { title: 'Frontend Engineer', company: 'Beta' } })] },
    });
    const user = userEvent.setup();

    render(<UserApplications />);
    await user.click((await screen.findAllByLabelText(/select to compare/i))[0]);

    expect(screen.queryByRole('button', { name: /compare/i })).not.toBeInTheDocument();
  });

  it('shows the Compare button once two applications are selected and opens the modal', async () => {
    api.get.mockResolvedValue({
      data: {
        applications: [
          application({ id: 1, job: { title: 'Backend Engineer', company: 'Acme', salaryRange: '$100k-$120k' } }),
          application({ id: 2, job: { title: 'Frontend Engineer', company: 'Beta', salaryRange: '$90k-$110k' } }),
        ],
      },
    });
    const user = userEvent.setup();

    render(<UserApplications />);
    const checkboxes = await screen.findAllByLabelText(/select to compare/i);
    await user.click(checkboxes[0]);
    await user.click(checkboxes[1]);

    const compareButton = await screen.findByRole('button', { name: /compare \(2\)/i });
    await user.click(compareButton);

    expect(await screen.findByText('Compare Applications')).toBeInTheDocument();
    expect(screen.getByText('$100k-$120k')).toBeInTheDocument();
    expect(screen.getByText('$90k-$110k')).toBeInTheDocument();
  });

  it('limits selection to three applications', async () => {
    api.get.mockResolvedValue({
      data: {
        applications: [
          application({ id: 1 }),
          application({ id: 2, job: { title: 'Job 2', company: 'B' } }),
          application({ id: 3, job: { title: 'Job 3', company: 'C' } }),
          application({ id: 4, job: { title: 'Job 4', company: 'D' } }),
        ],
      },
    });
    const user = userEvent.setup();

    render(<UserApplications />);
    const checkboxes = await screen.findAllByLabelText(/select to compare/i);
    await user.click(checkboxes[0]);
    await user.click(checkboxes[1]);
    await user.click(checkboxes[2]);
    await user.click(checkboxes[3]);

    expect(checkboxes[3]).not.toBeChecked();
    expect(await screen.findByRole('button', { name: /compare \(3\)/i })).toBeInTheDocument();
  });
});
