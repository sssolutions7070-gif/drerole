import React, { useEffect, useState } from 'react';
import { X, ShieldCheck, Copy, Check } from 'lucide-react';
import api from '../services/api';

// Groups a base32 secret into 4-character chunks so it's easier to type by hand into an
// authenticator app - most apps also accept scanning otpauthUri, but not every device can scan.
const formatSecret = (secret) => secret.match(/.{1,4}/g)?.join(' ') || secret;

const TwoFactorSetupModal = ({ userId, onClose, onEnabled }) => {
  const [step, setStep] = useState('loading'); // loading -> enter-code -> backup-codes
  const [secret, setSecret] = useState('');
  const [otpauthUri, setOtpauthUri] = useState('');
  const [code, setCode] = useState('');
  const [backupCodes, setBackupCodes] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await api.post(`/users/${userId}/2fa/setup`);
        if (cancelled) return;
        setSecret(response.data.secret);
        setOtpauthUri(response.data.otpauthUri);
        setStep('enter-code');
      } catch (err) {
        if (!cancelled) setError(err.response?.data?.error || 'Failed to start setup');
      }
    })();
    return () => { cancelled = true; };
  }, [userId]);

  const handleConfirm = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const response = await api.post(`/users/${userId}/2fa/confirm`, { secret, code });
      setBackupCodes(response.data.backupCodes);
      setStep('backup-codes');
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to verify code');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopySecret = async () => {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be denied by the browser; the secret is still shown as text.
    }
  };

  const handleDone = () => {
    onEnabled();
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
      <div className="relative top-10 mx-auto p-6 border w-full max-w-md shadow-lg rounded-md bg-white">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold text-gray-900 flex items-center">
            <ShieldCheck className="h-5 w-5 mr-2 text-blue-600" />
            Two-Factor Authentication
          </h2>
          {step !== 'backup-codes' && (
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
              <X className="h-6 w-6" />
            </button>
          )}
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-100 border border-red-400 text-red-700 rounded text-sm">
            {error}
          </div>
        )}

        {step === 'loading' && <p className="text-gray-500 text-sm">Setting up...</p>}

        {step === 'enter-code' && (
          <form onSubmit={handleConfirm} className="space-y-4">
            <p className="text-sm text-gray-600">
              Open your authenticator app (Google Authenticator, Authy, 1Password, etc.) and add a
              new account using this setup key:
            </p>
            <div className="flex items-center justify-between bg-gray-50 rounded-lg p-3 font-mono text-sm">
              <span>{formatSecret(secret)}</span>
              <button type="button" onClick={handleCopySecret} className="text-blue-600 hover:text-blue-800 ml-2 flex-shrink-0">
                {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
              </button>
            </div>
            <p className="text-xs text-gray-400">
              Account name: your email &middot; Time-based, 6 digits, 30 second period
            </p>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Enter the 6-digit code it shows
              </label>
              <input
                type="text"
                inputMode="numeric"
                autoFocus
                value={code}
                onChange={(e) => setCode(e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-center text-lg tracking-widest"
                placeholder="123456"
              />
            </div>
            <button
              type="submit"
              disabled={submitting || code.length !== 6}
              className="w-full bg-blue-600 text-white py-2 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {submitting ? 'Verifying...' : 'Verify and enable'}
            </button>
          </form>
        )}

        {step === 'backup-codes' && (
          <div className="space-y-4">
            <p className="text-sm text-gray-700">
              Two-factor authentication is now on. Save these one-time backup codes somewhere
              safe - each one works once, and they're the only way back into your account if you
              lose access to your authenticator app. They won't be shown again.
            </p>
            <div className="grid grid-cols-2 gap-2 bg-gray-50 rounded-lg p-4 font-mono text-sm">
              {backupCodes.map((backupCode) => (
                <div key={backupCode}>{backupCode}</div>
              ))}
            </div>
            <button
              onClick={handleDone}
              className="w-full bg-blue-600 text-white py-2 rounded-lg font-medium hover:bg-blue-700"
            >
              I've saved these codes
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default TwoFactorSetupModal;
