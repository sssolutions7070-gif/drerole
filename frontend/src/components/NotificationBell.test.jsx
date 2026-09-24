import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import NotificationBell from './NotificationBell';
import { notificationAPI } from '../services/api';

vi.mock('../services/api');

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
}

const notification = (overrides = {}) => ({
  id: 1, type: 'NEW_MESSAGE', title: 'New message', message: 'Hi there', link: '/dashboard',
  read: false, createdAt: '2026-01-01T10:00:00', ...overrides,
});

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    FakeWebSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
    localStorage.clear();
    notificationAPI.unreadCount.mockResolvedValue({ data: { count: 0 } });
    notificationAPI.list.mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const renderBell = () => render(
    <MemoryRouter>
      <NotificationBell />
    </MemoryRouter>
  );

  it('renders nothing when logged out', () => {
    const { container } = renderBell();

    expect(container).toBeEmptyDOMElement();
  });

  it('shows an unread badge from the unread-count endpoint', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');
    notificationAPI.unreadCount.mockResolvedValue({ data: { count: 3 } });

    renderBell();

    expect(await screen.findByText('3')).toBeInTheDocument();
  });

  it('loads and shows the notification list when opened', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');
    notificationAPI.list.mockResolvedValue({ data: [notification()] });
    const user = userEvent.setup();

    renderBell();
    await user.click(screen.getByRole('button', { name: /notifications/i }));

    expect(await screen.findByText('New message')).toBeInTheDocument();
    expect(screen.getByText('Hi there')).toBeInTheDocument();
  });

  it('shows an empty state when there are no notifications', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');
    const user = userEvent.setup();

    renderBell();
    await user.click(screen.getByRole('button', { name: /notifications/i }));

    expect(await screen.findByText(/no notifications yet/i)).toBeInTheDocument();
  });

  it('marks a notification read and navigates when clicked', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');
    notificationAPI.list.mockResolvedValue({ data: [notification()] });
    notificationAPI.markRead.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderBell();
    await user.click(screen.getByRole('button', { name: /notifications/i }));
    await user.click(await screen.findByText('New message'));

    expect(notificationAPI.markRead).toHaveBeenCalledWith(1, 1);
  });

  it('marks all notifications read', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');
    notificationAPI.list.mockResolvedValue({ data: [notification()] });
    notificationAPI.markAllRead.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    renderBell();
    await user.click(screen.getByRole('button', { name: /notifications/i }));
    await user.click(await screen.findByText(/mark all read/i));

    expect(notificationAPI.markAllRead).toHaveBeenCalledWith(1);
  });

  it('refreshes the unread count when a realtime event arrives', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha' }));
    localStorage.setItem('token', 'my-token');
    notificationAPI.unreadCount.mockResolvedValueOnce({ data: { count: 0 } }).mockResolvedValue({ data: { count: 1 } });

    renderBell();
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));

    act(() => {
      FakeWebSocket.instances[0].onmessage?.({ data: JSON.stringify({ type: 'NEW_MESSAGE', data: {} }) });
    });

    expect(await screen.findByText('1')).toBeInTheDocument();
  });
});
