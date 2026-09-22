import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ResetPassword from './ResetPassword';
import authService from '../services/auth';

vi.mock('../services/auth');

describe('ResetPassword', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  const renderAt = (path) =>
    render(
      <MemoryRouter initialEntries={[path]}>
        <ResetPassword />
      </MemoryRouter>
    );

  it('shows an error and no form when the URL has no token', () => {
    renderAt('/reset-password');

    expect(screen.getByText(/missing its token/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/new password/i)).not.toBeInTheDocument();
  });

  it('rejects a password shorter than 8 characters without calling the service', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc');

    await user.type(screen.getByLabelText(/^new password$/i), 'short');
    await user.type(screen.getByLabelText(/confirm new password/i), 'short');
    await user.click(screen.getByRole('button', { name: /update password/i }));

    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument();
    expect(authService.resetPassword).not.toHaveBeenCalled();
  });

  it('rejects mismatched passwords without calling the service', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc');

    await user.type(screen.getByLabelText(/^new password$/i), 'longenoughpw1');
    await user.type(screen.getByLabelText(/confirm new password/i), 'longenoughpw2');
    await user.click(screen.getByRole('button', { name: /update password/i }));

    expect(await screen.findByText(/do not match/i)).toBeInTheDocument();
    expect(authService.resetPassword).not.toHaveBeenCalled();
  });

  it('submits the token and new password, and shows a success message', async () => {
    authService.resetPassword.mockResolvedValue({ message: 'ok' });
    const user = userEvent.setup();
    renderAt('/reset-password?token=my-token');

    await user.type(screen.getByLabelText(/^new password$/i), 'longenoughpw1');
    await user.type(screen.getByLabelText(/confirm new password/i), 'longenoughpw1');
    await user.click(screen.getByRole('button', { name: /update password/i }));

    expect(await screen.findByText(/password updated/i)).toBeInTheDocument();
    expect(authService.resetPassword).toHaveBeenCalledWith('my-token', 'longenoughpw1');
  });

  it('shows the server error when the token is invalid or expired', async () => {
    authService.resetPassword.mockRejectedValue({ error: 'Reset link is invalid or has expired' });
    const user = userEvent.setup();
    renderAt('/reset-password?token=bad-token');

    await user.type(screen.getByLabelText(/^new password$/i), 'longenoughpw1');
    await user.type(screen.getByLabelText(/confirm new password/i), 'longenoughpw1');
    await user.click(screen.getByRole('button', { name: /update password/i }));

    expect(await screen.findByText(/reset link is invalid or has expired/i)).toBeInTheDocument();
  });
});
