import React, { useEffect, useState } from 'react';
import { Key, Webhook as WebhookIcon, Trash2, Copy, Plus, Code2, ChevronDown, ChevronUp } from 'lucide-react';
import { apiKeyAPI, webhookAPI } from '../services/api';
import { BACKEND_ORIGIN } from '../config';

// A curated slice of the full API, documented here for external integrators - every endpoint a
// recruiter can call with a browser session also accepts an X-API-Key header (see ApiKeyService
// and AuthInterceptor), so this list is a starting point, not the whole surface.
const DOCUMENTED_ENDPOINTS = [
  { method: 'POST', path: '/api/recruiters/jobs', description: 'Create a job posting' },
  { method: 'GET', path: '/api/recruiters/jobs/recruiter/{recruiterId}', description: "List your job postings" },
  { method: 'PUT', path: '/api/recruiters/jobs/{jobId}', description: 'Update a job posting' },
  { method: 'GET', path: '/api/recruiters/jobs/{jobId}/applications', description: 'List applications to one job' },
  { method: 'PUT', path: '/api/applications/{applicationId}/status', description: "Update an application's status" },
  { method: 'GET', path: '/api/candidates/discover', description: 'Search the open-to-work candidate pool' },
];

const EVENT_TYPES = [
  { value: 'APPLICATION_SUBMITTED', label: 'Application submitted' },
  { value: 'OFFER_ACCEPTED', label: 'Offer accepted' },
  { value: 'INTERVIEW_SCHEDULED', label: 'Interview scheduled / confirmed' },
  { value: 'TEST_COMPLETED', label: 'Assessment / test completed' },
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

const getPlatformBadge = (url) => {
  if (!url) return null;
  const lower = url.toLowerCase();
  if (lower.includes('hooks.slack.com')) {
    return { name: 'Slack Block Kit', color: 'bg-emerald-100 text-emerald-800 border-emerald-300' };
  }
  if (lower.includes('discord.com/api/webhooks') || lower.includes('discordapp.com/api/webhooks')) {
    return { name: 'Discord Embed', color: 'bg-indigo-100 text-indigo-800 border-indigo-300' };
  }
  return { name: 'Standard HMAC JSON', color: 'bg-gray-100 text-gray-700 border-gray-300' };
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
  const [testResults, setTestResults] = useState({});
  const [testingWebhookId, setTestingWebhookId] = useState(null);
  const [showApiDocs, setShowApiDocs] = useState(false);

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

  const handleTestPing = async (webhookId) => {
    setTestingWebhookId(webhookId);
    try {
      const res = await webhookAPI.testPing(recruiterId, webhookId);
      const isSuccess = res.data?.status === 'SUCCESS';
      setTestResults(prev => ({
        ...prev,
        [webhookId]: { success: isSuccess, message: res.data?.message || 'Ping delivered!' }
      }));
    } catch (err) {
      setTestResults(prev => ({
        ...prev,
        [webhookId]: { success: false, message: err.response?.data?.error || 'Ping delivery failed' }
      }));
    } finally {
      setTestingWebhookId(null);
    }
  };

  const detectedPlatform = getPlatformBadge(webhookUrl);

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

        <button
          type="button"
          onClick={() => setShowApiDocs((prev) => !prev)}
          className="inline-flex items-center gap-1.5 text-sm text-blue-600 hover:text-blue-800 font-medium mt-4"
        >
          <Code2 className="h-4 w-4" />
          API Documentation
          {showApiDocs ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </button>

        {showApiDocs && (
          <div className="mt-3 bg-gray-50 border border-gray-200 rounded-lg p-4 space-y-4">
            <div>
              <p className="text-sm text-gray-700 mb-2">
                Authenticate every request with an <code>X-API-Key</code> header set to a key generated above.
                It authenticates exactly as you - the same permissions a browser session would have.
              </p>
              <CopyBox value={`curl ${BACKEND_ORIGIN}/api/recruiters/jobs/recruiter/${recruiterId} \\\n  -H "X-API-Key: dr_live_..."`} />
            </div>

            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase mb-2">Common endpoints</p>
              <div className="space-y-1.5">
                {DOCUMENTED_ENDPOINTS.map((endpoint) => (
                  <div key={`${endpoint.method} ${endpoint.path}`} className="flex items-start gap-2 text-sm">
                    <span className="font-mono text-xs font-semibold px-1.5 py-0.5 rounded bg-blue-100 text-blue-800 flex-shrink-0 mt-0.5">
                      {endpoint.method}
                    </span>
                    <div>
                      <span className="font-mono text-xs text-gray-800">{endpoint.path}</span>
                      <span className="text-gray-500 ml-2">{endpoint.description}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>

      <div>
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-lg font-semibold text-gray-800 flex items-center gap-2">
            <WebhookIcon className="h-5 w-5" /> Team Collaboration & Webhooks
          </h3>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setWebhookUrl('https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK')}
              className="text-xs px-2.5 py-1 bg-purple-50 text-purple-700 border border-purple-200 rounded hover:bg-purple-100 font-medium"
            >
              💬 Slack Preset
            </button>
            <button
              type="button"
              onClick={() => setWebhookUrl('https://discord.com/api/webhooks/YOUR/DISCORD/WEBHOOK')}
              className="text-xs px-2.5 py-1 bg-indigo-50 text-indigo-700 border border-indigo-200 rounded hover:bg-indigo-100 font-medium"
            >
              🎮 Discord Preset
            </button>
          </div>
        </div>
        <p className="text-sm text-gray-500 mb-3">
          Get real-time Slack Block Kit alerts, Discord channel embeds, or HMAC-signed JSON posts when candidate milestones occur.
        </p>

        {justCreatedWebhook && (
          <div className="bg-yellow-50 border border-yellow-200 rounded-md p-3 mb-4">
            <p className="text-sm font-medium text-yellow-800 mb-2">
              Signing secret - copy it now to verify the <code>X-DreamRole-Signature</code> header later.
            </p>
            <CopyBox value={justCreatedWebhook.secret} />
          </div>
        )}

        <form onSubmit={handleRegisterWebhook} className="space-y-2 mb-4">
          <div className="flex flex-wrap gap-2">
            <input
              type="url"
              required
              value={webhookUrl}
              onChange={(e) => setWebhookUrl(e.target.value)}
              placeholder="https://your-app.com/webhook (or Slack / Discord webhook)"
              className="flex-1 min-w-[220px] border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
            <select
              value={webhookEvent}
              onChange={(e) => setWebhookEvent(e.target.value)}
              className="border border-gray-300 rounded-md px-3 py-2 text-sm"
            >
              {EVENT_TYPES.map((et) => (
                <option key={et.value} value={et.value}>{et.label}</option>
              ))}
            </select>
            <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 flex items-center gap-2 text-sm font-medium">
              <Plus className="h-4 w-4" /> Add webhook
            </button>
          </div>
          {webhookUrl && detectedPlatform && (
            <div className="flex items-center gap-2 text-xs">
              <span className="text-gray-500">Auto-detected formatting:</span>
              <span className={`px-2 py-0.5 border rounded-full font-medium ${detectedPlatform.color}`}>
                {detectedPlatform.name}
              </span>
            </div>
          )}
        </form>

        <div className="space-y-2">
          {webhooks.map((webhook) => {
            const platform = getPlatformBadge(webhook.url);
            const testResult = testResults[webhook.id];
            const isTesting = testingWebhookId === webhook.id;
            return (
              <div key={webhook.id} className="bg-white border border-gray-200 rounded-lg p-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div className="space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="font-medium text-gray-900 break-all text-sm">{webhook.url}</p>
                    {platform && (
                      <span className={`px-2 py-0.5 text-[11px] border rounded-full font-medium ${platform.color}`}>
                        {platform.name}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-gray-500">Event: <span className="font-mono text-gray-700">{webhook.eventType}</span></p>
                  {testResult && (
                    <div className={`text-xs p-1.5 rounded flex items-center gap-1.5 ${
                      testResult.success ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'
                    }`}>
                      <span>{testResult.success ? '✓' : '⚠️'}</span>
                      <span>{testResult.message}</span>
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-2 flex-shrink-0 self-end sm:self-center">
                  <button
                    type="button"
                    onClick={() => handleTestPing(webhook.id)}
                    disabled={isTesting}
                    className="px-2.5 py-1.5 bg-gray-50 text-gray-700 border border-gray-300 rounded hover:bg-gray-100 text-xs font-medium flex items-center gap-1 disabled:opacity-50"
                  >
                    {isTesting ? 'Sending Ping...' : '⚡ Send Test Ping'}
                  </button>
                  <button
                    onClick={() => handleRemoveWebhook(webhook.id)}
                    aria-label="Remove webhook"
                    className="text-red-500 hover:text-red-700 p-1.5"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default IntegrationsPanel;
