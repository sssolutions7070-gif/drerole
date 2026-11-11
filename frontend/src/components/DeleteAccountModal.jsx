import React, { useState } from 'react';
import { X, AlertTriangle } from 'lucide-react';
import { accountAPI } from '../services/api';
import authService from '../services/auth';

const DeleteAccountModal = ({ userId, onClose }) => {
  const [password, setPassword] = useState('');
  const [confirmText, setConfirmText] = useState('');
  const [error, setError] = useState('');
  const [deleting, setDeleting] = useState(false);

  const canSubmit = password.length > 0 && confirmText.trim().toUpperCase() === 'DELETE' && !deleting;

  const handleDelete = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;

    setDeleting(true);
    setError('');

    try {
      await accountAPI.deleteMyAccount(userId, password);
      // The account (and its session token) no longer exists; clear local state and leave.
      authService.logout();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to delete account. Please try again.');
      setDeleting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
      <div className="relative top-20 mx-auto p-6 border w-full max-w-md shadow-lg rounded-md bg-white">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold text-red-700 flex items-center">
            <AlertTriangle className="h-5 w-5 mr-2" />
            Delete Account
          </h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>

        <p className="text-sm text-gray-700 mb-4">
          This permanently deletes your profile, resumes, applications, saved jobs, messages and
          ratings. It cannot be undone. If you'd rather keep your data, use "Download My Data"
          first.
        </p>

        <form onSubmit={handleDelete} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Confirm your password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2"
              autoFocus
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Type <span className="font-mono font-bold">DELETE</span> to confirm
            </label>
            <input
              type="text"
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2"
            />
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex space-x-2 pt-2">
            <button
              type="submit"
              disabled={!canSubmit}
              className="flex-1 bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {deleting ? 'Deleting...' : 'Permanently Delete My Account'}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="border border-gray-300 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default DeleteAccountModal;
