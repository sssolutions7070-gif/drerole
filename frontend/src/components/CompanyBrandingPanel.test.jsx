import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CompanyBrandingPanel from './CompanyBrandingPanel';
import api from '../services/api';

vi.mock('../services/api');

describe('CompanyBrandingPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('pre-fills fields from the existing recruiter profile', () => {
    render(
      <CompanyBrandingPanel
        userId={10}
        recruiterProfile={{
          missionStatement: 'We build the future.',
          benefits: JSON.stringify(['Remote-first']),
          companyLinkedin: 'https://linkedin.com/company/acme',
          companyTwitter: 'https://twitter.com/acme',
          foundedYear: 2015,
          headquarters: 'Austin, TX',
        }}
        onUpdated={vi.fn()}
      />
    );

    expect(screen.getByDisplayValue('We build the future.')).toBeInTheDocument();
    expect(screen.getByText('Remote-first')).toBeInTheDocument();
    expect(screen.getByDisplayValue('https://linkedin.com/company/acme')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2015')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Austin, TX')).toBeInTheDocument();
  });

  it('adds and removes a benefit', async () => {
    const user = userEvent.setup();
    render(<CompanyBrandingPanel userId={10} recruiterProfile={{}} onUpdated={vi.fn()} />);

    await user.type(screen.getByPlaceholderText(/remote-first/i), 'Unlimited PTO');
    await user.click(screen.getByLabelText('Add benefit'));

    expect(await screen.findByText('Unlimited PTO')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Remove Unlimited PTO'));
    expect(screen.queryByText('Unlimited PTO')).not.toBeInTheDocument();
  });

  it('saves the branding fields', async () => {
    api.put.mockResolvedValue({ data: {} });
    const onUpdated = vi.fn();
    const user = userEvent.setup();

    render(<CompanyBrandingPanel userId={10} recruiterProfile={{}} onUpdated={onUpdated} />);

    await user.type(screen.getByPlaceholderText(/what is your company working toward/i), 'Our mission');
    await user.click(screen.getByRole('button', { name: /save branding/i }));

    expect(api.put).toHaveBeenCalledWith('/recruiters/user/10/profile', expect.objectContaining({
      missionStatement: 'Our mission',
      benefits: '[]',
    }));
    expect(await screen.findByText('Company profile updated')).toBeInTheDocument();
    expect(onUpdated).toHaveBeenCalled();
  });

  it('shows an error when saving fails', async () => {
    api.put.mockRejectedValue({ response: { data: { error: 'boom' } } });
    const user = userEvent.setup();

    render(<CompanyBrandingPanel userId={10} recruiterProfile={{}} onUpdated={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: /save branding/i }));

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });

  it('uploads a logo image', async () => {
    api.post.mockResolvedValue({ data: { companyLogo: '/uploads/company-logos/10_abc.png' } });
    const onUpdated = vi.fn();
    const user = userEvent.setup();

    render(<CompanyBrandingPanel userId={10} recruiterProfile={{}} onUpdated={onUpdated} />);

    const file = new File(['x'], 'logo.png', { type: 'image/png' });
    const inputs = document.querySelectorAll('input[type="file"]');
    await user.upload(inputs[0], file);

    expect(api.post).toHaveBeenCalledWith('/recruiters/user/10/logo', expect.any(FormData), expect.any(Object));
    expect(await screen.findByText('Company logo updated')).toBeInTheDocument();
    expect(onUpdated).toHaveBeenCalled();
  });

  it('rejects a non-image file for the banner', async () => {
    render(<CompanyBrandingPanel userId={10} recruiterProfile={{}} onUpdated={vi.fn()} />);

    const file = new File(['x'], 'doc.pdf', { type: 'application/pdf' });
    const inputs = document.querySelectorAll('input[type="file"]');
    Object.defineProperty(inputs[1], 'files', { value: [file] });
    fireEvent.change(inputs[1]);

    expect(await screen.findByText(/please upload an image file/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });
});
