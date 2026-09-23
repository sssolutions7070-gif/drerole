import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import VoiceAssistant from './VoiceAssistant';

class MockSpeechRecognition {
  start() {}
  stop() {}
}

describe('VoiceAssistant', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('SpeechRecognition', MockSpeechRecognition);
    vi.stubGlobal('speechSynthesis', { getVoices: () => [], cancel: vi.fn(), speak: vi.fn(), onvoiceschanged: null });
    Object.defineProperty(window.navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [] }) },
    });
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
});
