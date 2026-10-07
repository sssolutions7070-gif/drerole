import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import InterviewRoundRoom from './InterviewRoundRoom';
import { interviewRoundsAPI } from '../services/api';

vi.mock('../services/api', () => ({
  interviewRoundsAPI: {
    getJoinInfo: vi.fn(),
    updateStatus: vi.fn(),
    getCopilotProbes: vi.fn(),
  },
}));

class MockPeerConnection {
  addTrack() {}
  createOffer() { return Promise.resolve({ type: 'offer', sdp: 'offer-sdp' }); }
  createAnswer() { return Promise.resolve({ type: 'answer', sdp: 'answer-sdp' }); }
  setLocalDescription() { return Promise.resolve(); }
  setRemoteDescription() { return Promise.resolve(); }
  addIceCandidate() { return Promise.resolve(); }
  close() {}
}

class MockWebSocket {
  constructor(url) {
    this.url = url;
    this.readyState = MockWebSocket.OPEN;
    this.sent = [];
    MockWebSocket.instances.push(this);
  }
  send(data) { this.sent.push(data); }
  close() { this.readyState = MockWebSocket.CLOSED; this.onclose?.(); }
}
MockWebSocket.OPEN = 1;
MockWebSocket.CLOSED = 3;
MockWebSocket.instances = [];

