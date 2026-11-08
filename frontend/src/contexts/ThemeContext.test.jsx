import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, useTheme } from './ThemeContext';

const Probe = () => {
  const { theme, toggleTheme } = useTheme();
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <button onClick={toggleTheme}>Toggle</button>
    </div>
  );
};

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  afterEach(() => {
    document.documentElement.classList.remove('dark');
  });

  it('defaults to light when nothing is stored and the system has no dark preference', () => {
    window.matchMedia = vi.fn().mockReturnValue({ matches: false });
    render(<ThemeProvider><Probe /></ThemeProvider>);

    expect(screen.getByTestId('theme')).toHaveTextContent('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('starts dark when the system prefers dark and nothing is stored yet', () => {
    window.matchMedia = vi.fn().mockReturnValue({ matches: true });
    render(<ThemeProvider><Probe /></ThemeProvider>);

    expect(screen.getByTestId('theme')).toHaveTextContent('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('a previously stored theme wins over the system preference', () => {
    localStorage.setItem('theme', 'light');
    window.matchMedia = vi.fn().mockReturnValue({ matches: true });
    render(<ThemeProvider><Probe /></ThemeProvider>);

    expect(screen.getByTestId('theme')).toHaveTextContent('light');
  });

  it('toggling flips the theme, updates the <html> class, and persists the choice', async () => {
    window.matchMedia = vi.fn().mockReturnValue({ matches: false });
    const user = userEvent.setup();
    render(<ThemeProvider><Probe /></ThemeProvider>);

    await user.click(screen.getByRole('button', { name: 'Toggle' }));

    expect(screen.getByTestId('theme')).toHaveTextContent('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('theme')).toBe('dark');

    await user.click(screen.getByRole('button', { name: 'Toggle' }));

    expect(screen.getByTestId('theme')).toHaveTextContent('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('theme')).toBe('light');
  });

  it('useTheme throws when used outside a ThemeProvider', () => {
    const renderWithoutProvider = () => render(<Probe />);
    expect(renderWithoutProvider).toThrow(/useTheme must be used within a ThemeProvider/);
  });
});
