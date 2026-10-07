import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import IntegrationsPanel from './IntegrationsPanel';
import { apiKeyAPI, webhookAPI } from '../services/api';

vi.mock('../services/api');

describe('IntegrationsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue() },
      configurable: true,
    });
    apiKeyAPI.list.mockResolvedValue({ data: [] });
    webhookAPI.list.mockResolvedValue({ data: [] });
  });

  it('lists existing keys and webhooks', async () => {
    apiKeyAPI.list.mockResolvedValue({ data: [{ id: 1, label: 'My ATS', keyPrefix: 'dr_live_ab...', revoked: false }] });
    webhookAPI.list.mockResolvedValue({ data: [{ id: 2, url: 'https://x.com/hook', eventType: 'APPLICATION_SUBMITTED', active: true }] });

    render(<IntegrationsPanel recruiterId={100} />);

    expect(await screen.findByText('My ATS')).toBeInTheDocument();
    expect(screen.getByText('https://x.com/hook')).toBeInTheDocument();
  });

  it('generates a new API key and shows it once', async () => {
    apiKeyAPI.generate.mockResolvedValue({ data: { key: 'dr_live_supersecret', keyPrefix: 'dr_live_sup...', label: 'CI script' } });
    const user = userEvent.setup();

    render(<IntegrationsPanel recruiterId={100} />);
    await user.type(screen.getByPlaceholderText(/our ats/i), 'CI script');
    await user.click(screen.getByRole('button', { name: /generate key/i }));

    expect(apiKeyAPI.generate).toHaveBeenCalledWith(100, 'CI script');
    expect(await screen.findByText('dr_live_supersecret')).toBeInTheDocument();
  });

  it('revokes an API key', async () => {
    apiKeyAPI.list
      .mockResolvedValueOnce({ data: [{ id: 1, label: 'My ATS', keyPrefix: 'dr_live_ab...', revoked: false }] })
      .mockResolvedValueOnce({ data: [{ id: 1, label: 'My ATS', keyPrefix: 'dr_live_ab...', revoked: true }] });
    apiKeyAPI.revoke.mockResolvedValue({});
    const user = userEvent.setup();

    render(<IntegrationsPanel recruiterId={100} />);
    await screen.findByText('My ATS');
    await user.click(screen.getByRole('button', { name: /revoke my ats/i }));

    expect(apiKeyAPI.revoke).toHaveBeenCalledWith(100, 1);
  });

  it('registers a new webhook and shows the signing secret once', async () => {
    webhookAPI.register.mockResolvedValue({ data: { url: 'https://x.com/hook', eventType: 'OFFER_ACCEPTED', secret: 'shh-secret' } });
    const user = userEvent.setup();

    render(<IntegrationsPanel recruiterId={100} />);
    await user.type(screen.getByPlaceholderText(/your-app.com/i), 'https://x.com/hook');
    await user.click(screen.getByRole('button', { name: /add webhook/i }));

    expect(webhookAPI.register).toHaveBeenCalledWith(100, 'https://x.com/hook', 'APPLICATION_SUBMITTED');
    expect(await screen.findByText('shh-secret')).toBeInTheDocument();
  });

  it('removes a webhook', async () => {
    webhookAPI.list
      .mockResolvedValueOnce({ data: [{ id: 2, url: 'https://x.com/hook', eventType: 'APPLICATION_SUBMITTED', active: true }] })
      .mockResolvedValueOnce({ data: [] });
    webhookAPI.remove.mockResolvedValue({});
    const user = userEvent.setup();

    render(<IntegrationsPanel recruiterId={100} />);
    await screen.findByText('https://x.com/hook');
    await user.click(screen.getByRole('button', { name: /remove webhook/i }));

    expect(webhookAPI.remove).toHaveBeenCalledWith(100, 2);
  });

  it('shows an error when an action fails', async () => {
    apiKeyAPI.generate.mockRejectedValue({ response: { data: { error: 'Only the account owner can manage API keys' } } });
    const user = userEvent.setup();

    render(<IntegrationsPanel recruiterId={100} />);
    await user.click(screen.getByRole('button', { name: /generate key/i }));

    expect(await screen.findByText(/only the account owner/i)).toBeInTheDocument();
  });

  it('populates preset URLs when clicking Slack and Discord preset buttons', async () => {
    const user = userEvent.setup();
    render(<IntegrationsPanel recruiterId={100} />);

    await user.click(screen.getByRole('button', { name: /slack preset/i }));
    expect(screen.getByDisplayValue(/hooks\.slack\.com/i)).toBeInTheDocument();
    expect(screen.getByText('Slack Block Kit')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /discord preset/i }));
    expect(screen.getByDisplayValue(/discord\.com\/api\/webhooks/i)).toBeInTheDocument();
    expect(screen.getByText('Discord Embed')).toBeInTheDocument();
  });

  it('dispatches a test ping when clicking Send Test Ping', async () => {
    webhookAPI.list.mockResolvedValue({
      data: [{ id: 55, url: 'https://hooks.slack.com/services/test', eventType: 'INTERVIEW_SCHEDULED', active: true }],
    });
    webhookAPI.testPing = vi.fn().mockResolvedValue({
      data: { status: 'SUCCESS', message: 'Test ping delivered!' },
    });

    const user = userEvent.setup();
    render(<IntegrationsPanel recruiterId={100} />);

    await screen.findByText('https://hooks.slack.com/services/test');
    await user.click(screen.getByRole('button', { name: /send test ping/i }));

    expect(webhookAPI.testPing).toHaveBeenCalledWith(100, 55);
    expect(await screen.findByText(/test ping delivered!/i)).toBeInTheDocument();
  });
});
