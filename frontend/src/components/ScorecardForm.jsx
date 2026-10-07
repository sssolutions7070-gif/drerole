import React, { useState } from 'react';
import { scorecardAPI } from '../services/api';

const RECOMMENDATIONS = [
  { value: 'STRONG_YES', label: 'Strong yes' },
  { value: 'YES', label: 'Yes' },
  { value: 'NEUTRAL', label: 'Neutral' },
  { value: 'NO', label: 'No' },
  { value: 'STRONG_NO', label: 'Strong no' },
];

const RatingInput = ({ label, value, onChange }) => (
  <div>
    <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
    <div className="flex gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          onClick={() => onChange(n)}
          className={`w-9 h-9 rounded-md text-sm font-medium border ${
            value === n ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'
          }`}
        >
          {n}
        </button>
      ))}
    </div>
  </div>
);

// A fixed rubric (communication/technical/culture-fit, each 1-5, plus an overall recommendation)
// so a candidate's rounds are comparable, rather than relying on free-form notes alone.
const ScorecardForm = ({ roundId, existingScorecard, onSaved, onCancel }) => {
  const [communicationRating, setCommunicationRating] = useState(existingScorecard?.communicationRating || 0);
  const [technicalRating, setTechnicalRating] = useState(existingScorecard?.technicalRating || 0);
  const [cultureFitRating, setCultureFitRating] = useState(existingScorecard?.cultureFitRating || 0);
  const [recommendation, setRecommendation] = useState(existingScorecard?.recommendation || '');
  const [notes, setNotes] = useState(existingScorecard?.notes || '');
  const [scratchNotes, setScratchNotes] = useState('');
  const [scratchCode, setScratchCode] = useState('');
  const [aiDraftLoading, setAiDraftLoading] = useState(false);
  const [aiSummary, setAiSummary] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleGenerateAiDraft = async () => {
    setAiDraftLoading(true);
    setError('');
    try {
      const res = await scorecardAPI.generateDraft(roundId, {
        notes: scratchNotes || notes,
        codeSnippet: scratchCode,
      });
      const data = res.data;
      if (data) {
        if (data.communicationRating) setCommunicationRating(data.communicationRating);
        if (data.technicalRating) setTechnicalRating(data.technicalRating);
        if (data.cultureFitRating) setCultureFitRating(data.cultureFitRating);
        if (data.recommendation) setRecommendation(data.recommendation);
        if (data.notes) setNotes(data.notes);
        setAiSummary({
          executiveSummary: data.executiveSummary,
          strengths: data.strengths || [],
          areasForImprovement: data.areasForImprovement || [],
        });
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to generate AI scorecard draft');
    } finally {
      setAiDraftLoading(false);
    }
  };

  const handleSubmit = async () => {
    if (!communicationRating || !technicalRating || !cultureFitRating || !recommendation) {
      setError('Rate every category and pick a recommendation');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const response = await scorecardAPI.submit(roundId, {
        communicationRating, technicalRating, cultureFitRating, recommendation, notes,
      });
      onSaved(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save the scorecard');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* AI Scorecard Copilot Box */}
      <div className="bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200 rounded-lg p-3">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <span className="text-base">✨</span>
            <span className="text-sm font-semibold text-blue-900">AI Scorecard Copilot</span>
          </div>
          <button
            type="button"
            onClick={handleGenerateAiDraft}
            disabled={aiDraftLoading}
            className="px-3 py-1 bg-blue-600 text-white text-xs font-medium rounded hover:bg-blue-700 disabled:opacity-50 flex items-center gap-1 shadow-sm"
          >
            {aiDraftLoading ? (
              <>
                <span className="animate-spin text-xs">⏳</span>
                <span>Analyzing Interview...</span>
              </>
            ) : (
              '✨ Auto-Fill with AI'
            )}
          </button>
        </div>
        <p className="text-xs text-blue-700 mb-2">
          Have interview transcript notes or live code? Paste them below and AI will formulate rubric ratings and an executive summary.
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
          <textarea
            value={scratchNotes}
            onChange={(e) => setScratchNotes(e.target.value)}
            rows={2}
            className="w-full border border-blue-200 bg-white rounded p-1.5 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500"
            placeholder="Key talking points, answers, communication notes..."
          />
          <textarea
            value={scratchCode}
            onChange={(e) => setScratchCode(e.target.value)}
            rows={2}
            className="w-full border border-blue-200 bg-white rounded p-1.5 font-mono placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500"
            placeholder="Live code snippets or algorithmic approach..."
          />
        </div>

        {aiSummary && (
          <div className="mt-3 bg-white border border-blue-200 rounded p-2.5 shadow-sm text-xs space-y-1.5">
            <div className="font-semibold text-gray-800 flex items-center gap-1">
              <span>🎯</span>
              <span>AI Evaluation Summary</span>
            </div>
            {aiSummary.executiveSummary && (
              <p className="text-gray-700 italic bg-gray-50 p-2 rounded border border-gray-100">
                "{aiSummary.executiveSummary}"
              </p>
            )}
            {aiSummary.strengths && aiSummary.strengths.length > 0 && (
              <div>
                <span className="font-medium text-green-800 block mb-0.5">Strengths:</span>
                <div className="flex flex-wrap gap-1">
                  {aiSummary.strengths.map((str, i) => (
                    <span key={i} className="px-2 py-0.5 bg-green-50 text-green-700 border border-green-200 rounded text-[11px]">
                      ✓ {str}
                    </span>
                  ))}
                </div>
              </div>
            )}
            {aiSummary.areasForImprovement && aiSummary.areasForImprovement.length > 0 && (
              <div>
                <span className="font-medium text-amber-800 block mb-0.5">Areas to probe:</span>
                <div className="flex flex-wrap gap-1">
                  {aiSummary.areasForImprovement.map((area, i) => (
                    <span key={i} className="px-2 py-0.5 bg-amber-50 text-amber-700 border border-amber-200 rounded text-[11px]">
                      • {area}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      <RatingInput label="Communication" value={communicationRating} onChange={setCommunicationRating} />
      <RatingInput label="Technical skill" value={technicalRating} onChange={setTechnicalRating} />
      <RatingInput label="Culture fit" value={cultureFitRating} onChange={setCultureFitRating} />

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Recommendation</label>
        <select
          value={recommendation}
          onChange={(e) => setRecommendation(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2"
        >
          <option value="">Select...</option>
          {RECOMMENDATIONS.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          className="w-full border border-gray-300 rounded-md px-3 py-2"
          placeholder="Anything else worth remembering about this round..."
        />
      </div>

      {error && <p className="text-red-500 text-xs">{error}</p>}

      <div className="flex justify-end gap-2">
        <button onClick={onCancel} className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50">
          Cancel
        </button>
        <button
          onClick={handleSubmit}
          disabled={saving}
          className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'Save Scorecard'}
        </button>
      </div>
    </div>
  );
};

export default ScorecardForm;
