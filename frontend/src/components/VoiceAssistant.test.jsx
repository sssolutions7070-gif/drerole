import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import VoiceAssistant from './VoiceAssistant';
import api from '../services/api';
import authService from '../services/auth';

vi.mock('../services/api');
vi.mock('../services/auth');

class MockSpeechRecognition {
  start() {}
  stop() {}
}

const setUser = (overrides = {}) => {
  localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Test User', role: 'ROLE_USER', ...overrides }));
};

describe('VoiceAssistant', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    vi.stubGlobal('SpeechRecognition', MockSpeechRecognition);
    vi.stubGlobal('speechSynthesis', { getVoices: () => [], cancel: vi.fn(), speak: vi.fn(), onvoiceschanged: null });
    Object.defineProperty(window.navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [] }) },
    });
    api.get.mockResolvedValue({ data: [] });
  });

  const openAssistant = async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <VoiceAssistant />
      </MemoryRouter>
    );
    const micButton = screen.getByTitle('Start voice assistant');
    await act(async () => {
      await user.click(micButton);
    });
    return user;
  };

  // Types into the "Or type a command..." fallback and submits it - the same pipeline speech
  // recognition uses, so every assertion below also covers the spoken-command path.
  const typeCommand = async (user, text) => {
    const input = screen.getByPlaceholderText(/type a command/i);
    await user.type(input, text);
    await user.click(screen.getByRole('button', { name: /^Send$/i }));
  };

  it('opens the assistant panel after the microphone is granted', async () => {
    await openAssistant();

    expect(screen.getByText(/click for all voice commands/i)).toBeInTheDocument();
  });

  it('the help popup advertises the new apply-by-voice and rate-by-voice commands', async () => {
    const user = await openAssistant();

    await user.click(screen.getByText(/click for all voice commands/i));

    expect(screen.getByText(/apply to job 1/i)).toBeInTheDocument();
    expect(screen.getByText(/rate job 1 five stars/i)).toBeInTheDocument();
  });

  describe('typed command fallback', () => {
    it('opens the panel and shows the not-supported message when speech recognition is unavailable', async () => {
      // The outer beforeEach stubs SpeechRecognition for every other test; `in` checks for the
      // property's existence regardless of its value, so stubbing it to undefined wouldn't make
      // the component think it's unsupported - the property has to be removed outright.
      delete window.SpeechRecognition;
      delete window.webkitSpeechRecognition;
      const user = userEvent.setup();
      render(
        <MemoryRouter>
          <VoiceAssistant />
        </MemoryRouter>
      );

      await user.click(screen.getByTitle('Start voice assistant'));

      // Previously this message was added to a panel that never opened, so the user never saw it.
      expect(await screen.findByText(/not supported/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/type a command/i)).toBeInTheDocument();
    });

    it('runs a typed command through the same pipeline as a spoken one', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'go to home');

      expect(await screen.findByText('Going to home page.')).toBeInTheDocument();
    });
  });

  describe('role-aware behavior', () => {
    it('shows recruiter tools and hides job-search commands in the help popup for a recruiter', async () => {
      setUser({ role: 'ROLE_RECRUITER' });
      const user = await openAssistant();

      await user.click(screen.getByText(/click for all voice commands/i));

      expect(screen.getByText(/discover candidates.*Browse open-to-work/i)).toBeInTheDocument();
      expect(screen.getByText(/post a job.*Open your jobs tab/i)).toBeInTheDocument();
      expect(screen.queryByText(/apply to job 1/i)).not.toBeInTheDocument();
    });

    it('shows admin tools in the help popup for an admin', async () => {
      setUser({ role: 'ROLE_ADMIN' });
      const user = await openAssistant();

      await user.click(screen.getByText(/click for all voice commands/i));

      // Matched on the help-popup's exact bullet formatting - the admin welcome message also
      // happens to mention "pending jobs" and "moderation queue" in a different sentence.
      expect(screen.getByText(/"pending jobs" - Open the moderation queue/i)).toBeInTheDocument();
      expect(screen.queryByText(/apply to job 1/i)).not.toBeInTheDocument();
    });

    it('every role sees the Account section with "my profile" and "log out"', async () => {
      const user = await openAssistant();

      await user.click(screen.getByText(/click for all voice commands/i));

      expect(screen.getByText(/my profile.*Open your profile page/i)).toBeInTheDocument();
      expect(screen.getByText(/log out.*Sign out/i)).toBeInTheDocument();
    });

    it('"my applications" fetches the candidate\'s own applications', async () => {
      setUser({ role: 'ROLE_USER' });
      api.get.mockImplementation((url) => {
        if (url === '/applications/user/1') return Promise.resolve({ data: { applications: [] } });
        return Promise.resolve({ data: [] });
      });
      const user = await openAssistant();

      await typeCommand(user, 'my applications');

      expect(await screen.findByText(/haven't applied to any jobs yet/i)).toBeInTheDocument();
    });

    it('"my applications" sends a recruiter to their dashboard instead of fetching candidate data', async () => {
      setUser({ role: 'ROLE_RECRUITER' });
      const user = await openAssistant();

      await typeCommand(user, 'my applications');

      expect(await screen.findByText(/View Applications.*on a job/i)).toBeInTheDocument();
      expect(api.get).not.toHaveBeenCalledWith('/applications/user/1');
    });
  });

  describe('a failed applications fetch shows a real message, not a raw key', () => {
    it('shows a human-readable error instead of the literal "error_fetching_applications" key', async () => {
      setUser({ role: 'ROLE_USER' });
      api.get.mockImplementation((url) => {
        if (url === '/applications/user/1') return Promise.reject(new Error('network down'));
        return Promise.resolve({ data: [] });
      });
      const user = await openAssistant();

      await typeCommand(user, 'my applications');

      expect(await screen.findByText(/trouble checking your applications/i)).toBeInTheDocument();
      expect(screen.queryByText('error_fetching_applications')).not.toBeInTheDocument();
    });
  });

  describe('account commands', () => {
    it('"my profile" navigates to the profile page', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'my profile');

      expect(await screen.findByText('Opening your profile.')).toBeInTheDocument();
    });

    it('"log out" asks for confirmation instead of signing out immediately', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'log out');

      expect(await screen.findByText(/are you sure you want to log out/i)).toBeInTheDocument();
      expect(authService.logout).not.toHaveBeenCalled();
    });

    it('confirming with "yes" after "log out" actually signs the user out', async () => {
      const onAuthChange = vi.fn();
      const user = userEvent.setup();
      render(
        <MemoryRouter>
          <VoiceAssistant onAuthChange={onAuthChange} />
        </MemoryRouter>
      );
      await act(async () => {
        await user.click(screen.getByTitle('Start voice assistant'));
      });

      await typeCommand(user, 'log out');
      await screen.findByText(/are you sure you want to log out/i);
      await typeCommand(user, 'yes');

      expect(await screen.findByText(/logging you out/i)).toBeInTheDocument();
      expect(authService.logout).toHaveBeenCalled();
      await waitFor(() => expect(onAuthChange).toHaveBeenCalledWith(null));
    });

    it('declining with "no" after "log out" stays signed in', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'log out');
      await screen.findByText(/are you sure you want to log out/i);
      await typeCommand(user, 'no');

      expect(await screen.findByText(/okay, staying signed in/i)).toBeInTheDocument();
      expect(authService.logout).not.toHaveBeenCalled();
    });

    it('moving on to an unrelated command after "log out" is treated as a new command, not stuck waiting', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'log out');
      await screen.findByText(/are you sure you want to log out/i);
      await typeCommand(user, 'go to home');

      expect(await screen.findByText('Going to home page.')).toBeInTheDocument();
      expect(authService.logout).not.toHaveBeenCalled();
    });

    it('"clear chat" resets the conversation to a single fresh message', async () => {
      const user = await openAssistant();
      await typeCommand(user, 'go to home');
      await screen.findByText('Going to home page.');

      await typeCommand(user, 'clear chat');

      expect(await screen.findByText(/cleared\. what can i help you with/i)).toBeInTheDocument();
      expect(screen.queryByText('Going to home page.')).not.toBeInTheDocument();
    });
  });

  describe('job-count queries', () => {
    it('"how many jobs are there" announces the total from the already-loaded job list', async () => {
      api.get.mockImplementation((url) => {
        if (url === '/jobs') return Promise.resolve({ data: [{ id: 1 }, { id: 2 }, { id: 3 }] });
        return Promise.resolve({ data: [] });
      });
      const user = await openAssistant();

      await typeCommand(user, 'how many jobs are there');

      expect(await screen.findByText(/there are 3 jobs posted right now/i)).toBeInTheDocument();
    });

    it('"how many pending jobs" is admin-only and reports the queue size without navigating', async () => {
      setUser({ role: 'ROLE_ADMIN' });
      api.get.mockImplementation((url) => {
        if (url === '/admin/jobs/pending') return Promise.resolve({ data: [{ id: 1 }, { id: 2 }] });
        return Promise.resolve({ data: [] });
      });
      const user = await openAssistant();

      await typeCommand(user, 'how many pending jobs are there');

      expect(await screen.findByText(/there are 2 jobs waiting in the moderation queue/i)).toBeInTheDocument();
    });

    it('a candidate asking "how many pending jobs" gets the unknown-command fallback, not the admin queue size', async () => {
      setUser({ role: 'ROLE_USER' });
      const user = await openAssistant();

      await typeCommand(user, 'how many pending jobs are there');

      expect(await screen.findByText(/didn't understand/i)).toBeInTheDocument();
    });
  });

  describe('admin moderation by voice', () => {
    const pendingJobs = [
      { id: 11, title: 'Backend Engineer', company: 'Acme' },
      { id: 12, title: 'Frontend Engineer', company: 'Acme' },
    ];

    beforeEach(() => {
      setUser({ role: 'ROLE_ADMIN' });
      api.get.mockImplementation((url) => {
        if (url === '/admin/jobs/pending') return Promise.resolve({ data: pendingJobs });
        return Promise.resolve({ data: [] });
      });
      api.put.mockResolvedValue({ data: { message: 'ok' } });
    });

    it('"approve job 1" approves the first pending job and confirms by name', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'approve job 1');

      await waitFor(() => expect(api.put).toHaveBeenCalledWith('/admin/jobs/11/approve'));
      expect(await screen.findByText(/"Backend Engineer" has been approved/i)).toBeInTheDocument();
    });

    it('"reject job 2 because it looks like spam" rejects with the spoken reason', async () => {
      const user = await openAssistant();

      await typeCommand(user, "reject job 2 because it looks like spam");

      await waitFor(() => expect(api.put).toHaveBeenCalledWith('/admin/jobs/12/reject', { reason: 'it looks like spam' }));
      expect(await screen.findByText(/"Frontend Engineer" has been rejected/i)).toBeInTheDocument();
    });

    it('"reject job 1" with no reason given falls back to a default reason', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'reject job 1');

      await waitFor(() => expect(api.put).toHaveBeenCalledWith('/admin/jobs/11/reject', { reason: 'Rejected via voice assistant.' }));
    });

    it('"approve job 9" (out of range) reports it could not find the job instead of calling the API', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'approve job 9');

      expect(await screen.findByText(/couldn't tell which pending job/i)).toBeInTheDocument();
      expect(api.put).not.toHaveBeenCalled();
    });

    it('a candidate saying "approve job 1" is not treated as a moderation command', async () => {
      setUser({ role: 'ROLE_USER' });
      const user = await openAssistant();

      await typeCommand(user, 'approve job 1');

      expect(api.put).not.toHaveBeenCalled();
      expect(await screen.findByText(/didn't understand/i)).toBeInTheDocument();
    });
  });

  describe('"stop" and "repeat" commands', () => {
    it('"stop" cancels speech synthesis without speaking the confirmation', async () => {
      const user = await openAssistant();
      window.speechSynthesis.speak.mockClear();
      window.speechSynthesis.cancel.mockClear();

      await typeCommand(user, 'stop talking');

      expect(window.speechSynthesis.cancel).toHaveBeenCalled();
      expect(await screen.findByText('Stopped.')).toBeInTheDocument();
      expect(window.speechSynthesis.speak).not.toHaveBeenCalled();
    });

    it('"repeat that" re-speaks the last assistant message', async () => {
      const user = await openAssistant();
      await typeCommand(user, 'go to home');
      await screen.findByText('Going to home page.');
      window.speechSynthesis.speak.mockClear();

      await typeCommand(user, 'repeat that');

      await waitFor(() => expect(window.speechSynthesis.speak).toHaveBeenCalledTimes(1));
      const spoken = window.speechSynthesis.speak.mock.calls[0][0];
      expect(spoken.text).toBe('Going to home page.');
    });
  });

  describe('Alt+A keyboard shortcut', () => {
    it('opens the assistant and focuses the text input', async () => {
      render(
        <MemoryRouter>
          <VoiceAssistant />
        </MemoryRouter>
      );

      fireEvent.keyDown(window, { key: 'a', altKey: true });

      const input = await screen.findByPlaceholderText(/type a command/i);
      expect(input).toHaveFocus();
    });

    it('does not open on AltGr-style combinations (ctrlKey+altKey together)', async () => {
      render(
        <MemoryRouter>
          <VoiceAssistant />
        </MemoryRouter>
      );

      fireEvent.keyDown(window, { key: 'a', altKey: true, ctrlKey: true });

      expect(screen.queryByPlaceholderText(/type a command/i)).not.toBeInTheDocument();
    });
  });

  describe('fuzzy understanding of rephrased commands', () => {
    // None of these contain any COMMAND_MAPPINGS phrase word-for-word - they only share the
    // same *meaningful* words, in a different order and with filler words mixed in, which is
    // exactly what the fuzzy fallback in detectCommand is for.
    it('"can you add a new job listing for me" is understood as "post a job"', async () => {
      setUser({ role: 'ROLE_RECRUITER' });
      const user = await openAssistant();

      await typeCommand(user, 'can you add a new job listing for me');

      expect(await screen.findByText(/opening your jobs tab/i)).toBeInTheDocument();
    });

    it('"please open the queue for moderation" (reversed word order) is understood as "pending jobs"', async () => {
      setUser({ role: 'ROLE_ADMIN' });
      const user = await openAssistant();

      await typeCommand(user, 'please open the queue for moderation');

      expect(await screen.findByText(/opening the pending jobs moderation queue/i)).toBeInTheDocument();
    });

    it('"please get me signed out of here" (inflected verb) is understood as "log out"', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'please get me signed out of here');

      expect(await screen.findByText(/are you sure you want to log out/i)).toBeInTheDocument();
    });

    it('a single generic word is not enough to fuzzy-match anything on its own', async () => {
      const user = await openAssistant();

      await typeCommand(user, 'jobs');

      // "jobs" alone only ever reaches the 1-meaningful-word floor, which the fallback requires
      // at least 2 of - so this should NOT be silently reinterpreted as some arbitrary command.
      expect(await screen.findByText(/didn't understand/i)).toBeInTheDocument();
    });

    it('an unrelated sentence still falls through to the unknown-command message', async () => {
      const user = await openAssistant();

      await typeCommand(user, "what's the weather like today");

      expect(await screen.findByText(/didn't understand/i)).toBeInTheDocument();
    });
  });
});
