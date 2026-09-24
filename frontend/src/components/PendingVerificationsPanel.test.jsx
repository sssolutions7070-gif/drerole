import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PendingVerificationsPanel from './PendingVerificationsPanel';
import { adminVerificationAPI } from '../services/api';

vi.mock('../services/api');

const item = (overrides = {}) => ({
  recruiterId: 100, companyName: 'Acme', industry: 'Software', companyWebsite: 'https://acme.example',
  verificationDocumentPath: '/uploads/verifications/100_x.pdf', ...overrides,
});

describe('PendingVerificationsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
    window.open = vi.fn();
  });

  it('shows an empty state when nothing is pending', async () => {
    adminVerificationAPI.listPending.mockResolvedValue({ data: [] });

    render(<PendingVerificationsPanel />);

    expect(await screen.findByText(/no verification requests waiting/i)).toBeInTheDocument();
  });

  it('lists pending submissions', async () => {
    adminVerificationAPI.listPending.mockResolvedValue({ data: [item()] });

    render(<PendingVerificationsPanel />);

    expect(await screen.findByText('Acme')).toBeInTheDocument();
    expect(screen.getByText('Software')).toBeInTheDocument();
  });

  it('opens the document in a new tab', async () => {
    adminVerificationAPI.listPending.mockResolvedValue({ data: [item()] });
    adminVerificationAPI.downloadDocument.mockResolvedValue({ data: new Blob(['pdf']) });
    const user = userEvent.setup();

    render(<PendingVerificationsPanel />);
    await user.click(await screen.findByRole('button', { name: /view document/i }));

    expect(adminVerificationAPI.downloadDocument).toHaveBeenCalledWith(100);
    expect(window.open).toHaveBeenCalled();
  });

  it('approves a submission', async () => {
    adminVerificationAPI.listPending.mockResolvedValueOnce({ data: [item()] }).mockResolvedValueOnce({ data: [] });
    adminVerificationAPI.approve.mockResolvedValue({});
    const user = userEvent.setup();

    render(<PendingVerificationsPanel />);
    await user.click(await screen.findByRole('button', { name: /approve/i }));

    expect(adminVerificationAPI.approve).toHaveBeenCalledWith(100);
  });

  it('rejects a submission with a reason', async () => {
    adminVerificationAPI.listPending.mockResolvedValueOnce({ data: [item()] }).mockResolvedValueOnce({ data: [] });
    adminVerificationAPI.reject.mockResolvedValue({});
    const user = userEvent.setup();

    render(<PendingVerificationsPanel />);
    await user.click(await screen.findByRole('button', { name: /^reject$/i }));
    await user.type(screen.getByPlaceholderText(/reason/i), 'Blurry document');
    await user.click(screen.getByRole('button', { name: /confirm/i }));

    expect(adminVerificationAPI.reject).toHaveBeenCalledWith(100, 'Blurry document');
  });

  it('shows an error when loading fails', async () => {
    adminVerificationAPI.listPending.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<PendingVerificationsPanel />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});
