import React, { useEffect, useState } from 'react';
import { Monitor, MapPin, Clock, LogOut } from 'lucide-react';
import { accountAPI } from '../services/api';

/** Lets the signed-in user see every device currently logged into their account and sign one of
 *  them out remotely - e.g. after losing a phone or using a shared computer. */
const ActiveSessionsPanel = ({ userId }) => {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revokingId, setRevokingId] = useState(null);

  const fetchSessions = async () => {
    try {
      const response = await accountAPI.getSessions(userId);
      setSessions(response.data);
    } catch (err) {
      setError('Failed to load active sessions');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  const handleRevoke = async (sessionId) => {
    if (!window.confirm('Sign this device out? It will need to log in again.')) return;
    setRevokingId(sessionId);
    try {
      await accountAPI.revokeSession(userId, sessionId);
      setSessions((prev) => prev.filter((s) => s.id !== sessionId));
    } catch (err) {
      alert('Failed to sign out that device');
    } finally {
      setRevokingId(null);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'Unknown';
    return new Date(dateString).toLocaleString(undefined, {
      month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
    });
  };

  if (loading) return <p className="text-sm text-gray-500">Loading sessions...</p>;
  if (error) return <p className="text-sm text-red-600">{error}</p>;

  return (
    <div className="space-y-2">
      {sessions.length === 0 ? (
        <p className="text-sm text-gray-500">No active sessions found.</p>
      ) : (
        sessions.map((session) => (
          <div
            key={session.id}
            className={`flex items-center justify-between px-3 py-2 rounded-lg border ${
              session.current ? 'border-blue-200 bg-blue-50/50' : 'border-gray-200'
            }`}
          >
            <div className="flex items-start gap-2">
              <Monitor className="h-4 w-4 text-gray-400 mt-0.5" />
              <div>
                <p className="text-sm font-medium text-gray-800 flex items-center gap-2">
                  {session.device}
                  {session.current && (
                    <span className="text-xs font-semibold text-blue-700 bg-blue-100 px-2 py-0.5 rounded-full">
                      This device
                    </span>
                  )}
                </p>
                <p className="text-xs text-gray-500 flex items-center gap-3 mt-0.5">
                  {session.ipAddress && (
                    <span className="flex items-center gap-1">
                      <MapPin className="h-3 w-3" /> {session.ipAddress}
                    </span>
                  )}
                  <span className="flex items-center gap-1">
                    <Clock className="h-3 w-3" /> Last active {formatDate(session.lastUsedAt)}
                  </span>
                </p>
              </div>
            </div>
            {!session.current && (
              <button
                onClick={() => handleRevoke(session.id)}
                disabled={revokingId === session.id}
                className="inline-flex items-center text-xs font-medium text-red-700 hover:text-red-800 border border-red-200 rounded-full px-2.5 py-1 hover:bg-red-50 disabled:opacity-50"
              >
                <LogOut className="h-3 w-3 mr-1" />
                {revokingId === session.id ? 'Signing out...' : 'Sign out'}
              </button>
            )}
          </div>
        ))
      )}
    </div>
  );
};

export default ActiveSessionsPanel;
