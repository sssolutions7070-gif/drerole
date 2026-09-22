import { useEffect, useRef, useState } from 'react';
import { WS_ORIGIN } from '../config';

const INITIAL_RECONNECT_DELAY_MS = 3000;
const MAX_RECONNECT_DELAY_MS = 30000;

/**
 * Opens a WebSocket to the backend's per-user notification channel while a token is present,
 * and reconnects with backoff if the connection drops. Returns the most recent event; the
 * caller decides what to do with it (e.g. show a toast).
 */
const useRealtimeNotifications = (token) => {
  const [lastEvent, setLastEvent] = useState(null);
  const socketRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectDelayRef = useRef(INITIAL_RECONNECT_DELAY_MS);
  const closedByCallerRef = useRef(false);

  useEffect(() => {
    if (!token) {
      return undefined;
    }

    closedByCallerRef.current = false;

    const connect = () => {
      const socket = new WebSocket(`${WS_ORIGIN}/ws/notifications?token=${encodeURIComponent(token)}`);
      socketRef.current = socket;

      socket.onopen = () => {
        reconnectDelayRef.current = INITIAL_RECONNECT_DELAY_MS;
      };

      socket.onmessage = (event) => {
        try {
          const parsed = JSON.parse(event.data);
          setLastEvent({ ...parsed, receivedAt: Date.now() });
        } catch (error) {
          // Ignore malformed payloads rather than crashing the listener.
        }
      };

      socket.onclose = () => {
        if (closedByCallerRef.current) {
          return;
        }
        reconnectTimeoutRef.current = setTimeout(connect, reconnectDelayRef.current);
        reconnectDelayRef.current = Math.min(reconnectDelayRef.current * 2, MAX_RECONNECT_DELAY_MS);
      };

      socket.onerror = () => {
        socket.close();
      };
    };

    connect();

    return () => {
      closedByCallerRef.current = true;
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      socketRef.current?.close();
    };
  }, [token]);

  return lastEvent;
};

export default useRealtimeNotifications;
