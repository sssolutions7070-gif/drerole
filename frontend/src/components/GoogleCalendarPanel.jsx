import React, { useEffect, useState } from 'react';
import { CalendarCheck, Link2, Unlink } from 'lucide-react';
import { calendarAPI } from '../services/api';

/** Lets the signed-in user (candidate or recruiter) connect their Google Calendar so scheduled
 *  interview rounds push there automatically (GoogleCalendarService.syncRound on the backend),
 *  instead of them having to download and import the .ics file by hand every time. */
const GoogleCalendarPanel = () => {
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');
  const [banner, setBanner] = useState(null);

  const fetchStatus = async () => {
    try {
      const response = await calendarAPI.getGoogleStatus();
      setConnected(response.data.connected);
    } catch (err) {
      setError('Failed to load Google Calendar connection status');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Google redirects back here (CalendarIntegrationController.resultUrl) after the user
    // approves or cancels the consent screen.
    const params = new URLSearchParams(window.location.search);
    const result = params.get('googleCalendar');
    if (result === 'connected') {
      setBanner({ type: 'success', text: 'Google Calendar connected.' });
    } else if (result === 'error') {
      setBanner({ type: 'error', text: params.get('message') || 'Failed to connect Google Calendar.' });
    }
    fetchStatus();
  }, []);

  const handleConnect = async () => {
    setWorking(true);
    setError('');
    try {
      const response = await calendarAPI.getGoogleConnectUrl();
      window.location.href = response.data.url;
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to start the Google Calendar connection');
      setWorking(false);
    }
  };

  const handleDisconnect = async () => {
    if (!window.confirm('Disconnect Google Calendar? Scheduled interviews will stop syncing automatically.')) {
      return;
    }
    setWorking(true);
    try {
      await calendarAPI.disconnectGoogle();
      setConnected(false);
    } catch (err) {
      setError('Failed to disconnect Google Calendar');
    } finally {
      setWorking(false);
    }
  };

  if (loading) {
    return <p className="text-sm text-gray-500">Loading...</p>;
  }

  return (
    <div>
      {banner && (
        <p className={`text-sm mb-3 ${banner.type === 'success' ? 'text-green-700' : 'text-red-600'}`}>
          {banner.text}
        </p>
      )}
      {error && <p className="text-sm text-red-600 mb-3">{error}</p>}

      {connected ? (
        <div className="flex items-center justify-between">
          <span className="inline-flex items-center text-sm font-medium text-green-700 bg-green-50 px-3 py-1.5 rounded-full">
            <CalendarCheck className="h-4 w-4 mr-1.5" />
            Connected
          </span>
          <button
            onClick={handleDisconnect}
            disabled={working}
            className="inline-flex items-center text-sm font-medium text-red-700 hover:text-red-800 border border-red-200 rounded-lg px-3 py-1.5 hover:bg-red-50 disabled:opacity-50"
          >
            <Unlink className="h-4 w-4 mr-1.5" />
            {working ? 'Disconnecting...' : 'Disconnect'}
          </button>
        </div>
      ) : (
        <button
          onClick={handleConnect}
          disabled={working}
          className="inline-flex items-center px-4 py-2 border border-blue-300 text-blue-700 rounded-lg hover:bg-blue-50 disabled:opacity-50"
        >
          <Link2 className="h-4 w-4 mr-2" />
          {working ? 'Redirecting...' : 'Connect Google Calendar'}
        </button>
      )}
    </div>
  );
};

export default GoogleCalendarPanel;
