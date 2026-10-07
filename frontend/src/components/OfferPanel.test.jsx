import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OfferPanel from './OfferPanel';
import { offerAPI } from '../services/api';

vi.mock('../services/api');

describe('OfferPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  describe('recruiter view', () => {
    it('shows a form to send a new offer when none exists yet', async () => {
      offerAPI.get.mockRejectedValue({ response: { data: { error: 'No offer has been sent for application 1000' } } });

      render(<OfferPanel applicationId={1000} isRecruiter={true} onClose={() => {}} />);

      expect(await screen.findByRole('button', { name: /send offer/i })).toBeInTheDocument();
    });

    it('sends a new offer with the entered details', async () => {
      offerAPI.get.mockRejectedValueOnce({ response: { data: {} } })
        .mockResolvedValueOnce({ data: { title: 'Backend Engineer', salaryOffered: '$120,000', status: 'SENT' } });
      offerAPI.send.mockResolvedValue({ data: {} });
      const user = userEvent.setup();

      render(<OfferPanel applicationId={1000} isRecruiter={true} onClose={() => {}} />);
      await screen.findByRole('button', { name: /send offer/i });

      await user.type(screen.getByLabelText(/position title/i), 'Backend Engineer');
      await user.type(screen.getByLabelText(/salary/i), '$120,000');
      await user.click(screen.getByRole('button', { name: /send offer/i }));

      expect(offerAPI.send).toHaveBeenCalledWith(1000, 'Backend Engineer', '$120,000', null, null);
    });

    it('shows offer status and a withdraw button while pending', async () => {
      offerAPI.get.mockResolvedValue({ data: { title: 'Backend Engineer', salaryOffered: '$120,000', status: 'SENT' } });

      render(<OfferPanel applicationId={1000} isRecruiter={true} onClose={() => {}} />);

      expect(await screen.findByText('Backend Engineer')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /withdraw offer/i })).toBeInTheDocument();
    });

    it('shows the signed confirmation once accepted', async () => {
      offerAPI.get.mockResolvedValue({
        data: { title: 'Backend Engineer', salaryOffered: '$120,000', status: 'ACCEPTED', candidateSignatureName: 'Jane Doe' }
      });

      render(<OfferPanel applicationId={1000} isRecruiter={true} onClose={() => {}} />);

      expect(await screen.findByText(/signed by jane doe/i)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /withdraw offer/i })).not.toBeInTheDocument();
    });

    it('withdraws a pending offer', async () => {
      offerAPI.get.mockResolvedValue({ data: { title: 'Backend Engineer', salaryOffered: '$120,000', status: 'SENT' } });
      offerAPI.withdraw.mockResolvedValue({ data: {} });
      const user = userEvent.setup();

      render(<OfferPanel applicationId={1000} isRecruiter={true} onClose={() => {}} />);
      await user.click(await screen.findByRole('button', { name: /withdraw offer/i }));

      expect(offerAPI.withdraw).toHaveBeenCalledWith(1000);
    });
  });

  describe('candidate view', () => {
    it('shows a message when there is no offer yet', async () => {
      offerAPI.get.mockRejectedValue({ response: { data: {} } });

      render(<OfferPanel applicationId={1000} isRecruiter={false} onClose={() => {}} />);

      expect(await screen.findByText(/no offer has been sent yet/i)).toBeInTheDocument();
    });

    it('accepts an offer by typing a signature name', async () => {
      offerAPI.get.mockResolvedValue({ data: { title: 'Backend Engineer', salaryOffered: '$120,000', status: 'SENT' } });
      offerAPI.accept.mockResolvedValue({ data: {} });
      const user = userEvent.setup();

      render(<OfferPanel applicationId={1000} isRecruiter={false} onClose={() => {}} />);
      await user.type(await screen.findByPlaceholderText(/full legal name/i), 'Jane Doe');
      await user.click(screen.getByRole('button', { name: /accept & sign/i }));

      expect(offerAPI.accept).toHaveBeenCalledWith(1000, 'Jane Doe');
    });

    it('requires a signature name before accepting', async () => {
      offerAPI.get.mockResolvedValue({ data: { title: 'Backend Engineer', salaryOffered: '$120,000', status: 'SENT' } });
      const user = userEvent.setup();

      render(<OfferPanel applicationId={1000} isRecruiter={false} onClose={() => {}} />);
      await user.click(await screen.findByRole('button', { name: /accept & sign/i }));

      expect(await screen.findByText('Type your full name to sign')).toBeInTheDocument();
      expect(offerAPI.accept).not.toHaveBeenCalled();
    });

    it('declines an offer with a reason', async () => {
      offerAPI.get.mockResolvedValue({ data: { title: 'Backend Engineer', salaryOffered: '$120,000', status: 'SENT' } });
      offerAPI.decline.mockResolvedValue({ data: {} });
      const user = userEvent.setup();

      render(<OfferPanel applicationId={1000} isRecruiter={false} onClose={() => {}} />);
      await user.click(await screen.findByRole('button', { name: /^decline$/i }));
      await user.type(screen.getByRole('textbox'), 'Accepted another offer');
      await user.click(screen.getByRole('button', { name: /confirm decline/i }));

      expect(offerAPI.decline).toHaveBeenCalledWith(1000, 'Accepted another offer');
    });

    it('shows a withdrawn message', async () => {
      offerAPI.get.mockResolvedValue({ data: { title: 'Backend Engineer', salaryOffered: '$120,000', status: 'WITHDRAWN' } });

      render(<OfferPanel applicationId={1000} isRecruiter={false} onClose={() => {}} />);

      expect(await screen.findByText(/withdrawn by the recruiter/i)).toBeInTheDocument();
    });

    it('opens and displays the official offer letter modal', async () => {
      offerAPI.get.mockResolvedValue({
        data: {
          title: 'Staff Architect',
          salaryOffered: '$180,000',
          startDate: '2026-10-01',
          additionalTerms: '10,000 ISO Stock Options',
          status: 'SENT',
        },
      });
      const user = userEvent.setup();

      render(<OfferPanel applicationId={1000} isRecruiter={false} onClose={() => {}} />);

      const viewBtn = await screen.findByRole('button', { name: /view official letter/i });
      await user.click(viewBtn);

      expect(await screen.findByRole('heading', { name: /official offer of employment/i })).toBeInTheDocument();
      expect(screen.getAllByText(/10,000 ISO Stock Options/).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByRole('button', { name: /print \/ save pdf/i })).toBeInTheDocument();
    });
  });
});
