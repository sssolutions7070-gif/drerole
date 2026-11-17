import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import AdminDashboard from './AdminDashboard';
import api from '../services/api';

// Simulates another page (e.g. the voice assistant) navigating here a second time with a new
// initialTab, while AdminDashboard stays mounted the whole time (same route, no remount) - the
// scenario that caught a real bug: reading location.state only in useState's initializer missed
// this entirely, since that initializer runs once and never again.
const DeepLinkHarness = ({ tab }) => {
  const navigate = useNavigate();
  return (
    <>
      <button onClick={() => navigate('/admin/dashboard', { state: { initialTab: tab } })}>
        Simulate deep link to {tab}
      </button>
      <AdminDashboard />
    </>
  );
};

vi.mock('../services/api');

const pendingJobs = [
  { id: 1, title: 'Backend Engineer', company: 'Acme', location: 'Remote', jobType: 'Full-time', description: 'd1' },
  { id: 2, title: 'Frontend Engineer', company: 'Acme', location: 'Remote', jobType: 'Full-time', description: 'd2' },
];

describe('AdminDashboard - bulk moderation of pending jobs', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.confirm = vi.fn(() => true);
    api.get.mockImplementation((url) => {
      if (url === '/admin/dashboard/stats') return Promise.resolve({ data: {} });
      if (url === '/admin/jobs/pending') return Promise.resolve({ data: pendingJobs });
      if (url === '/admin/company-reviews/pending') return Promise.resolve({ data: [] });
      if (url === '/admin/interview-experiences/pending') return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    api.put.mockResolvedValue({ data: { message: 'ok' } });
  });

  it('bulk-approves selected pending jobs', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><AdminDashboard /></MemoryRouter>);

    await user.click(await screen.findByRole('button', { name: /Pending Jobs \(2\)/i }));
    await screen.findByText('Backend Engineer');

    const selectAll = screen.getByLabelText(/Select all pending jobs/i);
    await user.click(selectAll);

    const approveBtn = screen.getByRole('button', { name: /Approve Selected/i });
    await user.click(approveBtn);

    expect(window.confirm).toHaveBeenCalledWith('Approve 2 selected job posting(s)?');
    await waitFor(() => {
      expect(api.put).toHaveBeenCalledWith('/admin/jobs/1/approve');
      expect(api.put).toHaveBeenCalledWith('/admin/jobs/2/approve');
    });
    expect(await screen.findByText(/2 jobs processed/i)).toBeInTheDocument();
  });

  it('bulk-rejects selected pending jobs with a shared reason', async () => {
    const user = userEvent.setup();
    window.prompt = vi.fn(() => 'Looks spammy');
    render(<MemoryRouter><AdminDashboard /></MemoryRouter>);

    await user.click(await screen.findByRole('button', { name: /Pending Jobs \(2\)/i }));
    const jobCheckbox = await screen.findByLabelText('Select Backend Engineer');
    await user.click(jobCheckbox);

    await user.click(screen.getByRole('button', { name: /Reject Selected/i }));

    await waitFor(() => {
      expect(api.put).toHaveBeenCalledWith('/admin/jobs/1/reject', { reason: 'Looks spammy' });
    });
    expect(api.put).not.toHaveBeenCalledWith('/admin/jobs/2/reject', expect.anything());
  });

  it('does not show bulk buttons until something is selected', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><AdminDashboard /></MemoryRouter>);

    await user.click(await screen.findByRole('button', { name: /Pending Jobs \(2\)/i }));
    await screen.findByText('Backend Engineer');

    expect(screen.queryByRole('button', { name: /Approve Selected/i })).not.toBeInTheDocument();
  });
});