describe('InterviewRoundRoom', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    MockWebSocket.instances = [];
    global.RTCPeerConnection = MockPeerConnection;
    global.RTCSessionDescription = function (init) { return init; };
    global.RTCIceCandidate = function (init) { return init; };
    global.WebSocket = MockWebSocket;
    global.navigator.mediaDevices = {
      getUserMedia: vi.fn().mockResolvedValue({
        getTracks: () => [],
        getAudioTracks: () => [{ enabled: true }],
        getVideoTracks: () => [{ enabled: true }],
      }),
    };
    localStorage.setItem('token', 'test-token');
  });

  const renderRoom = () =>
    render(
      <MemoryRouter initialEntries={['/interview-rounds/5000']}>
        <Routes>
          <Route path="/interview-rounds/:roundId" element={<InterviewRoundRoom />} />
        </Routes>
      </MemoryRouter>
    );

  it('shows the lobby with the round title and counterpart name', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Phone screen', roundType: 'PHONE_SCREEN', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });

    renderRoom();

    expect(await screen.findByText('Phone screen')).toBeInTheDocument();
    expect(screen.getByText(/with acme corp/i)).toBeInTheDocument();
  });

  it('shows an error when the round cannot be loaded', async () => {
    interviewRoundsAPI.getJoinInfo.mockRejectedValue({
      response: { data: { error: 'Interview round not found: 5000' } }
    });

    renderRoom();

    expect(await screen.findByText('Interview round not found: 5000')).toBeInTheDocument();
  });

  it('shows the code editor by default for a technical round', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Technical round', roundType: 'TECHNICAL', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));

    expect(await screen.findByText(/shared code editor/i)).toBeInTheDocument();
  });

  it('does not show the code editor by default for a phone screen', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Phone screen', roundType: 'PHONE_SCREEN', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));
    await screen.findByText(/waiting for acme corp to join/i);

    expect(screen.queryByText(/shared code editor/i)).not.toBeInTheDocument();
  });

  it('can toggle the code editor on and off', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Phone screen', roundType: 'PHONE_SCREEN', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));
    await user.click(screen.getByRole('button', { name: /show code editor/i }));

    expect(await screen.findByText(/shared code editor/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /hide code editor/i }));
    expect(screen.queryByText(/shared code editor/i)).not.toBeInTheDocument();
  });

  it('typing in the code editor relays a code-update message over the socket', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Technical round', roundType: 'TECHNICAL', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));
    const editor = await screen.findByPlaceholderText(/shared with the other participant/i);
    await user.type(editor, 'x');

    await waitFor(() => {
      const sent = MockWebSocket.instances[0].sent;
      expect(sent.some((m) => JSON.parse(m).type === 'code-update' && JSON.parse(m).code === 'x')).toBe(true);
    }, { timeout: 1000 });
  });

  it('joining opens the signaling socket to the interview-round endpoint', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Phone screen', roundType: 'PHONE_SCREEN', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));

    await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1));
    expect(MockWebSocket.instances[0].url).toContain('/ws/interview-round');
    expect(MockWebSocket.instances[0].url).toContain('roomId=room-1');
    await waitFor(() => expect(interviewRoundsAPI.updateStatus).toHaveBeenCalledWith('5000', 'IN_PROGRESS'));
  });

  it('ending the call marks the round completed and shows the ended screen', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Phone screen', roundType: 'PHONE_SCREEN', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));
    await user.click(await screen.findByRole('button', { name: /end call/i }));

    expect(await screen.findByText(/call ended/i)).toBeInTheDocument();
    expect(interviewRoundsAPI.updateStatus).toHaveBeenCalledWith('5000', 'COMPLETED');
  });

  it('runs code and displays output in the live terminal console', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Technical round', roundType: 'TECHNICAL', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));

    const editor = await screen.findByRole('textbox', { name: /code editor/i });
    await user.clear(editor);
    await user.type(editor, 'console.log("Live Execution Test!");');

    const runBtn = screen.getByRole('button', { name: /run code/i });
    expect(runBtn).toBeInTheDocument();
    await user.click(runBtn);

    const matches = await screen.findAllByText(/Live Execution Test!/);
    expect(matches.length).toBeGreaterThanOrEqual(2); // One in editor textarea, one in terminal output

    // Verify WebSocket signal was sent for code-run-started and code-run-result
    await waitFor(() => {
      const sent = MockWebSocket.instances[0].sent.map((m) => JSON.parse(m));
      expect(sent.some((m) => m.type === 'code-run-started')).toBe(true);
      expect(sent.some((m) => m.type === 'code-run-result')).toBe(true);
    });
  });

  it('allows switching code language and toggles terminal console', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Technical round', roundType: 'TECHNICAL', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));

    const langSelect = await screen.findByRole('combobox', { name: /code language/i });
    expect(langSelect).toBeInTheDocument();
    await user.selectOptions(langSelect, 'Python');

    // Terminal toggle
    const terminalToggle = screen.getByRole('button', { name: /hide terminal/i });
    await user.click(terminalToggle);
    expect(screen.queryByText(/console output/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /show terminal/i }));
    expect(screen.getByText(/console output/i)).toBeInTheDocument();
  });

  it('does not display AI Copilot controls to candidates', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Technical round', roundType: 'TECHNICAL', role: 'candidate',
        counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));

    expect(screen.queryByRole('button', { name: /ai interviewer copilot/i })).not.toBeInTheDocument();
  });

  it('allows recruiter to access AI Copilot, generates probes, and copies probing question', async () => {
    interviewRoundsAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', title: 'Technical round', roundType: 'TECHNICAL', role: 'recruiter',
        counterpartName: 'Jane Candidate', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    interviewRoundsAPI.updateStatus.mockResolvedValue({ data: {} });
    interviewRoundsAPI.getCopilotProbes.mockResolvedValue({
      data: {
        estimatedComplexity: {
          time: 'O(N²)',
          space: 'O(1)',
          explanation: 'Nested pairwise loop structure detected.'
        },
        strengths: ['Modular function structure'],
        edgeCases: [
          { scenario: 'Empty Array', probe: 'How does your solution handle an empty array or null input?' }
        ],
        optimizationProbes: [
          {
            topic: 'Quadratic Loop Elimination',
            question: 'Can we reduce this to O(N) using a Hash Map?',
            expectedInsight: 'Trading memory for linear time.'
          }
        ],
        architecturalFollowUps: ['How to handle a stream of 100M items?']
      }
    });

    const user = userEvent.setup();
    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));

    const copilotBtn = await screen.findByRole('button', { name: /ai interviewer copilot/i });
    expect(copilotBtn).toBeInTheDocument();
    await user.click(copilotBtn);

    expect(await screen.findByText(/nested pairwise loop structure detected/i)).toBeInTheDocument();
    expect(screen.getByText(/scenario: empty array/i)).toBeInTheDocument();
    expect(screen.getByText(/"How does your solution handle an empty array or null input\?"/i)).toBeInTheDocument();
    expect(screen.getByText(/quadratic loop elimination/i)).toBeInTheDocument();
    expect(screen.getByText(/trading memory for linear time/i)).toBeInTheDocument();

    const copyBtns = screen.getAllByRole('button', { name: /copy question/i });
    expect(copyBtns.length).toBeGreaterThanOrEqual(1);
    await user.click(copyBtns[0]);
    expect(await screen.findByText('Copied')).toBeInTheDocument();
  });
});
