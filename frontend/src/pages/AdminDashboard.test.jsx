import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminDashboard from './AdminDashboard';
import api from '../services/api';

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
    render(<AdminDashboard />);

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
    render(<AdminDashboard />);

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
    render(<AdminDashboard />);

    await user.click(await screen.findByRole('button', { name: /Pending Jobs \(2\)/i }));
    await screen.findByText('Backend Engineer');

    expect(screen.queryByRole('button', { name: /Approve Selected/i })).not.toBeInTheDocument();
  });
});
