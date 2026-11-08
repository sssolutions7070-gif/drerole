import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Navbar from './Navbar';
import { ThemeProvider } from '../contexts/ThemeContext';

/** Only the dark-mode toggle button this Navbar now renders - the rest of this component has no
 *  prior dedicated test file to extend. */

const renderNavbar = () => render(
  <MemoryRouter>
    <ThemeProvider>
      <Navbar onAuthChange={() => {}} />
    </ThemeProvider>
  </MemoryRouter>
);

describe('Navbar theme toggle', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    window.matchMedia = vi.fn().mockReturnValue({ matches: false });
  });

  it('shows a toggle button for a logged-out visitor and switches to dark mode on click', async () => {
    const user = userEvent.setup();
    renderNavbar();

    const toggle = screen.getByRole('button', { name: /switch to dark mode/i });
    await user.click(toggle);

    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(screen.getByRole('button', { name: /switch to light mode/i })).toBeInTheDocument();
  });

  it('shows the toggle button for a logged-in user too', () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha Rao', role: 'ROLE_CANDIDATE' }));
    localStorage.setItem('token', 'fake-token');
    renderNavbar();

    expect(screen.getByRole('button', { name: /switch to dark mode/i })).toBeInTheDocument();
  });
});
