import React, { useEffect, useRef, useState } from 'react';
import { X, FileCheck, CheckCircle, XCircle, Printer, FileText, Eraser } from 'lucide-react';
import { offerAPI } from '../services/api';

/** The offer for one application - recruiter sends/withdraws it, candidate accepts (with a
 *  typed-name click-to-sign or HTML5 drawn signature) or declines. */
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
  const [showOfferLetter, setShowOfferLetter] = useState(false);
  const [hasDrawnSignature, setHasDrawnSignature] = useState(false);

  const canvasRef = useRef(null);
  const isDrawingRef = useRef(false);

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

  // Canvas drawing handlers
  const startDrawing = (e) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const rect = canvas.getBoundingClientRect();
    const x = (e.clientX || (e.touches && e.touches[0].clientX)) - rect.left;
    const y = (e.clientY || (e.touches && e.touches[0].clientY)) - rect.top;
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    ctx.strokeStyle = '#1e3a8a';
    isDrawingRef.current = true;
    setHasDrawnSignature(true);
  };

  const draw = (e) => {
    if (!isDrawingRef.current) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const rect = canvas.getBoundingClientRect();
    const x = (e.clientX || (e.touches && e.touches[0].clientX)) - rect.left;
    const y = (e.clientY || (e.touches && e.touches[0].clientY)) - rect.top;
    ctx.lineTo(x, y);
    ctx.stroke();
  };

  const stopDrawing = () => {
    isDrawingRef.current = false;
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    setHasDrawnSignature(false);
  };

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
    let signatureDataUrl = null;
    if (hasDrawnSignature && canvasRef.current) {
      try {
        signatureDataUrl = canvasRef.current.toDataURL('image/png');
      } catch (e) {
        // ignore if canvas security block
      }
    }
    setActionLoading(true);
    setError('');
    try {
      if (signatureDataUrl) {
        await offerAPI.accept(applicationId, signatureName.trim(), signatureDataUrl);
      } else {
        await offerAPI.accept(applicationId, signatureName.trim());
      }
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
      <div className="flex items-center justify-between">
        <p className="font-semibold text-gray-900 text-base">{offer.title}</p>
        <button
          type="button"
          onClick={() => setShowOfferLetter(true)}
          className="text-xs text-blue-600 hover:text-blue-800 flex items-center gap-1 font-medium bg-blue-50 px-2 py-1 rounded border border-blue-200"
        >
          <FileText className="h-3.5 w-3.5" /> View Official Letter
        </button>
      </div>
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
        <div className="space-y-2 bg-green-50 border border-green-200 rounded p-3 text-xs">
          <p className="text-green-800 font-semibold flex items-center">
            <CheckCircle className="h-4 w-4 mr-1 text-green-600" /> Signed by {offer.candidateSignatureName}
          </p>
          {offer.signatureDataUrl && (
            <div>
              <p className="text-gray-500 mb-1">E-Signature Verification:</p>
              <img src={offer.signatureDataUrl} alt="Digital Signature" className="h-10 bg-white border border-gray-300 rounded p-1" />
            </div>
          )}
        </div>
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
        <div className="space-y-2 bg-green-50 border border-green-200 rounded p-3 text-xs">
          <p className="text-green-800 font-semibold flex items-center">
            <CheckCircle className="h-4 w-4 mr-1 text-green-600" /> You signed this offer as {offer.candidateSignatureName}
          </p>
          {offer.signatureDataUrl && (
            <div>
              <p className="text-gray-500 mb-1">Your Verified Digital Signature:</p>
              <img src={offer.signatureDataUrl} alt="Digital Signature" className="h-10 bg-white border border-gray-300 rounded p-1" />
            </div>
          )}
        </div>
      )}
      {offer.status === 'DECLINED' && (
        <p className="text-sm text-gray-600 flex items-center"><XCircle className="h-4 w-4 mr-1" /> You declined this offer</p>
      )}
      {offer.status === 'WITHDRAWN' && (
        <p className="text-sm text-gray-600">This offer was withdrawn by the recruiter.</p>
      )}
      {error && <p className="text-red-500 text-sm">{error}</p>}
      {offer.status === 'SENT' && !showDeclineForm && (
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Type your full name to sign and accept</label>
            <input type="text" value={signatureName} onChange={(e) => setSignatureName(e.target.value)}
              placeholder="Full legal name" className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-xs font-medium text-gray-600">Draw Digital Signature (Optional):</label>
              <button
                type="button"
                onClick={clearCanvas}
                className="text-[11px] text-gray-500 hover:text-red-600 flex items-center gap-1"
              >
                <Eraser className="h-3 w-3" /> Clear
              </button>
            </div>
            <canvas
              ref={canvasRef}
              width={340}
              height={70}
              onMouseDown={startDrawing}
              onMouseMove={draw}
              onMouseUp={stopDrawing}
              onMouseLeave={stopDrawing}
              onTouchStart={startDrawing}
              onTouchMove={draw}
              onTouchEnd={stopDrawing}
              className="w-full h-18 bg-gray-50 border border-gray-300 rounded cursor-crosshair touch-none"
            />
          </div>

          <div className="flex gap-2">
            <button onClick={handleAccept} disabled={actionLoading}
              className="flex-1 px-4 py-2 bg-green-600 text-white rounded-md font-medium hover:bg-green-700 disabled:opacity-50 text-sm">
              {actionLoading ? 'Signing...' : 'Accept & Sign'}
            </button>
            <button onClick={() => setShowDeclineForm(true)}
              className="flex-1 px-4 py-2 border border-red-300 text-red-700 rounded-md font-medium hover:bg-red-50 text-sm">
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
    <>
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-lg shadow-lg w-full max-w-md max-h-[85vh] overflow-y-auto">
          <div className="flex justify-between items-center p-4 border-b border-gray-200">
            <h2 className="text-lg font-semibold text-gray-900 flex items-center">
              <FileCheck className="h-5 w-5 mr-2 text-green-600" /> Offer
            </h2>
            <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
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

      {/* Formal Official Offer Letter Modal */}
      {showOfferLetter && offer && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-xl shadow-2xl max-w-2xl w-full p-8 border border-gray-200 text-gray-800 space-y-6 print:m-0 print:p-0 print:border-none">
            <div className="flex justify-between items-start border-b border-gray-200 pb-4">
              <div>
                <h1 className="text-2xl font-serif font-bold text-gray-900">OFFICIAL OFFER OF EMPLOYMENT</h1>
                <p className="text-xs text-gray-500 tracking-wider uppercase mt-1">DreamRole Verified Employment Agreement</p>
              </div>
              <div className="flex items-center gap-2 print:hidden">
                <button
                  type="button"
                  onClick={() => window.print()}
                  className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs font-medium hover:bg-blue-700 flex items-center gap-1.5 shadow-sm"
                >
                  <Printer className="h-4 w-4" /> Print / Save PDF
                </button>
                <button
                  type="button"
                  onClick={() => setShowOfferLetter(false)}
                  aria-label="Close"
                  className="p-1.5 text-gray-400 hover:text-gray-600 rounded"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>
            </div>

            <div className="text-sm space-y-4 leading-relaxed font-sans">
              <p>Dear Candidate,</p>
              <p>
                We are thrilled to extend an official offer of employment for the position of{' '}
                <strong className="text-gray-900">{offer.title}</strong>. We were immensely impressed by your interview
                evaluations and feel your expertise will be a vital addition to the organization.
              </p>

              <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 space-y-2">
                <h3 className="font-semibold text-gray-900 text-sm">Summary of Employment Terms:</h3>
                <ul className="list-disc list-inside space-y-1 text-xs text-gray-700">
                  <li><strong>Role:</strong> {offer.title}</li>
                  <li><strong>Compensation:</strong> {offer.salaryOffered}</li>
                  <li><strong>Start Date:</strong> {offer.startDate || 'To be mutually scheduled'}</li>
                  {offer.additionalTerms && <li><strong>Additional Terms:</strong> {offer.additionalTerms}</li>}
                  <li><strong>Employment Status:</strong> Full-Time Regular</li>
                </ul>
              </div>

              <p className="text-xs text-gray-600">
                This offer is contingent upon successful completion of onboarding verifications. Acceptance of this
                offer is formalized electronically via DreamRole E-Signature.
              </p>
            </div>

            <div className="pt-6 border-t border-gray-200 grid grid-cols-2 gap-8 text-xs">
              <div>
                <p className="font-semibold text-gray-800">Authorized by:</p>
                <div className="h-12 border-b border-gray-400 flex items-end font-serif italic text-gray-700">
                  DreamRole Hiring Representative
                </div>
                <p className="text-gray-500 mt-1">Date: {offer.createdAt ? new Date(offer.createdAt).toLocaleDateString() : 'Active'}</p>
              </div>

              <div>
                <p className="font-semibold text-gray-800">Candidate Acceptance:</p>
                {offer.status === 'ACCEPTED' ? (
                  <>
                    <div className="h-12 border-b border-gray-400 flex items-center">
                      {offer.signatureDataUrl ? (
                        <img src={offer.signatureDataUrl} alt="Signature" className="h-10" />
                      ) : (
                        <span className="font-serif italic text-blue-900 text-sm font-semibold">{offer.candidateSignatureName}</span>
                      )}
                    </div>
                    <p className="text-green-700 font-medium mt-1">
                      Signed & Verified: {offer.signedAt ? new Date(offer.signedAt).toLocaleDateString() : 'Signed'}
                    </p>
                  </>
                ) : (
                  <>
                    <div className="h-12 border-b border-gray-300 border-dashed flex items-center text-gray-400 italic">
                      Awaiting Candidate Signature
                    </div>
                    <p className="text-amber-600 mt-1 font-medium">Pending acceptance</p>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default OfferPanel;
