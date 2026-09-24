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
});
