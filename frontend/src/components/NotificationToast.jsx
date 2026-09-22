import React, { useEffect, useState } from 'react';
import { Bell, X, CalendarCheck } from 'lucide-react';
import useRealtimeNotifications from '../hooks/useRealtimeNotifications';

const TOAST_LIFETIME_MS = 8000;

const describeEvent = (event) => {
  if (!event) return null;
  const { type, data } = event;

  if (type === 'APPLICATION_STATUS_CHANGED') {
    const status = (data?.status || '').replace(/_/g, ' ').toLowerCase();
    return {
      icon: Bell,
      title: data?.jobTitle ? `Update on ${data.jobTitle}` : 'Application update',
      message: `Your application status is now: ${status || 'updated'}.`,
    };
  }

  if (type === 'INTERVIEW_SCHEDULED') {
    return {
      icon: CalendarCheck,
      title: data?.jobTitle ? `Interview scheduled: ${data.jobTitle}` : 'Interview scheduled',
      message: [data?.interviewDate, data?.interviewLocation].filter(Boolean).join(' • ') || 'Check your applications for details.',
    };
  }

  return { icon: Bell, title: 'New update', message: 'Check your applications for details.' };
};

const NotificationToast = () => {
  const currentUser = (() => {
    try {
      return JSON.parse(localStorage.getItem('user'));
    } catch (error) {
      return null;
    }
  })();
  const token = currentUser ? localStorage.getItem('token') : null;

  const lastEvent = useRealtimeNotifications(token);
  const [toasts, setToasts] = useState([]);

  useEffect(() => {
    if (!lastEvent) return;
    const description = describeEvent(lastEvent);
    if (!description) return;

    const id = lastEvent.receivedAt;
    setToasts((prev) => [...prev, { id, ...description }]);

    const timeoutId = setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, TOAST_LIFETIME_MS);

    return () => clearTimeout(timeoutId);
  }, [lastEvent]);

  const dismiss = (id) => setToasts((prev) => prev.filter((toast) => toast.id !== id));

  if (!token || toasts.length === 0) {
    return null;
  }

  return (
    <div className="fixed top-4 right-4 z-50 space-y-2 w-full max-w-sm">
      {toasts.map(({ id, icon: Icon, title, message }) => (
        <div key={id} className="bg-white border border-gray-200 shadow-lg rounded-lg p-4 flex items-start gap-3">
          <Icon className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-gray-900">{title}</p>
            <p className="text-sm text-gray-600 mt-0.5">{message}</p>
          </div>
          <button
            type="button"
            onClick={() => dismiss(id)}
            className="text-gray-400 hover:text-gray-600 flex-shrink-0"
            aria-label="Dismiss notification"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ))}
    </div>
  );
};

export default NotificationToast;
