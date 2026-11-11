import React, { useState } from 'react';
import { X, ShieldOff } from 'lucide-react';
import api from '../services/api';

const DisableTwoFactorModal = ({ userId, onClose, onDisabled }) => {
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await api.post(`/users/${userId}/2fa/disable`, { password });
      onDisabled();
      onClose();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to disable two-factor authentication');
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
      <div className="relative top-20 mx-auto p-6 border w-full max-w-sm shadow-lg rounded-md bg-white">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-lg font-bold text-gray-900 flex items-center">
            <ShieldOff className="h-5 w-5 mr-2 text-red-600" />
            Disable Two-Factor Auth
          </h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>
        <p className="text-sm text-gray-600 mb-4">
          Confirm your password to turn off two-factor authentication for your account.
        </p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            type="password"
            autoFocus
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-3 py-2"
            placeholder="Current password"
          />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-red-600 text-white py-2 rounded-lg font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {submitting ? 'Disabling...' : 'Disable Two-Factor Auth'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default DisableTwoFactorModal;
