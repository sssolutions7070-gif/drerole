import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import SubscriptionPanel from './SubscriptionPanel';
import api from '../services/api';

vi.mock('../services/api');

describe('SubscriptionPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.confirm = vi.fn(() => true);
    delete window.location;
    window.location = { href: '' };
  });

  const renderPanel = (initialEntry = '/recruiter/dashboard') =>
    render(
      <MemoryRouter initialEntries={[initialEntry]}>
        <SubscriptionPanel recruiterId={42} />
      </MemoryRouter>
    );

  it('shows the free plan usage and an upgrade button', async () => {
    api.get.mockResolvedValue({
      data: { tier: 'FREE', status: null, activeJobCount: 2, freeTierJobLimit: 3, configured: true }
    });

    renderPanel();

    expect(await screen.findByText('Free plan')).toBeInTheDocument();
    expect(screen.getByText(/2 of 3 active job postings used/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /upgrade to pro/i })).toBeEnabled();
  });

  it('disables upgrading when subscriptions are not configured', async () => {
    api.get.mockResolvedValue({
      data: { tier: 'FREE', status: null, activeJobCount: 0, freeTierJobLimit: 3, configured: false }
    });

    renderPanel();

    expect(await screen.findByRole('button', { name: /upgrade to pro/i })).toBeDisabled();
    expect(screen.getByText(/not configured on this server yet/i)).toBeInTheDocument();
  });

  it('redirects to the checkout url when upgrading', async () => {
    api.get.mockResolvedValue({
      data: { tier: 'FREE', status: null, activeJobCount: 0, freeTierJobLimit: 3, configured: true }
    });
    api.post.mockResolvedValue({ data: { url: 'https://checkout.stripe.com/pay/cs_1' } });
    const user = userEvent.setup();

    renderPanel();
    await user.click(await screen.findByRole('button', { name: /upgrade to pro/i }));

    await waitFor(() => expect(window.location.href).toBe('https://checkout.stripe.com/pay/cs_1'));
    expect(api.post).toHaveBeenCalledWith('/recruiters/42/subscription/checkout');
  });

  it('shows the pro plan and a cancel button', async () => {
    api.get.mockResolvedValue({
      data: { tier: 'PRO', status: 'ACTIVE', activeJobCount: 10, freeTierJobLimit: 3, configured: true }
    });

    renderPanel();

    expect(await screen.findByText('Pro plan')).toBeInTheDocument();
    expect(screen.getByText('Status: ACTIVE')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel subscription/i })).toBeInTheDocument();
  });

  it('cancels the subscription after confirmation and refreshes the status', async () => {
    api.get
      .mockResolvedValueOnce({ data: { tier: 'PRO', status: 'ACTIVE', activeJobCount: 5, freeTierJobLimit: 3, configured: true } })
      .mockResolvedValueOnce({ data: { tier: 'FREE', status: 'CANCELED', activeJobCount: 5, freeTierJobLimit: 3, configured: true } });
    api.post.mockResolvedValue({ data: { message: 'Subscription cancelled' } });
    const user = userEvent.setup();

    renderPanel();
    await user.click(await screen.findByRole('button', { name: /cancel subscription/i }));

    expect(await screen.findByText('Free plan')).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/recruiters/42/subscription/cancel');
  });

  it('does not cancel when the confirmation dialog is declined', async () => {
    window.confirm = vi.fn(() => false);
    api.get.mockResolvedValue({
      data: { tier: 'PRO', status: 'ACTIVE', activeJobCount: 5, freeTierJobLimit: 3, configured: true }
    });
    const user = userEvent.setup();

    renderPanel();
    await user.click(await screen.findByRole('button', { name: /cancel subscription/i }));

    expect(api.post).not.toHaveBeenCalled();
  });

  it('shows a success banner after returning from checkout', async () => {
    api.get.mockResolvedValue({
      data: { tier: 'FREE', status: null, activeJobCount: 0, freeTierJobLimit: 3, configured: true }
    });

    renderPanel('/recruiter/dashboard?subscription=success');

    expect(await screen.findByText(/checkout complete/i)).toBeInTheDocument();
  });

  it('shows a cancelled banner when checkout was abandoned', async () => {
    api.get.mockResolvedValue({
      data: { tier: 'FREE', status: null, activeJobCount: 0, freeTierJobLimit: 3, configured: true }
    });

    renderPanel('/recruiter/dashboard?subscription=cancelled');

    expect(await screen.findByText(/checkout was cancelled/i)).toBeInTheDocument();
  });
});
