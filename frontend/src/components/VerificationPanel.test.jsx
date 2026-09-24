import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import VerificationPanel from './VerificationPanel';
import { recruiterVerificationAPI } from '../services/api';

vi.mock('../services/api');

describe('VerificationPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows "Not submitted" and an upload form when nothing has been submitted', async () => {
    recruiterVerificationAPI.getStatus.mockResolvedValue({ data: { verificationStatus: 'NONE' } });

    render(<VerificationPanel recruiterId={100} />);

    expect(await screen.findByText('Not submitted')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /submit for review/i })).toBeInTheDocument();
  });

  it('shows "Pending review" and no upload form while awaiting review', async () => {
    recruiterVerificationAPI.getStatus.mockResolvedValue({ data: { verificationStatus: 'PENDING' } });

    render(<VerificationPanel recruiterId={100} />);

    expect(await screen.findByText('Pending review')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /submit for review/i })).not.toBeInTheDocument();
  });

  it('shows "Verified" once approved', async () => {
    recruiterVerificationAPI.getStatus.mockResolvedValue({ data: { verificationStatus: 'VERIFIED' } });

    render(<VerificationPanel recruiterId={100} />);

    expect(await screen.findByText('Verified')).toBeInTheDocument();
  });

  it('shows the rejection reason and lets the recruiter resubmit', async () => {
    recruiterVerificationAPI.getStatus.mockResolvedValue({
      data: { verificationStatus: 'REJECTED', verificationRejectionReason: 'Document was blurry' },
    });

    render(<VerificationPanel recruiterId={100} />);

    expect(await screen.findByText(/document was blurry/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /submit for review/i })).toBeInTheDocument();
  });

  it('submits a selected file', async () => {
    recruiterVerificationAPI.getStatus.mockResolvedValue({ data: { verificationStatus: 'NONE' } });
    recruiterVerificationAPI.submit.mockResolvedValue({ data: { verificationStatus: 'PENDING' } });
    const user = userEvent.setup();

    render(<VerificationPanel recruiterId={100} />);
    await screen.findByText('Not submitted');

    const file = new File(['content'], 'registration.pdf', { type: 'application/pdf' });
    const input = document.querySelector('input[type="file"]');
    await user.upload(input, file);
    await user.click(screen.getByRole('button', { name: /submit for review/i }));

    expect(recruiterVerificationAPI.submit).toHaveBeenCalledWith(100, file);
    expect(await screen.findByText('Pending review')).toBeInTheDocument();
  });

  it('shows an error when submission fails', async () => {
    recruiterVerificationAPI.getStatus.mockResolvedValue({ data: { verificationStatus: 'NONE' } });
    recruiterVerificationAPI.submit.mockRejectedValue({ response: { data: { error: 'Only the account owner can submit verification documents' } } });
    const user = userEvent.setup();

    render(<VerificationPanel recruiterId={100} />);
    await screen.findByText('Not submitted');

    const file = new File(['content'], 'registration.pdf', { type: 'application/pdf' });
    await user.upload(document.querySelector('input[type="file"]'), file);
    await user.click(screen.getByRole('button', { name: /submit for review/i }));

    expect(await screen.findByText(/only the account owner/i)).toBeInTheDocument();
  });
});
