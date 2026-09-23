import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import NotificationToast from './NotificationToast';

class FakeWebSocket {
  static instances = [];

  constructor(url) {
    this.url = url;
    this.onopen = null;
    this.onmessage = null;
    this.onclose = null;
    this.onerror = null;
    FakeWebSocket.instances.push(this);
  }

  send() {}
  close() {
    this.onclose?.();
  }

  emitMessage(payload) {
    this.onmessage?.({ data: JSON.stringify(payload) });
  }
}

describe('NotificationToast', () => {
  beforeEach(() => {
    FakeWebSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders nothing and opens no socket when logged out', () => {
    const { container } = render(<NotificationToast />);

    expect(container).toBeEmptyDOMElement();
    expect(FakeWebSocket.instances).toHaveLength(0);
  });

  it('opens a socket with the token as a query parameter when logged in', () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');

    render(<NotificationToast />);

    expect(FakeWebSocket.instances).toHaveLength(1);
    expect(FakeWebSocket.instances[0].url).toContain('token=my-token');
  });

  it('shows a toast when an application-status event arrives', () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');

    render(<NotificationToast />);
    const socket = FakeWebSocket.instances[0];

    act(() => {
      socket.emitMessage({ type: 'APPLICATION_STATUS_CHANGED', data: { jobTitle: 'Backend Developer', status: 'SHORTLISTED' } });
    });

    expect(screen.getByText('Update on Backend Developer')).toBeInTheDocument();
    expect(screen.getByText(/shortlisted/i)).toBeInTheDocument();
  });

  it('shows an interview-scheduled toast with the date and location', () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');

    render(<NotificationToast />);
    const socket = FakeWebSocket.instances[0];

    act(() => {
      socket.emitMessage({
        type: 'INTERVIEW_SCHEDULED',
        data: { jobTitle: 'Backend Developer', interviewDate: '2026-10-01T14:00', interviewLocation: 'Zoom' },
      });
    });

    expect(screen.getByText('Interview scheduled: Backend Developer')).toBeInTheDocument();
    expect(screen.getByText(/Zoom/)).toBeInTheDocument();
  });

  it('shows a new-message toast with the message preview', () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');

    render(<NotificationToast />);
    const socket = FakeWebSocket.instances[0];

    act(() => {
      socket.emitMessage({
        type: 'NEW_MESSAGE',
        data: { applicationId: 42, senderId: 2, preview: 'Are you available Friday?' },
      });
    });

    expect(screen.getByText('New message')).toBeInTheDocument();
    expect(screen.getByText('Are you available Friday?')).toBeInTheDocument();
  });

  it('dismisses a toast when its close button is clicked', () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');

    render(<NotificationToast />);
    const socket = FakeWebSocket.instances[0];

    act(() => {
      socket.emitMessage({ type: 'APPLICATION_STATUS_CHANGED', data: { jobTitle: 'Backend Developer', status: 'REJECTED' } });
    });

    const dismissButton = screen.getByRole('button', { name: /dismiss notification/i });
    act(() => {
      dismissButton.click();
    });

    expect(screen.queryByText('Update on Backend Developer')).not.toBeInTheDocument();
  });

  it('ignores malformed JSON payloads without crashing', () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');

    render(<NotificationToast />);
    const socket = FakeWebSocket.instances[0];

    expect(() => {
      act(() => {
        socket.onmessage?.({ data: 'not json' });
      });
    }).not.toThrow();
  });
});
