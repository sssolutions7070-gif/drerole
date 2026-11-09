import React, { useEffect, useState } from 'react';
import { X, HelpCircle } from 'lucide-react';
import { applicationStatusAPI } from '../services/api';

/** AI-generated, constructive explanation of why one specific application was rejected,
 *  grounded in the match data already recorded on it (matchedSkills/missingSkills/match
 *  percentage, plus the recruiter's own note if any) - see ApplicationStatusService.
 *  getRejectionExplanation. Only ever called for a REJECTED application. */
const RejectionExplanationModal = ({ applicationId, onClose }) => {
  const [explanation, setExplanation] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchExplanation = async () => {
      try {
        const response = await applicationStatusAPI.getRejectionExplanation(applicationId);
        setExplanation(response.data.explanation);
      } catch (err) {
        setError(err.response?.data?.error || 'Failed to load an explanation for this rejection');
      } finally {
        setLoading(false);
      }
    };
    fetchExplanation();
  }, [applicationId]);

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-md max-h-[85vh] overflow-y-auto">
        <div className="flex justify-between items-center p-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900 flex items-center">
            <HelpCircle className="h-5 w-5 mr-2 text-blue-600" /> Why was I rejected?
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>

        <div className="p-4">
          {loading ? (
            <p className="text-gray-500 text-sm">Loading...</p>
          ) : error ? (
            <p className="text-red-600 text-sm">{error}</p>
          ) : (
            <p className="text-sm text-gray-700 whitespace-pre-line">{explanation}</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default RejectionExplanationModal;
