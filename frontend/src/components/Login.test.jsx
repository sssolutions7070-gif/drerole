import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Login from './Login';
import authService from '../services/auth';

vi.mock('../services/auth');

describe('Login', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  const renderPage = () =>
    render(
      <MemoryRouter>
        <Login onAuthChange={() => {}} />
      </MemoryRouter>
    );

  it('links to the forgot-password page', () => {
    renderPage();

    expect(screen.getByRole('link', { name: /forgot password\?/i })).toHaveAttribute('href', '/forgot-password');
  });

  it('shows the server error message on a failed login without crashing', async () => {
    authService.login.mockRejectedValue({ error: 'Invalid email or password' });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/email address/i), 'a@b.com');
    await user.type(screen.getByLabelText(/^password$/i), 'wrong-password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
  });

  it('asks for a 2FA code instead of logging in immediately when the account requires one', async () => {
    authService.login.mockResolvedValue({ requiresTwoFactor: true, userId: 42 });
    const onAuthChange = vi.fn();
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <Login onAuthChange={onAuthChange} />
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText(/email address/i), 'a@b.com');
    await user.type(screen.getByLabelText(/^password$/i), 'correct-password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText(/two-factor authentication/i)).toBeInTheDocument();
    expect(onAuthChange).not.toHaveBeenCalled();
  });

  it('completes login once the 2FA code is verified', async () => {
    authService.login.mockResolvedValue({ requiresTwoFactor: true, userId: 42 });
    authService.loginWithTwoFactor.mockResolvedValue({ user: { id: 42, role: 'ROLE_USER' } });
    const onAuthChange = vi.fn();
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <Login onAuthChange={onAuthChange} />
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText(/email address/i), 'a@b.com');
    await user.type(screen.getByLabelText(/^password$/i), 'correct-password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await user.type(await screen.findByLabelText(/authentication code/i), '123456');
    await user.click(screen.getByRole('button', { name: /verify/i }));

    expect(authService.loginWithTwoFactor).toHaveBeenCalledWith(42, '123456');
    expect(onAuthChange).toHaveBeenCalledWith({ id: 42, role: 'ROLE_USER' });
  });

  it('links to the backend Google and LinkedIn sign-in routes', () => {
    renderPage();

    expect(screen.getByRole('link', { name: /google/i })).toHaveAttribute(
      'href', 'http://localhost:8081/api/auth/oauth/google');
    expect(screen.getByRole('link', { name: /linkedin/i })).toHaveAttribute(
      'href', 'http://localhost:8081/api/auth/oauth/linkedin');
  });

  it('shows an oauthError from the URL as the page error', () => {
    render(
      <MemoryRouter initialEntries={['/login?oauthError=Google%20sign-in%20is%20not%20configured%20on%20this%20server']}>
        <Login onAuthChange={() => {}} />
      </MemoryRouter>
    );

    expect(screen.getByText('Google sign-in is not configured on this server')).toBeInTheDocument();
  });

  it('shows an error and stays on the 2FA step when the code is wrong', async () => {
    authService.login.mockResolvedValue({ requiresTwoFactor: true, userId: 42 });
    authService.loginWithTwoFactor.mockRejectedValue({ error: 'Incorrect code' });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/email address/i), 'a@b.com');
    await user.type(screen.getByLabelText(/^password$/i), 'correct-password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await user.type(await screen.findByLabelText(/authentication code/i), '000000');
    await user.click(screen.getByRole('button', { name: /verify/i }));

    expect(await screen.findByText('Incorrect code')).toBeInTheDocument();
  });
});
