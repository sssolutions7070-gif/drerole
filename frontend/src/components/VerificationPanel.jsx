import React, { useEffect, useState } from 'react';
import { BadgeCheck, Clock, XCircle, Upload } from 'lucide-react';
import { recruiterVerificationAPI } from '../services/api';

const STATUS_META = {
  NONE: { label: 'Not submitted', style: 'bg-gray-100 text-gray-700', icon: Upload },
  PENDING: { label: 'Pending review', style: 'bg-yellow-100 text-yellow-800', icon: Clock },
  VERIFIED: { label: 'Verified', style: 'bg-blue-100 text-blue-700', icon: BadgeCheck },
  REJECTED: { label: 'Rejected', style: 'bg-red-100 text-red-700', icon: XCircle },
};

/** Lets the account owner submit proof of their business for the "Verified Employer" badge, and
 *  shows where that submission stands. Owner-only in the backend; a team member can see this tab
 *  but any submit attempt will 403. */
const VerificationPanel = ({ recruiterId }) => {
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [file, setFile] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const fetchStatus = async () => {
    try {
      const response = await recruiterVerificationAPI.getStatus(recruiterId);
      setStatus(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load verification status');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recruiterId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) return;
    setSubmitting(true);
    setError('');
    try {
      const response = await recruiterVerificationAPI.submit(recruiterId, file);
      setStatus(response.data);
      setFile(null);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to submit the document');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <p className="text-gray-500 text-sm">Loading...</p>;

  const meta = STATUS_META[status?.verificationStatus] || STATUS_META.NONE;
  const Icon = meta.icon;

  return (
    <div className="max-w-xl">
      <h3 className="text-lg font-semibold text-gray-800 mb-2">Verified Employer</h3>
      <p className="text-sm text-gray-500 mb-4">
        Upload proof of your business (a registration certificate or company ID) to get a "Verified"
        badge on your job postings and company profile, showing candidates you're a real, reviewed business.
      </p>

      <div className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium mb-4 ${meta.style}`}>
        <Icon className="h-4 w-4" />
        {meta.label}
      </div>

      {status?.verificationStatus === 'REJECTED' && status.verificationRejectionReason && (
        <p className="text-sm text-red-600 mb-4">Reason: {status.verificationRejectionReason}</p>
      )}

      {error && <p className="text-red-600 text-sm mb-4">{error}</p>}

      {(status?.verificationStatus === 'NONE' || status?.verificationStatus === 'REJECTED') && (
        <form onSubmit={handleSubmit} className="flex flex-wrap items-center gap-2">
          <input
            type="file"
            accept=".pdf,.png,.jpg,.jpeg"
            onChange={(e) => setFile(e.target.files?.[0] || null)}
            className="text-sm"
          />
          <button
            type="submit"
            disabled={!file || submitting}
            className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
          >
            <Upload className="h-4 w-4" />
            {submitting ? 'Submitting...' : 'Submit for review'}
          </button>
        </form>
      )}

      {status?.verificationStatus === 'PENDING' && (
        <p className="text-sm text-gray-500">An admin will review your document soon.</p>
      )}
    </div>
  );
};

export default VerificationPanel;
