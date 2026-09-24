import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CreditCard, CheckCircle, Sparkles } from 'lucide-react';
import api from '../services/api';

const SubscriptionPanel = ({ recruiterId }) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchStatus = async () => {
    try {
      const response = await api.get(`/recruiters/${recruiterId}/subscription`);
      setStatus(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load subscription status');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recruiterId]);

  const banner = searchParams.get('subscription');

  const dismissBanner = () => {
    const next = new URLSearchParams(searchParams);
    next.delete('subscription');
    setSearchParams(next, { replace: true });
  };

  const handleUpgrade = async () => {
    setActionLoading(true);
    setError('');
    try {
      const response = await api.post(`/recruiters/${recruiterId}/subscription/checkout`);
      window.location.href = response.data.url;
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to start checkout');
      setActionLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!window.confirm('Cancel your Pro subscription? You will be moved back to the Free plan immediately.')) {
      return;
    }
    setActionLoading(true);
    setError('');
    try {
      await api.post(`/recruiters/${recruiterId}/subscription/cancel`);
      await fetchStatus();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to cancel subscription');
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return <p className="text-gray-500">Loading subscription...</p>;
  }

  const isPro = status?.tier === 'PRO';

  return (
    <div className="max-w-2xl">
      {banner === 'success' && (
        <div className="mb-4 p-3 bg-green-50 border border-green-200 rounded-md flex justify-between items-center">
          <span className="text-green-800 text-sm">Checkout complete - your plan will update once Stripe confirms the payment.</span>
          <button onClick={dismissBanner} className="text-green-600 text-sm font-medium">Dismiss</button>
        </div>
      )}
      {banner === 'cancelled' && (
        <div className="mb-4 p-3 bg-gray-50 border border-gray-200 rounded-md flex justify-between items-center">
          <span className="text-gray-700 text-sm">Checkout was cancelled - you're still on your current plan.</span>
          <button onClick={dismissBanner} className="text-gray-600 text-sm font-medium">Dismiss</button>
        </div>
      )}
      {error && <p className="text-red-600 text-sm mb-4">{error}</p>}

      <div className="bg-white rounded-lg shadow-lg border border-gray-200 p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className={`w-12 h-12 rounded-full flex items-center justify-center ${isPro ? 'bg-purple-100' : 'bg-gray-100'}`}>
            {isPro ? <Sparkles className="h-6 w-6 text-purple-600" /> : <CreditCard className="h-6 w-6 text-gray-600" />}
          </div>
          <div>
            <h3 className="text-lg font-semibold text-gray-900">{isPro ? 'Pro plan' : 'Free plan'}</h3>
            {status?.status && <p className="text-sm text-gray-500">Status: {status.status}</p>}
          </div>
        </div>

        {!isPro && (
          <p className="text-sm text-gray-600 mb-4">
            {status.activeJobCount} of {status.freeTierJobLimit} active job postings used.
            Upgrade to Pro for unlimited active postings.
          </p>
        )}

        {isPro ? (
          <button
            onClick={handleCancel}
            disabled={actionLoading}
            className="px-4 py-2 border border-red-300 text-red-700 rounded-md text-sm font-medium hover:bg-red-50 disabled:opacity-50"
          >
            {actionLoading ? 'Cancelling...' : 'Cancel subscription'}
          </button>
        ) : (
          <button
            onClick={handleUpgrade}
            disabled={actionLoading || !status?.configured}
            className="px-4 py-2 bg-purple-600 text-white rounded-md text-sm font-medium hover:bg-purple-700 disabled:opacity-50"
            title={!status?.configured ? 'Subscriptions are not configured on this server yet' : undefined}
          >
            {actionLoading ? 'Redirecting...' : 'Upgrade to Pro'}
          </button>
        )}
        {!status?.configured && !isPro && (
          <p className="text-xs text-gray-400 mt-2">Subscriptions are not configured on this server yet.</p>
        )}
        {isPro && (
          <div className="mt-3 flex items-center text-sm text-green-700">
            <CheckCircle className="h-4 w-4 mr-1" /> Unlimited active job postings
          </div>
        )}
      </div>
    </div>
  );
};

export default SubscriptionPanel;
