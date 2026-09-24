import React, { useEffect, useState } from 'react';
import { X, FileCheck, CheckCircle, XCircle } from 'lucide-react';
import { offerAPI } from '../services/api';

/** The offer for one application - recruiter sends/withdraws it, candidate accepts (with a
 *  typed-name click-to-sign) or declines. The terminal step of the hiring pipeline. */
const OfferPanel = ({ applicationId, isRecruiter, onClose }) => {
  const [offer, setOffer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const [title, setTitle] = useState('');
  const [salaryOffered, setSalaryOffered] = useState('');
  const [startDate, setStartDate] = useState('');
  const [additionalTerms, setAdditionalTerms] = useState('');

  const [signatureName, setSignatureName] = useState('');
  const [declineReason, setDeclineReason] = useState('');
  const [showDeclineForm, setShowDeclineForm] = useState(false);

  const fetchOffer = async () => {
    try {
      const response = await offerAPI.get(applicationId);
      setOffer(response.data);
    } catch (err) {
      setOffer(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOffer();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId]);

  const handleSend = async () => {
    if (!title.trim() || !salaryOffered.trim()) {
      setError('Give the offer a title and a salary');
      return;
    }
    setActionLoading(true);
    setError('');
    try {
      await offerAPI.send(applicationId, title.trim(), salaryOffered.trim(), startDate || null, additionalTerms || null);
      await fetchOffer();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to send the offer');
    } finally {
      setActionLoading(false);
    }
  };

  const handleAccept = async () => {
    if (!signatureName.trim()) {
      setError('Type your full name to sign');
      return;
    }
    setActionLoading(true);
    setError('');
    try {
      await offerAPI.accept(applicationId, signatureName.trim());
      await fetchOffer();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to accept the offer');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDecline = async () => {
    setActionLoading(true);
    setError('');
    try {
      await offerAPI.decline(applicationId, declineReason || null);
      await fetchOffer();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to decline the offer');
    } finally {
      setActionLoading(false);
    }
  };

  const handleWithdraw = async () => {
    setActionLoading(true);
    setError('');
    try {
      await offerAPI.withdraw(applicationId);
      await fetchOffer();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to withdraw the offer');
    } finally {
      setActionLoading(false);
    }
  };

  const renderRecruiterForm = () => (
    <div className="space-y-3">
      <div>
        <label htmlFor="offer-title" className="block text-sm font-medium text-gray-700 mb-1">Position title</label>
        <input id="offer-title" type="text" value={title} onChange={(e) => setTitle(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2" />
      </div>
      <div>
        <label htmlFor="offer-salary" className="block text-sm font-medium text-gray-700 mb-1">Salary</label>
        <input id="offer-salary" type="text" value={salaryOffered} onChange={(e) => setSalaryOffered(e.target.value)}
          placeholder="e.g. $120,000/year" className="w-full border border-gray-300 rounded-md px-3 py-2" />
      </div>
      <div>
        <label htmlFor="offer-start-date" className="block text-sm font-medium text-gray-700 mb-1">Start date</label>
        <input id="offer-start-date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2" />
      </div>
      <div>
        <label htmlFor="offer-terms" className="block text-sm font-medium text-gray-700 mb-1">Additional terms</label>
        <textarea id="offer-terms" value={additionalTerms} onChange={(e) => setAdditionalTerms(e.target.value)} rows={3}
          placeholder="Equity, benefits, relocation, ..." className="w-full border border-gray-300 rounded-md px-3 py-2" />
      </div>
      {error && <p className="text-red-500 text-sm">{error}</p>}
      <button onClick={handleSend} disabled={actionLoading}
        className="w-full px-4 py-2 bg-green-600 text-white rounded-md font-medium hover:bg-green-700 disabled:opacity-50">
        {actionLoading ? 'Sending...' : 'Send Offer'}
      </button>
    </div>
  );

  const renderOfferDetails = () => (
    <div className="space-y-2">
      <p className="font-medium text-gray-900">{offer.title}</p>
      <p className="text-sm text-gray-700">Salary: {offer.salaryOffered}</p>
      {offer.startDate && <p className="text-sm text-gray-700">Start date: {offer.startDate}</p>}
      {offer.additionalTerms && <p className="text-sm text-gray-600">{offer.additionalTerms}</p>}
    </div>
  );

  const renderRecruiterView = () => (
    <div className="space-y-4">
      {renderOfferDetails()}
      <p className="text-sm">
        Status: <span className="font-medium">{offer.status}</span>
      </p>
      {offer.status === 'ACCEPTED' && (
        <p className="text-sm text-green-700 flex items-center">
          <CheckCircle className="h-4 w-4 mr-1" /> Signed by {offer.candidateSignatureName}
        </p>
      )}
      {offer.status === 'DECLINED' && offer.declineReason && (
        <p className="text-sm text-red-600">Decline reason: {offer.declineReason}</p>
      )}
      {error && <p className="text-red-500 text-sm">{error}</p>}
      {offer.status === 'SENT' && (
        <button onClick={handleWithdraw} disabled={actionLoading}
          className="px-4 py-2 border border-red-300 text-red-700 rounded-md text-sm font-medium hover:bg-red-50 disabled:opacity-50">
          {actionLoading ? 'Withdrawing...' : 'Withdraw Offer'}
        </button>
      )}
      {(offer.status === 'DECLINED' || offer.status === 'WITHDRAWN') && (
        <div className="pt-4 border-t border-gray-200">
          <p className="text-sm text-gray-600 mb-3">Send a revised offer?</p>
          {renderRecruiterForm()}
        </div>
      )}
    </div>
  );

  const renderCandidateView = () => (
    <div className="space-y-4">
      {renderOfferDetails()}
      {offer.status === 'ACCEPTED' && (
        <p className="text-sm text-green-700 flex items-center">
          <CheckCircle className="h-4 w-4 mr-1" /> You signed this offer as {offer.candidateSignatureName}
        </p>
      )}
      {offer.status === 'DECLINED' && (
        <p className="text-sm text-gray-600 flex items-center"><XCircle className="h-4 w-4 mr-1" /> You declined this offer</p>
      )}
      {offer.status === 'WITHDRAWN' && (
        <p className="text-sm text-gray-600">This offer was withdrawn by the recruiter.</p>
      )}
      {error && <p className="text-red-500 text-sm">{error}</p>}
      {offer.status === 'SENT' && !showDeclineForm && (
        <div className="space-y-2">
          <label className="block text-sm font-medium text-gray-700">Type your full name to sign and accept</label>
          <input type="text" value={signatureName} onChange={(e) => setSignatureName(e.target.value)}
            placeholder="Full legal name" className="w-full border border-gray-300 rounded-md px-3 py-2" />
          <div className="flex gap-2">
            <button onClick={handleAccept} disabled={actionLoading}
              className="flex-1 px-4 py-2 bg-green-600 text-white rounded-md font-medium hover:bg-green-700 disabled:opacity-50">
              {actionLoading ? 'Signing...' : 'Accept & Sign'}
            </button>
            <button onClick={() => setShowDeclineForm(true)}
              className="flex-1 px-4 py-2 border border-red-300 text-red-700 rounded-md font-medium hover:bg-red-50">
              Decline
            </button>
          </div>
        </div>
      )}
      {offer.status === 'SENT' && showDeclineForm && (
        <div className="space-y-2">
          <label className="block text-sm font-medium text-gray-700">Reason (optional)</label>
          <textarea value={declineReason} onChange={(e) => setDeclineReason(e.target.value)} rows={2}
            className="w-full border border-gray-300 rounded-md px-3 py-2" />
          <div className="flex gap-2">
            <button onClick={() => setShowDeclineForm(false)} className="flex-1 px-4 py-2 border border-gray-300 rounded-md text-sm">
              Back
            </button>
            <button onClick={handleDecline} disabled={actionLoading}
              className="flex-1 px-4 py-2 bg-red-600 text-white rounded-md text-sm disabled:opacity-50">
              {actionLoading ? 'Declining...' : 'Confirm Decline'}
            </button>
          </div>
        </div>
      )}
    </div>
  );

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-md max-h-[85vh] overflow-y-auto">
        <div className="flex justify-between items-center p-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900 flex items-center">
            <FileCheck className="h-5 w-5 mr-2 text-green-600" /> Offer
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>

        <div className="p-4">
          {loading ? (
            <p className="text-gray-500 text-sm">Loading...</p>
          ) : !offer ? (
            isRecruiter ? renderRecruiterForm() : <p className="text-gray-500 text-sm">No offer has been sent yet.</p>
          ) : isRecruiter ? renderRecruiterView() : renderCandidateView()}
        </div>
      </div>
    </div>
  );
};

export default OfferPanel;
