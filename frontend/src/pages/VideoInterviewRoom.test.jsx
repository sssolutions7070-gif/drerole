import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import VideoInterviewRoom from './VideoInterviewRoom';
import { videoInterviewAPI } from '../services/api';

vi.mock('../services/api', () => ({
  videoInterviewAPI: {
    getJoinInfo: vi.fn(),
    schedule: vi.fn(),
    updateStatus: vi.fn(),
  },
}));

class MockPeerConnection {
  constructor(config) {
    this.config = config;
    this.connectionState = 'new';
    MockPeerConnection.instances.push(this);
  }
  addTrack() {}
  createOffer() { return Promise.resolve({ type: 'offer', sdp: 'offer-sdp' }); }
  createAnswer() { return Promise.resolve({ type: 'answer', sdp: 'answer-sdp' }); }
  setLocalDescription() { return Promise.resolve(); }
  setRemoteDescription() { return Promise.resolve(); }
  addIceCandidate() { return Promise.resolve(); }
  close() {}
}
MockPeerConnection.instances = [];

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

describe('VideoInterviewRoom', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    MockPeerConnection.instances = [];
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

  afterEach(() => {
    localStorage.clear();
  });

  const renderRoom = () =>
    render(
      <MemoryRouter initialEntries={['/applications/1000/video-interview']}>
        <Routes>
          <Route path="/applications/:applicationId/video-interview" element={<VideoInterviewRoom />} />
        </Routes>
      </MemoryRouter>
    );

  it('shows the lobby with the counterpart name once join info loads', async () => {
    videoInterviewAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', role: 'candidate', counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });

    renderRoom();

    expect(await screen.findByText(/video interview with acme corp/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /join call/i })).toBeInTheDocument();
  });

  it('shows an error and a way back when no interview has been scheduled', async () => {
    videoInterviewAPI.getJoinInfo.mockRejectedValue({
      response: { data: { error: 'No video interview has been scheduled for application 1000' } }
    });

    renderRoom();

    expect(await screen.findByText('No video interview has been scheduled for application 1000')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /go back/i })).toBeInTheDocument();
  });

  it('joining acquires media, opens the signaling socket and marks the interview in progress', async () => {
    videoInterviewAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', role: 'candidate', counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] }
    });
    videoInterviewAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));

    await waitFor(() => expect(global.navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ video: true, audio: true }));
    expect(MockWebSocket.instances).toHaveLength(1);
    expect(MockWebSocket.instances[0].url).toContain('roomId=room-1');
    expect(MockWebSocket.instances[0].url).toContain('token=test-token');
    await waitFor(() => expect(videoInterviewAPI.updateStatus).toHaveBeenCalledWith('1000', 'IN_PROGRESS'));
  });

  it('ending the call marks the interview completed and shows the ended screen', async () => {
    videoInterviewAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', role: 'candidate', counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    videoInterviewAPI.updateStatus.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));
    await screen.findByText(/waiting for acme corp to join/i);

    await user.click(screen.getByRole('button', { name: /end call/i }));

    expect(await screen.findByText(/call ended/i)).toBeInTheDocument();
    expect(videoInterviewAPI.updateStatus).toHaveBeenCalledWith('1000', 'COMPLETED');
  });

  it('shows a permission error and stays in the lobby when the camera/mic is denied', async () => {
    videoInterviewAPI.getJoinInfo.mockResolvedValue({
      data: { roomId: 'room-1', role: 'candidate', counterpartName: 'Acme Corp', status: 'SCHEDULED', scheduledAt: null, iceServers: [] }
    });
    global.navigator.mediaDevices.getUserMedia = vi.fn().mockRejectedValue(new Error('denied'));
    const user = userEvent.setup();

    renderRoom();
    await user.click(await screen.findByRole('button', { name: /join call/i }));

    expect(await screen.findByText(/could not access your camera\/microphone/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /join call/i })).toBeInTheDocument();
  });
});
