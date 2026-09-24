import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell } from 'lucide-react';
import authService from '../services/auth';
import { notificationAPI } from '../services/api';
import useRealtimeNotifications from '../hooks/useRealtimeNotifications';

const formatWhen = (isoString) => {
  if (!isoString) return '';
  try {
    return new Date(isoString).toLocaleString();
  } catch (error) {
    return '';
  }
};

/** Bell icon + dropdown showing the persisted notification feed (NotificationController), so a
 *  user still sees what happened while they were away - unlike NotificationToast, which only
 *  shows events pushed while a tab is open. */
const NotificationBell = () => {
  const navigate = useNavigate();
  const currentUser = authService.getCurrentUser();
  const token = currentUser ? authService.getToken() : null;
  const lastEvent = useRealtimeNotifications(token);

  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const menuRef = useRef(null);

  const refreshUnreadCount = async () => {
    if (!currentUser) return;
    try {
      const response = await notificationAPI.unreadCount(currentUser.id);
      setUnreadCount(response.data.count);
    } catch (error) {
      // Silently ignore - the bell just won't show a badge this time.
    }
  };

  useEffect(() => {
    refreshUnreadCount();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser?.id]);

  useEffect(() => {
    if (lastEvent) {
      refreshUnreadCount();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastEvent]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const toggleOpen = async () => {
    const next = !open;
    setOpen(next);
    if (next && currentUser) {
      try {
        const response = await notificationAPI.list(currentUser.id);
        setNotifications(response.data);
      } catch (error) {
        setNotifications([]);
      }
    }
  };

  const handleSelect = async (notification) => {
    setOpen(false);
    if (!notification.read) {
      try {
        await notificationAPI.markRead(currentUser.id, notification.id);
        setUnreadCount((prev) => Math.max(0, prev - 1));
      } catch (error) {
        // Ignore - navigating still works even if marking-read failed.
      }
    }
    if (notification.link) {
      navigate(notification.link);
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await notificationAPI.markAllRead(currentUser.id);
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      setUnreadCount(0);
    } catch (error) {
      // Ignore - user can retry.
    }
  };

  if (!currentUser) {
    return null;
  }

  return (
    <div className="relative" ref={menuRef}>
      <button
        type="button"
        onClick={toggleOpen}
        aria-label="Notifications"
        className="relative p-2 text-gray-600 hover:text-blue-600 transition-colors focus:outline-none"
      >
        <Bell className="h-6 w-6" />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 inline-flex items-center justify-center h-5 min-w-[1.25rem] px-1 rounded-full text-xs font-semibold text-white bg-red-600">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-xl border border-gray-200 py-2 z-50">
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
            <p className="text-sm font-semibold text-gray-800">Notifications</p>
            {notifications.some((n) => !n.read) && (
              <button
                type="button"
                onClick={handleMarkAllRead}
                className="text-xs text-blue-600 hover:underline"
              >
                Mark all read
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {notifications.length === 0 ? (
              <p className="px-4 py-6 text-sm text-gray-500 text-center">No notifications yet.</p>
            ) : (
              notifications.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  onClick={() => handleSelect(notification)}
                  className={`block w-full text-left px-4 py-3 border-b border-gray-50 hover:bg-gray-50 ${
                    notification.read ? '' : 'bg-blue-50'
                  }`}
                >
                  <p className="text-sm font-medium text-gray-800">{notification.title}</p>
                  <p className="text-sm text-gray-600 mt-0.5">{notification.message}</p>
                  <p className="text-xs text-gray-400 mt-1">{formatWhen(notification.createdAt)}</p>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default NotificationBell;
