import React, { useEffect, useState } from 'react';
import { CheckCircle, XCircle, FileText, BadgeCheck } from 'lucide-react';
import { adminVerificationAPI } from '../services/api';

/** The admin review queue for "Verified Employer" submissions - the same moderation shape as
 *  pending jobs/reviews/interview-experiences elsewhere in this dashboard. */
const PendingVerificationsPanel = () => {
  const [pending, setPending] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [rejectingId, setRejectingId] = useState(null);
  const [rejectReason, setRejectReason] = useState('');

  const fetchPending = async () => {
    try {
      const response = await adminVerificationAPI.listPending();
      setPending(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load pending verifications');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPending();
  }, []);

  const handleApprove = async (recruiterId) => {
    try {
      await adminVerificationAPI.approve(recruiterId);
      await fetchPending();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to approve');
    }
  };

  const handleViewDocument = async (recruiterId) => {
    try {
      const response = await adminVerificationAPI.downloadDocument(recruiterId);
      const url = window.URL.createObjectURL(response.data);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load the document');
    }
  };

  const handleReject = async (recruiterId) => {
    try {
      await adminVerificationAPI.reject(recruiterId, rejectReason.trim() || undefined);
      setRejectingId(null);
      setRejectReason('');
      await fetchPending();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to reject');
    }
  };

  if (loading) return <p className="text-gray-500 text-sm">Loading...</p>;
  if (error) return <p className="text-red-600 text-sm">{error}</p>;

  if (pending.length === 0) {
    return (
      <div className="text-center py-12 bg-white rounded-lg shadow">
        <BadgeCheck className="mx-auto h-16 w-16 text-gray-400 mb-4" />
        <p className="text-gray-500">No verification requests waiting for review.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {pending.map((item) => (
        <div key={item.recruiterId} className="bg-white border border-gray-200 rounded-lg p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="font-medium text-gray-900">{item.companyName}</p>
              {item.industry && <p className="text-sm text-gray-500">{item.industry}</p>}
              {item.companyWebsite && <p className="text-sm text-blue-600">{item.companyWebsite}</p>}
            </div>
            {item.verificationDocumentPath && (
              <button
                onClick={() => handleViewDocument(item.recruiterId)}
                className="inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-800"
              >
                <FileText className="h-4 w-4" /> View document
              </button>
            )}
          </div>

          <div className="flex flex-wrap gap-2 mt-3">
            <button
              onClick={() => handleApprove(item.recruiterId)}
              className="inline-flex items-center px-3 py-1.5 bg-green-600 text-white rounded-md text-sm font-medium hover:bg-green-700"
            >
              <CheckCircle className="h-4 w-4 mr-1" /> Approve
            </button>
            <button
              onClick={() => setRejectingId(item.recruiterId)}
              className="inline-flex items-center px-3 py-1.5 border border-red-300 text-red-700 rounded-md text-sm font-medium hover:bg-red-50"
            >
              <XCircle className="h-4 w-4 mr-1" /> Reject
            </button>
          </div>

          {rejectingId === item.recruiterId && (
            <div className="mt-3 flex gap-2">
              <input
                type="text"
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="Reason (optional)"
                className="flex-1 border border-gray-300 rounded-md px-3 py-1.5 text-sm"
              />
              <button onClick={() => handleReject(item.recruiterId)} className="px-3 py-1.5 bg-red-600 text-white rounded-md text-sm">
                Confirm
              </button>
              <button onClick={() => setRejectingId(null)} className="px-3 py-1.5 border border-gray-300 rounded-md text-sm">
                Cancel
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

export default PendingVerificationsPanel;
