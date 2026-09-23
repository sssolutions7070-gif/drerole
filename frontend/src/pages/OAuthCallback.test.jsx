import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import OAuthCallback from './OAuthCallback';
import authService from '../services/auth';

vi.mock('../services/auth');

describe('OAuthCallback', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  const renderAt = (path, onAuthChange = () => {}) =>
    render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/oauth-callback" element={<OAuthCallback onAuthChange={onAuthChange} />} />
          <Route path="/dashboard" element={<div>Dashboard page</div>} />
          <Route path="/recruiter/dashboard" element={<div>Recruiter dashboard page</div>} />
          <Route path="/login" element={<div>Login page</div>} />
        </Routes>
      </MemoryRouter>
    );

  it('completes the login and lands on the user dashboard', async () => {
    const onAuthChange = vi.fn();
    authService.completeOAuthLogin.mockResolvedValue({ id: 1, role: 'ROLE_USER' });

    renderAt('/oauth-callback?token=abc123', onAuthChange);

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument();
    expect(authService.completeOAuthLogin).toHaveBeenCalledWith('abc123');
    expect(onAuthChange).toHaveBeenCalledWith({ id: 1, role: 'ROLE_USER' });
  });

  it('sends a recruiter to the recruiter dashboard', async () => {
    authService.completeOAuthLogin.mockResolvedValue({ id: 2, role: 'ROLE_RECRUITER' });

    renderAt('/oauth-callback?token=abc123');

    expect(await screen.findByText('Recruiter dashboard page')).toBeInTheDocument();
  });

  it('sends the visitor back to login when there is no token on the URL', async () => {
    renderAt('/oauth-callback');

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(authService.completeOAuthLogin).not.toHaveBeenCalled();
  });

  it('shows an error instead of navigating when completing the login fails', async () => {
    authService.completeOAuthLogin.mockRejectedValue({ error: 'Sign-in failed. Please try again.' });

    renderAt('/oauth-callback?token=abc123');

    expect(await screen.findByText('Sign-in failed. Please try again.')).toBeInTheDocument();
  });
});
