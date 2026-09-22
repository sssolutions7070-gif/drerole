import { describe, it, expect, beforeEach, vi } from 'vitest';
import { handleResponseError, PUBLIC_PATHS } from './api';

// Regression test for the infinite-reload-loop bug: a 401 while already on a public
// (logged-out) page used to force `window.location.href` to that same URL, which is a
// full-page reload even when the value doesn't change - remounting everything, refiring
// the same failing request, reloading again, forever. See VoiceAssistant/App.jsx history.
describe('handleResponseError', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    localStorage.clear();
    delete window.location;
    window.location = { ...originalLocation, href: '', pathname: '/jobs' };
  });

  const error401 = () => ({ response: { status: 401 } });

  it('clears the session and redirects on a 401 from a protected page', async () => {
    window.location.pathname = '/jobs';
    localStorage.setItem('token', 'abc');
    localStorage.setItem('user', '{"id":1}');

    await expect(handleResponseError(error401())).rejects.toBeDefined();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(window.location.href).toBe('/login');
  });

  it.each(PUBLIC_PATHS)('does not touch localStorage or navigate on a 401 while already on %s', async (path) => {
    window.location.pathname = path;
    localStorage.setItem('token', 'abc');

    await expect(handleResponseError(error401())).rejects.toBeDefined();

    expect(localStorage.getItem('token')).toBe('abc');
    expect(window.location.href).toBe('');
  });

  it('leaves non-401 errors alone', async () => {
    window.location.pathname = '/jobs';
    localStorage.setItem('token', 'abc');

    await expect(handleResponseError({ response: { status: 500 } })).rejects.toBeDefined();

    expect(localStorage.getItem('token')).toBe('abc');
    expect(window.location.href).toBe('');
  });

  it('handles a network error with no response object at all', async () => {
    window.location.pathname = '/jobs';

    await expect(handleResponseError({ message: 'Network Error' })).rejects.toBeDefined();

    expect(window.location.href).toBe('');
  });
});
