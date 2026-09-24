import React, { useEffect, useState } from 'react';
import { Key, Webhook as WebhookIcon, Trash2, Copy, Plus } from 'lucide-react';
import { apiKeyAPI, webhookAPI } from '../services/api';

const EVENT_TYPES = [
  { value: 'APPLICATION_SUBMITTED', label: 'Application submitted' },
  { value: 'OFFER_ACCEPTED', label: 'Offer accepted' },
];

const CopyBox = ({ value }) => {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (e) {
      // Ignore - the value is still visible to copy manually.
    }
  };
  return (
    <div className="flex items-center gap-2 bg-gray-50 border border-gray-200 rounded-md px-3 py-2 font-mono text-xs break-all">
      <span className="flex-1">{value}</span>
      <button type="button" onClick={copy} className="text-blue-600 hover:text-blue-800 flex-shrink-0">
        {copied ? 'Copied!' : <Copy className="h-4 w-4" />}
      </button>
    </div>
  );
};

/** Developer-facing integrations for a recruiter's own account: API keys for calling the API as
 *  themselves, and webhooks to be notified of events without polling. Owner-only in the backend;
 *  this panel doesn't hide itself from team members, but every action here will 403 for them. */
const IntegrationsPanel = ({ recruiterId }) => {
  const [keys, setKeys] = useState([]);
  const [webhooks, setWebhooks] = useState([]);
  const [error, setError] = useState('');
  const [newKeyLabel, setNewKeyLabel] = useState('');
  const [justCreatedKey, setJustCreatedKey] = useState(null);
  const [webhookUrl, setWebhookUrl] = useState('');
  const [webhookEvent, setWebhookEvent] = useState(EVENT_TYPES[0].value);
  const [justCreatedWebhook, setJustCreatedWebhook] = useState(null);

  const fetchAll = async () => {
    try {
      const [keysRes, webhooksRes] = await Promise.all([
        apiKeyAPI.list(recruiterId),
        webhookAPI.list(recruiterId),
      ]);
      setKeys(keysRes.data);
      setWebhooks(webhooksRes.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load integrations');
    }
  };

  useEffect(() => {
    fetchAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recruiterId]);

  const handleGenerateKey = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const response = await apiKeyAPI.generate(recruiterId, newKeyLabel.trim() || 'Untitled key');
      setJustCreatedKey(response.data);
      setNewKeyLabel('');
      await fetchAll();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to generate key');
    }
  };

  const handleRevokeKey = async (keyId) => {
    setError('');
    try {
      await apiKeyAPI.revoke(recruiterId, keyId);
      await fetchAll();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to revoke key');
    }
  };

  const handleRegisterWebhook = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const response = await webhookAPI.register(recruiterId, webhookUrl.trim(), webhookEvent);
      setJustCreatedWebhook(response.data);
      setWebhookUrl('');
      await fetchAll();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to register webhook');
    }
  };

  const handleRemoveWebhook = async (webhookId) => {
    setError('');
    try {
      await webhookAPI.remove(recruiterId, webhookId);
      await fetchAll();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to remove webhook');
    }
  };

  return (
    <div className="space-y-8">
      {error && <p className="text-red-600 text-sm">{error}</p>}

      <div>
        <h3 className="text-lg font-semibold text-gray-800 flex items-center gap-2 mb-2">
          <Key className="h-5 w-5" /> API Keys
        </h3>
        <p className="text-sm text-gray-500 mb-3">
          Call the DreamRole API as yourself from a script or your own ATS, using an <code>X-API-Key</code> header instead of logging in.
        </p>

        {justCreatedKey && (
          <div className="bg-yellow-50 border border-yellow-200 rounded-md p-3 mb-4">
            <p className="text-sm font-medium text-yellow-800 mb-2">
              Copy this key now - it won't be shown again.
            </p>
            <CopyBox value={justCreatedKey.key} />
          </div>
        )}

        <form onSubmit={handleGenerateKey} className="flex gap-2 mb-4">
          <input
            type="text"
            value={newKeyLabel}
            onChange={(e) => setNewKeyLabel(e.target.value)}
            placeholder="Label (e.g. Our ATS)"
            className="flex-1 border border-gray-300 rounded-md px-3 py-2"
          />
          <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 flex items-center gap-2">
            <Plus className="h-4 w-4" /> Generate key
          </button>
        </form>

        <div className="space-y-2">
          {keys.map((key) => (
            <div key={key.id} className="bg-white border border-gray-200 rounded-lg p-3 flex items-center justify-between">
              <div>
                <p className="font-medium text-gray-900">{key.label || 'Untitled key'}</p>
                <p className="text-xs text-gray-500 font-mono">{key.keyPrefix}</p>
              </div>
              <div className="flex items-center gap-3">
                {key.revoked ? (
                  <span className="text-xs text-gray-400">Revoked</span>
                ) : (
                  <button onClick={() => handleRevokeKey(key.id)} aria-label={`Revoke ${key.label || 'key'}`} className="text-red-500 hover:text-red-700">
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div>
        <h3 className="text-lg font-semibold text-gray-800 flex items-center gap-2 mb-2">
          <WebhookIcon className="h-5 w-5" /> Webhooks
        </h3>
        <p className="text-sm text-gray-500 mb-3">
          Get an HMAC-signed POST to your URL when one of these events happens, instead of polling the API.
        </p>

        {justCreatedWebhook && (
          <div className="bg-yellow-50 border border-yellow-200 rounded-md p-3 mb-4">
            <p className="text-sm font-medium text-yellow-800 mb-2">
              Signing secret - copy it now to verify the <code>X-DreamRole-Signature</code> header later.
            </p>
            <CopyBox value={justCreatedWebhook.secret} />
          </div>
        )}

        <form onSubmit={handleRegisterWebhook} className="flex flex-wrap gap-2 mb-4">
          <input
            type="url"
            required
            value={webhookUrl}
            onChange={(e) => setWebhookUrl(e.target.value)}
            placeholder="https://your-app.com/webhook"
            className="flex-1 min-w-[220px] border border-gray-300 rounded-md px-3 py-2"
          />
          <select
            value={webhookEvent}
            onChange={(e) => setWebhookEvent(e.target.value)}
            className="border border-gray-300 rounded-md px-3 py-2"
          >
            {EVENT_TYPES.map((et) => (
              <option key={et.value} value={et.value}>{et.label}</option>
            ))}
          </select>
          <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 flex items-center gap-2">
            <Plus className="h-4 w-4" /> Add webhook
          </button>
        </form>

        <div className="space-y-2">
          {webhooks.map((webhook) => (
            <div key={webhook.id} className="bg-white border border-gray-200 rounded-lg p-3 flex items-center justify-between">
              <div>
                <p className="font-medium text-gray-900 break-all">{webhook.url}</p>
                <p className="text-xs text-gray-500">{webhook.eventType}</p>
              </div>
              <button onClick={() => handleRemoveWebhook(webhook.id)} aria-label="Remove webhook" className="text-red-500 hover:text-red-700 flex-shrink-0">
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default IntegrationsPanel;