describe('AdminDashboard - fraud risk badges on pending jobs', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows a high-risk badge and its flags for a job that scores high', async () => {
    const user = userEvent.setup();
    api.get.mockImplementation((url) => {
      if (url === '/admin/dashboard/stats') return Promise.resolve({ data: {} });
      if (url === '/admin/jobs/pending') return Promise.resolve({ data: pendingJobs });
      if (url === '/admin/jobs/pending/fraud-scores') return Promise.resolve({
        data: {
          1: { riskScore: 0, riskLevel: 'LOW', flags: [] },
          2: { riskScore: 55, riskLevel: 'HIGH', flags: [{ reason: 'Asks the candidate to pay a fee to proceed' }] },
        },
      });
      return Promise.resolve({ data: [] });
    });

    render(<MemoryRouter><AdminDashboard /></MemoryRouter>);
    await user.click(await screen.findByRole('button', { name: /Pending Jobs \(2\)/i }));

    expect(await screen.findByText('High fraud risk')).toBeInTheDocument();
    expect(screen.getByText('Asks the candidate to pay a fee to proceed')).toBeInTheDocument();
    expect(screen.queryByText('Possible fraud risk')).not.toBeInTheDocument();
  });

  it('shows no badge for jobs scored low risk', async () => {
    const user = userEvent.setup();
    api.get.mockImplementation((url) => {
      if (url === '/admin/dashboard/stats') return Promise.resolve({ data: {} });
      if (url === '/admin/jobs/pending') return Promise.resolve({ data: pendingJobs });
      if (url === '/admin/jobs/pending/fraud-scores') return Promise.resolve({
        data: {
          1: { riskScore: 0, riskLevel: 'LOW', flags: [] },
          2: { riskScore: 0, riskLevel: 'LOW', flags: [] },
        },
      });
      return Promise.resolve({ data: [] });
    });

    render(<MemoryRouter><AdminDashboard /></MemoryRouter>);
    await user.click(await screen.findByRole('button', { name: /Pending Jobs \(2\)/i }));
    await screen.findByText('Backend Engineer');

    expect(screen.queryByText(/fraud risk/i)).not.toBeInTheDocument();
  });

  it('does not break the pending-jobs list if the fraud-scores request fails', async () => {
    const user = userEvent.setup();
    api.get.mockImplementation((url) => {
      if (url === '/admin/dashboard/stats') return Promise.resolve({ data: {} });
      if (url === '/admin/jobs/pending') return Promise.resolve({ data: pendingJobs });
      if (url === '/admin/jobs/pending/fraud-scores') return Promise.reject(new Error('boom'));
      return Promise.resolve({ data: [] });
    });

    render(<MemoryRouter><AdminDashboard /></MemoryRouter>);
    await user.click(await screen.findByRole('button', { name: /Pending Jobs \(2\)/i }));

    expect(await screen.findByText('Backend Engineer')).toBeInTheDocument();
  });
});

describe('AdminDashboard - deep-linking to a tab without a remount', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.get.mockResolvedValue({ data: [] });
  });

  it('switches tabs when re-navigated to the same route with a new initialTab', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/admin/dashboard']}>
        <DeepLinkHarness tab="pending-reviews" />
      </MemoryRouter>
    );

    // Starts on the default tab - the pending-reviews heading isn't rendered yet.
    await screen.findByRole('button', { name: /Simulate deep link/i });
    expect(screen.queryByText(/Pending Company Reviews \(/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Simulate deep link/i }));

    expect(await screen.findByText(/Pending Company Reviews \(/i)).toBeInTheDocument();
  });
});

describe('AdminDashboard - CSV exports', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.get.mockImplementation((url) => {
      if (url === '/admin/dashboard/stats') return Promise.resolve({ data: {} });
      if (url === '/admin/users') return Promise.resolve({ data: [{ id: 1, fullName: 'Asha Rao', email: 'asha@example.com', roleId: 1, role: 'ROLE_USER', isActive: true }] });
      if (url === '/admin/jobs') return Promise.resolve({ data: [{ id: 1, title: 'Backend Engineer', company: 'Acme' }] });
      if (url === '/admin/applications') return Promise.resolve({ data: [] });
      if (url === '/admin/export/users.csv') return Promise.resolve({ data: 'ID,Full Name\n1,Asha Rao\n' });
      return Promise.resolve({ data: [] });
    });
  });

  const renderOnTab = (tab) => render(
    <MemoryRouter initialEntries={[{ pathname: '/admin/dashboard', state: { initialTab: tab } }]}>
      <AdminDashboard />
    </MemoryRouter>
  );

  it('downloads a users CSV when "Export CSV" is clicked on the Users tab', async () => {
    const user = userEvent.setup();
    renderOnTab('users');

    await screen.findByText(/All Users/i);
    await user.click(screen.getByRole('button', { name: /export csv/i }));

    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/admin/export/users.csv', { responseType: 'blob' }));
  });

  it('shows an "Export CSV" button on the Jobs tab', async () => {
    renderOnTab('jobs');

    await screen.findByText(/All Jobs/i);
    expect(screen.getByRole('button', { name: /export csv/i })).toBeInTheDocument();
  });

  it('shows an "Export CSV" button on the Applications tab', async () => {
    renderOnTab('applications');

    await screen.findByText(/All Applications/i);
    expect(screen.getByRole('button', { name: /export csv/i })).toBeInTheDocument();
  });
});
