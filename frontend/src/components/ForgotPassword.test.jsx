import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ForgotPassword from './ForgotPassword';
import authService from '../services/auth';

vi.mock('../services/auth');

describe('ForgotPassword', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  const renderPage = () =>
    render(
      <MemoryRouter>
        <ForgotPassword />
      </MemoryRouter>
    );

  it('submits the email and shows the generic success message', async () => {
    authService.forgotPassword.mockResolvedValue({ message: 'ok' });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/email address/i), 'a@b.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText(/we've sent a reset link/i)).toBeInTheDocument();
    expect(authService.forgotPassword).toHaveBeenCalledWith('a@b.com');
  });

  it('shows an error message when the request fails', async () => {
    authService.forgotPassword.mockRejectedValue({ error: 'Server unavailable' });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/email address/i), 'a@b.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
    expect(screen.queryByText(/we've sent a reset link/i)).not.toBeInTheDocument();
  });

  it('never calls the service with an empty email (the field is required)', async () => {
    authService.forgotPassword.mockResolvedValue({ message: 'ok' });
    renderPage();

    expect(screen.getByLabelText(/email address/i)).toBeRequired();
  });
});
