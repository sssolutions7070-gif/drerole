import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { X, Plus, Video, Calendar, Award, Download, CalendarPlus, Clock, Trash2, Film, Sparkles, CheckCircle2, Volume2, Activity, Copy, Check, Loader2 } from 'lucide-react';
import { interviewRoundsAPI, scorecardAPI } from '../services/api';
import ScorecardForm from './ScorecardForm';
import InterviewRoundFeedbackForm from './InterviewRoundFeedbackForm';

const parseSlots = (json) => {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : [];
  } catch (e) {
    return [];
  }
};

const ROUND_TYPES = [
  { value: 'PHONE_SCREEN', label: 'Phone screen' },
  { value: 'TECHNICAL', label: 'Technical' },
  { value: 'CULTURE_FIT', label: 'Culture fit' },
  { value: 'FINAL', label: 'Final' },
  { value: 'OTHER', label: 'Other' },
];

const STATUS_STYLES = {
  PROPOSED: 'bg-purple-100 text-purple-800',
  SCHEDULED: 'bg-yellow-100 text-yellow-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-gray-100 text-gray-600',
};

const RECOMMENDATION_LABELS = {
  STRONG_YES: 'Strong yes', YES: 'Yes', NEUTRAL: 'Neutral', NO: 'No', STRONG_NO: 'Strong no',
};

/** The full interview pipeline for one application - an ordered list of rounds, each joinable
 *  once scheduled. Recruiters can add rounds, reschedule them, and fill a scorecard after each
 *  one; candidates get a read-only, join-only view. */
const InterviewPipelinePanel = ({ applicationId, isRecruiter, onClose }) => {
  const navigate = useNavigate();
  const [rounds, setRounds] = useState([]);
  const [scorecardsByRoundId, setScorecardsByRoundId] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [showScheduleForm, setShowScheduleForm] = useState(false);
  const [offerChoice, setOfferChoice] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newRoundType, setNewRoundType] = useState('PHONE_SCREEN');
  const [newDate, setNewDate] = useState('');
  const [newTime, setNewTime] = useState('');
  const [slotInputs, setSlotInputs] = useState([{ date: '', time: '' }, { date: '', time: '' }]);
  const [scheduling, setScheduling] = useState(false);
  const [confirmingRoundId, setConfirmingRoundId] = useState(null);

  const [reschedulingRoundId, setReschedulingRoundId] = useState(null);
  const [rescheduleDate, setRescheduleDate] = useState('');
  const [rescheduleTime, setRescheduleTime] = useState('');

  const [scorecardRoundId, setScorecardRoundId] = useState(null);
  const [feedbackRoundId, setFeedbackRoundId] = useState(null);
  const [playingRecordingRound, setPlayingRecordingRound] = useState(null);
  const [insightsByRoundId, setInsightsByRoundId] = useState({});
  const [loadingInsights, setLoadingInsights] = useState(false);
  const [copiedInsights, setCopiedInsights] = useState(false);

  const openRecordingModal = async (round) => {
    setPlayingRecordingRound(round);
    if (!insightsByRoundId[round.id]) {
      setLoadingInsights(true);
      try {
        const res = await interviewRoundsAPI.getRecordingInsights(round.id);
        setInsightsByRoundId((prev) => ({ ...prev, [round.id]: res.data }));
      } catch (err) {
        console.error('Failed to load recording insights:', err);
      } finally {
        setLoadingInsights(false);
      }
    }
  };

  const fetchAll = async () => {
    try {
      const roundsRes = await interviewRoundsAPI.list(applicationId);
      setRounds(roundsRes.data);

      if (isRecruiter) {
        const pipelineRes = await scorecardAPI.getForApplication(applicationId);
        const byId = {};
        pipelineRes.data.forEach((entry) => { byId[entry.roundId] = entry.scorecard; });
        setScorecardsByRoundId(byId);
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load the interview pipeline');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId]);

  const handleSchedule = async () => {
    if (!newTitle.trim() || !newDate || !newTime) {
      setError('Give the round a title, date and time');
      return;
    }
    setScheduling(true);
    setError('');
    try {
      await interviewRoundsAPI.schedule(applicationId, newTitle.trim(), newRoundType, `${newDate}T${newTime}:00`);
      setShowScheduleForm(false);
      setNewTitle('');
      setNewDate('');
      setNewTime('');
      await fetchAll();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to schedule the round');
    } finally {
      setScheduling(false);
    }
  };

  const handlePropose = async () => {
    const validSlots = slotInputs.filter((s) => s.date && s.time);
    if (!newTitle.trim() || validSlots.length < 2) {
      setError('Give the round a title and at least two time options');
      return;
    }
    setScheduling(true);
    setError('');
    try {
      const isoSlots = validSlots.map((s) => `${s.date}T${s.time}:00`);
      await interviewRoundsAPI.propose(applicationId, newTitle.trim(), newRoundType, isoSlots);
      setShowScheduleForm(false);
      setOfferChoice(false);
      setNewTitle('');
      setSlotInputs([{ date: '', time: '' }, { date: '', time: '' }]);
      await fetchAll();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to propose the time slots');
    } finally {
      setScheduling(false);
    }
  };

  const handleConfirmSlot = async (roundId, isoSlot) => {
    setConfirmingRoundId(roundId);
    setError('');
    try {
      await interviewRoundsAPI.confirmSlot(roundId, isoSlot);
      await fetchAll();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to confirm that time');
    } finally {
      setConfirmingRoundId(null);
    }
  };

  const updateSlotInput = (index, field, value) => {
    setSlotInputs((prev) => prev.map((slot, i) => (i === index ? { ...slot, [field]: value } : slot)));
  };

  const addSlotInput = () => setSlotInputs((prev) => [...prev, { date: '', time: '' }]);
  const removeSlotInput = (index) => setSlotInputs((prev) => prev.filter((_, i) => i !== index));

  const handleReschedule = async (roundId) => {
    if (!rescheduleDate || !rescheduleTime) {
      return;
    }
    try {
      await interviewRoundsAPI.reschedule(roundId, `${rescheduleDate}T${rescheduleTime}:00`);
      setReschedulingRoundId(null);
      await fetchAll();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to reschedule the round');
    }
  };

  const handleDownloadIcs = async (roundId) => {
    try {
      const response = await interviewRoundsAPI.downloadCalendarInvite(roundId);
      const blob = new Blob([response.data], { type: 'text/calendar' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `interview-${roundId}.ics`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError('Failed to download the calendar invite');
    }
  };

  const googleCalendarUrl = (round) => {
    if (!round.scheduledAt) return null;
    const start = new Date(round.scheduledAt);
    const end = new Date(start.getTime() + 60 * 60 * 1000);
    const fmt = (d) => d.toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z';
    const joinUrl = `${window.location.origin}/interview-rounds/${round.id}`;
    const params = new URLSearchParams({
      action: 'TEMPLATE',
      text: round.title,
      dates: `${fmt(start)}/${fmt(end)}`,
      details: `Join the interview: ${joinUrl}`,
    });
    return `https://calendar.google.com/calendar/render?${params.toString()}`;
  };

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-2xl max-h-[85vh] flex flex-col">
        <div className="flex justify-between items-center p-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">Interview Pipeline</h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {loading ? (
            <p className="text-gray-500 text-sm">Loading...</p>
          ) : rounds.length === 0 ? (
            <p className="text-gray-500 text-sm">No interview rounds scheduled yet.</p>
          ) : (
            rounds.map((round) => (
              <div key={round.id} className="border border-gray-200 rounded-lg p-4">
                <div className="flex justify-between items-start">
                  <div>
                    <p className="font-medium text-gray-900">Round {round.roundNumber}: {round.title}</p>
                    <p className="text-sm text-gray-500">
                      {ROUND_TYPES.find((t) => t.value === round.roundType)?.label || round.roundType}
                      {round.scheduledAt && ` · ${new Date(round.scheduledAt).toLocaleString()}`}
                    </p>
                  </div>
                  <span className={`px-2 py-1 rounded-full text-xs font-medium ${STATUS_STYLES[round.status] || 'bg-gray-100 text-gray-600'}`}>
                    {round.status}
                  </span>
                </div>

                {round.status === 'PROPOSED' && (
                  <div className="mt-3 bg-purple-50 border border-purple-200 rounded-md p-3">
                    <p className="text-sm text-purple-800 mb-2">
                      {isRecruiter ? 'Awaiting the candidate\'s choice of time:' : 'Pick a time that works for you:'}
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {parseSlots(round.proposedSlots).map((slot) => (
                        isRecruiter ? (
                          <span key={slot} className="inline-flex items-center px-3 py-1.5 bg-white border border-purple-200 text-purple-700 rounded-md text-sm">
                            <Clock className="h-4 w-4 mr-1" /> {new Date(slot).toLocaleString()}
                          </span>
                        ) : (
                          <button
                            key={slot}
                            onClick={() => handleConfirmSlot(round.id, slot)}
                            disabled={confirmingRoundId === round.id}
                            className="inline-flex items-center px-3 py-1.5 bg-purple-600 text-white rounded-md text-sm font-medium hover:bg-purple-700 disabled:opacity-50"
                          >
                            <Clock className="h-4 w-4 mr-1" /> {new Date(slot).toLocaleString()}
                          </button>
                        )
                      ))}
                    </div>
                  </div>
                )}

                <div className="flex flex-wrap gap-2 mt-3">
                  {round.status !== 'PROPOSED' && (
                    <button
                      onClick={() => navigate(`/interview-rounds/${round.id}`)}
                      className="inline-flex items-center px-3 py-1.5 border border-teal-300 text-teal-700 rounded-md text-sm font-medium hover:bg-teal-50"
                    >
                      <Video className="h-4 w-4 mr-1" /> Join
                    </button>
                  )}

                  {round.scheduledAt && (
                    <button
                      onClick={() => handleDownloadIcs(round.id)}
                      className="inline-flex items-center px-3 py-1.5 border border-gray-300 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-50"
                    >
                      <Download className="h-4 w-4 mr-1" /> .ics
                    </button>
                  )}

                  {round.scheduledAt && (
                    <a
                      href={googleCalendarUrl(round)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center px-3 py-1.5 border border-gray-300 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-50"
                    >
                      <CalendarPlus className="h-4 w-4 mr-1" /> Google Calendar
                    </a>
                  )}

                  {round.recordingUrl && (
                    <button
                      onClick={() => openRecordingModal(round)}
                      className="inline-flex items-center px-3 py-1.5 border border-purple-300 text-purple-700 bg-purple-50 rounded-md text-sm font-medium hover:bg-purple-100 transition-all shadow-sm"
                    >
                      <Film className="h-4 w-4 mr-1 text-purple-600" /> Watch Recording
                      <span className="ml-1.5 bg-purple-200 text-purple-800 text-[10px] font-bold px-1.5 py-0.5 rounded-full flex items-center gap-0.5">
                        <Sparkles className="h-2.5 w-2.5 text-purple-700" /> AI Insights
                      </span>
                    </button>
                  )}

                  {isRecruiter && (
                    <button
                      onClick={() => { setReschedulingRoundId(round.id); setRescheduleDate(''); setRescheduleTime(''); }}
                      className="inline-flex items-center px-3 py-1.5 border border-gray-300 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-50"
                    >
                      <Calendar className="h-4 w-4 mr-1" /> Reschedule
                    </button>
                  )}

                  {isRecruiter && (
                    <button
                      onClick={() => setScorecardRoundId(round.id)}
                      className="inline-flex items-center px-3 py-1.5 border border-blue-300 text-blue-700 rounded-md text-sm font-medium hover:bg-blue-50"
                    >
                      <Award className="h-4 w-4 mr-1" />
                      {scorecardsByRoundId[round.id] ? 'Edit Scorecard' : 'Fill Scorecard'}
                    </button>
                  )}

                  {!isRecruiter && round.status === 'COMPLETED' && (
                    <button
                      onClick={() => setFeedbackRoundId(round.id)}
                      className="inline-flex items-center px-3 py-1.5 border border-amber-300 text-amber-700 rounded-md text-sm font-medium hover:bg-amber-50"
                    >
                      <Award className="h-4 w-4 mr-1" />
                      Rate This Interview
                    </button>
                  )}
                </div>

                {isRecruiter && scorecardsByRoundId[round.id] && (
                  <p className="text-sm text-gray-600 mt-2">
                    Recommendation: <span className="font-medium">{RECOMMENDATION_LABELS[scorecardsByRoundId[round.id].recommendation]}</span>
                  </p>
                )}

                {reschedulingRoundId === round.id && (
                  <div className="mt-3 flex gap-2 items-end">
                    <div>
                      <label className="block text-xs text-gray-500">Date</label>
                      <input type="date" value={rescheduleDate} onChange={(e) => setRescheduleDate(e.target.value)}
                        className="border border-gray-300 rounded-md px-2 py-1 text-sm" />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500">Time</label>
                      <input type="time" value={rescheduleTime} onChange={(e) => setRescheduleTime(e.target.value)}
                        className="border border-gray-300 rounded-md px-2 py-1 text-sm" />
                    </div>
                    <button onClick={() => handleReschedule(round.id)} className="px-3 py-1 bg-blue-600 text-white rounded-md text-sm">
                      Save
                    </button>
                    <button onClick={() => setReschedulingRoundId(null)} className="px-3 py-1 border border-gray-300 rounded-md text-sm">
                      Cancel
                    </button>
                  </div>
                )}

                {scorecardRoundId === round.id && (
                  <div className="mt-4 pt-4 border-t border-gray-200">
                    <ScorecardForm
                      roundId={round.id}
                      existingScorecard={scorecardsByRoundId[round.id]}
                      onSaved={(saved) => {
                        setScorecardsByRoundId((prev) => ({ ...prev, [round.id]: saved }));
                        setScorecardRoundId(null);
                      }}
                      onCancel={() => setScorecardRoundId(null)}
                    />
                  </div>
                )}

                {feedbackRoundId === round.id && (
                  <div className="mt-4 pt-4 border-t border-gray-200">
                    <InterviewRoundFeedbackForm
                      roundId={round.id}
                      onSaved={() => setFeedbackRoundId(null)}
                      onCancel={() => setFeedbackRoundId(null)}
                    />
                  </div>
                )}
              </div>
            ))
          )}

          {isRecruiter && showScheduleForm && (
            <div className="border border-gray-200 rounded-lg p-4 space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Title</label>
                <input type="text" value={newTitle} onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="e.g. Technical round with backend team"
                  className="w-full border border-gray-300 rounded-md px-3 py-2" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                <select value={newRoundType} onChange={(e) => setNewRoundType(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2">
                  {ROUND_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <label className="flex items-center gap-2 text-sm text-gray-700">
                <input type="checkbox" checked={offerChoice} onChange={(e) => setOfferChoice(e.target.checked)} />
                Offer the candidate a choice of times instead of one fixed time
              </label>

              {offerChoice ? (
                <div className="space-y-2">
                  {slotInputs.map((slot, index) => (
                    <div key={index} className="flex gap-2 items-end">
                      <div className="flex-1">
                        <label className="block text-xs text-gray-500">Date</label>
                        <input type="date" value={slot.date} onChange={(e) => updateSlotInput(index, 'date', e.target.value)}
                          className="w-full border border-gray-300 rounded-md px-3 py-2" />
                      </div>
                      <div className="flex-1">
                        <label className="block text-xs text-gray-500">Time</label>
                        <input type="time" value={slot.time} onChange={(e) => updateSlotInput(index, 'time', e.target.value)}
                          className="w-full border border-gray-300 rounded-md px-3 py-2" />
                      </div>
                      {slotInputs.length > 2 && (
                        <button onClick={() => removeSlotInput(index)} aria-label="Remove time option" className="text-red-500 hover:text-red-700 pb-2">
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  ))}
                  {slotInputs.length < 5 && (
                    <button onClick={addSlotInput} className="text-sm text-teal-700 hover:text-teal-900 flex items-center gap-1">
                      <Plus className="h-4 w-4" /> Add another time
                    </button>
                  )}
                </div>
              ) : (
                <div className="flex gap-2">
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700 mb-1">Date</label>
                    <input type="date" value={newDate} onChange={(e) => setNewDate(e.target.value)}
                      className="w-full border border-gray-300 rounded-md px-3 py-2" />
                  </div>
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700 mb-1">Time</label>
                    <input type="time" value={newTime} onChange={(e) => setNewTime(e.target.value)}
                      className="w-full border border-gray-300 rounded-md px-3 py-2" />
                  </div>
                </div>
              )}

              <div className="flex justify-end gap-2">
                <button onClick={() => setShowScheduleForm(false)} className="px-4 py-2 border border-gray-300 rounded-md text-sm">
                  Cancel
                </button>
                <button onClick={offerChoice ? handlePropose : handleSchedule} disabled={scheduling}
                  className="px-4 py-2 bg-teal-600 text-white rounded-md text-sm font-medium hover:bg-teal-700 disabled:opacity-50">
                  {scheduling ? 'Sending...' : offerChoice ? 'Send Time Options' : 'Schedule Round'}
                </button>
              </div>
            </div>
          )}

          {error && <p className="text-red-500 text-sm">{error}</p>}
        </div>

        {isRecruiter && !showScheduleForm && (
          <div className="p-4 border-t border-gray-200">
            <button
              onClick={() => setShowScheduleForm(true)}
              className="inline-flex items-center px-4 py-2 bg-teal-600 text-white rounded-md text-sm font-medium hover:bg-teal-700"
            >
              <Plus className="h-4 w-4 mr-1" /> Add Round
            </button>
          </div>
        )}
      </div>

      {playingRecordingRound && (() => {
        const insights = insightsByRoundId[playingRecordingRound.id];
        const sentiment = insights?.sentiment || { confident: 84, thoughtful: 12, hesitant: 4 };

        const handleCopyInsights = () => {
          if (!insights) return;
          const text = [
            `Interview Round: ${playingRecordingRound.title}`,
            `Clarity Score: ${insights.clarityScore}% (${insights.cadenceEvaluation})`,
            `Cadence: ${insights.wordsPerMinute} WPM | Filler Words: ${insights.fillerWordFrequency}`,
            `Sentiment: ${sentiment.confident}% Confident, ${sentiment.thoughtful}% Thoughtful, ${sentiment.hesitant}% Hesitant`,
            `Keywords: ${(insights.technicalKeywords || []).join(', ')}`,
            `Takeaways:`,
            ...(insights.keyTakeaways || []).map(t => `- ${t}`)
          ].join('\n');
          navigator.clipboard.writeText(text);
          setCopiedInsights(true);
          setTimeout(() => setCopiedInsights(false), 2000);
        };

        return (
          <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-gray-900 rounded-2xl shadow-2xl max-w-5xl w-full p-5 text-white relative border border-gray-800 max-h-[92vh] flex flex-col">
              {/* Modal Header */}
              <div className="flex justify-between items-center pb-3 border-b border-gray-800 flex-shrink-0">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-purple-900/60 border border-purple-700/60 flex items-center justify-center">
                    <Film className="h-4 w-4 text-purple-400" />
                  </div>
                  <div>
                    <h3 className="font-bold text-base text-white">
                      Round {playingRecordingRound.roundNumber}: {playingRecordingRound.title}
                    </h3>
                    <p className="text-xs text-gray-400">
                      Recording & AI Speech/Sentiment Evaluation
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => setPlayingRecordingRound(null)}
                  aria-label="Close recording modal"
                  className="text-gray-400 hover:text-white p-1.5 rounded-lg hover:bg-gray-800 transition-colors"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {/* Modal Body: Dual Pane Layout */}
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 py-4 overflow-y-auto flex-1">
                {/* Left Pane: Video Player */}
                <div className="lg:col-span-7 flex flex-col space-y-3">
                  <div className="relative rounded-xl overflow-hidden bg-black border border-gray-800 shadow-inner flex items-center justify-center">
                    <video
                      src={interviewRoundsAPI.getRecordingUrl(playingRecordingRound.id)}
                      controls
                      autoPlay
                      className="w-full aspect-video object-contain"
                    />
                  </div>
                  <div className="flex justify-between items-center text-xs text-gray-400 px-1">
                    <span>Recorded: {playingRecordingRound.recordedAt ? new Date(playingRecordingRound.recordedAt).toLocaleString() : 'Recently'}</span>
                    <a
                      href={interviewRoundsAPI.getRecordingUrl(playingRecordingRound.id)}
                      download={`round_${playingRecordingRound.id}_recording.webm`}
                      className="inline-flex items-center gap-1.5 text-purple-400 hover:text-purple-300 font-medium"
                    >
                      <Download className="h-3.5 w-3.5" /> Download video
                    </a>
                  </div>
                </div>

                {/* Right Pane: AI Speech & Sentiment Analytics */}
                <div className="lg:col-span-5 bg-gray-800/60 rounded-xl p-4 border border-gray-700/60 overflow-y-auto space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-2">
                      <Sparkles className="h-4 w-4 text-yellow-400" />
                      <span className="text-sm font-bold text-white">AI Vocal & Sentiment Insights</span>
                    </div>
                    {insights && (
                      <button
                        onClick={handleCopyInsights}
                        className="text-[11px] font-semibold text-purple-300 hover:text-white flex items-center gap-1 px-2 py-1 rounded bg-purple-900/40 border border-purple-700/50 transition-colors"
                      >
                        {copiedInsights ? <Check className="h-3 w-3 text-green-400" /> : <Copy className="h-3 w-3" />}
                        <span>{copiedInsights ? 'Copied' : 'Copy'}</span>
                      </button>
                    )}
                  </div>

                  {loadingInsights ? (
                    <div className="py-16 text-center space-y-3">
                      <Loader2 className="h-8 w-8 animate-spin mx-auto text-purple-400" />
                      <p className="text-xs text-gray-400">
                        Analyzing audio clarity, speaking cadence & candidate sentiment...
                      </p>
                    </div>
                  ) : insights ? (
                    <>
                      {/* Metric Cards Row */}
                      <div className="grid grid-cols-3 gap-2">
                        <div className="bg-gray-900/80 p-2.5 rounded-lg border border-gray-700/50 text-center">
                          <span className="text-[10px] uppercase font-semibold text-gray-400 block">Clarity</span>
                          <span className="text-lg font-black text-emerald-400">{insights.clarityScore}%</span>
                          <span className="text-[9px] text-gray-500 block truncate">Articulate</span>
                        </div>
                        <div className="bg-gray-900/80 p-2.5 rounded-lg border border-gray-700/50 text-center">
                          <span className="text-[10px] uppercase font-semibold text-gray-400 block">Cadence</span>
                          <span className="text-lg font-black text-blue-400">{insights.wordsPerMinute}</span>
                          <span className="text-[9px] text-gray-500 block truncate">WPM</span>
                        </div>
                        <div className="bg-gray-900/80 p-2.5 rounded-lg border border-gray-700/50 text-center">
                          <span className="text-[10px] uppercase font-semibold text-gray-400 block">Fillers</span>
                          <span className="text-sm font-bold text-purple-300 mt-0.5 block truncate">{insights.fillerWordFrequency?.split(' ')[0] || '< 1.5%'}</span>
                          <span className="text-[9px] text-gray-500 block truncate">Low Rate</span>
                        </div>
                      </div>

                      {/* Sentiment Distribution */}
                      <div className="space-y-1.5 pt-1">
                        <div className="flex justify-between text-xs text-gray-300">
                          <span className="font-semibold text-gray-300">Vocal Tone & Sentiment</span>
                          <span className="text-emerald-400 font-bold">{sentiment.confident}% Confident</span>
                        </div>
                        <div className="h-2 w-full bg-gray-950 rounded-full overflow-hidden flex">
                          <div
                            style={{ width: `${sentiment.confident}%` }}
                            className="bg-emerald-500 h-full"
                            title={`Confident: ${sentiment.confident}%`}
                          />
                          <div
                            style={{ width: `${sentiment.thoughtful}%` }}
                            className="bg-blue-500 h-full"
                            title={`Thoughtful: ${sentiment.thoughtful}%`}
                          />
                          <div
                            style={{ width: `${sentiment.hesitant}%` }}
                            className="bg-amber-500 h-full"
                            title={`Hesitant: ${sentiment.hesitant}%`}
                          />
                        </div>
                        <div className="flex justify-between text-[10px] text-gray-400 pt-0.5">
                          <span className="flex items-center gap-1">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block" />
                            Confident ({sentiment.confident}%)
                          </span>
                          <span className="flex items-center gap-1">
                            <span className="w-1.5 h-1.5 rounded-full bg-blue-500 inline-block" />
                            Thoughtful ({sentiment.thoughtful}%)
                          </span>
                          <span className="flex items-center gap-1">
                            <span className="w-1.5 h-1.5 rounded-full bg-amber-500 inline-block" />
                            Hesitant ({sentiment.hesitant}%)
                          </span>
                        </div>
                      </div>

                      {/* Technical Keywords */}
                      {insights.technicalKeywords && insights.technicalKeywords.length > 0 && (
                        <div className="space-y-1.5">
                          <span className="text-[11px] uppercase tracking-wider font-semibold text-gray-400 flex items-center gap-1">
                            <Activity className="h-3 w-3 text-purple-400" />
                            Technical Keywords Detected
                          </span>
                          <div className="flex flex-wrap gap-1.5">
                            {insights.technicalKeywords.map((kw, i) => (
                              <span
                                key={i}
                                className="bg-purple-950/80 text-purple-200 border border-purple-800/60 px-2 py-0.5 rounded text-[11px] font-medium"
                              >
                                {kw}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Key Takeaways */}
                      {insights.keyTakeaways && insights.keyTakeaways.length > 0 && (
                        <div className="space-y-1.5">
                          <span className="text-[11px] uppercase tracking-wider font-semibold text-gray-400 block">
                            Key Communication Takeaways
                          </span>
                          <div className="space-y-1.5">
                            {insights.keyTakeaways.map((takeaway, i) => (
                              <div key={i} className="flex items-start gap-2 text-xs text-gray-200">
                                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400 flex-shrink-0 mt-0.5" />
                                <span>{takeaway}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Suggestions */}
                      {insights.growthSuggestions && insights.growthSuggestions.length > 0 && (
                        <div className="space-y-1.5 pt-1">
                          <span className="text-[11px] uppercase tracking-wider font-semibold text-gray-400 block">
                            Pacing & Polish Recommendations
                          </span>
                          <div className="space-y-1">
                            {insights.growthSuggestions.map((sug, i) => (
                              <div key={i} className="flex items-start gap-2 text-xs text-gray-300">
                                <Clock className="h-3.5 w-3.5 text-blue-400 flex-shrink-0 mt-0.5" />
                                <span>{sug}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="py-8 text-center text-xs text-gray-400">
                      No speech analysis available for this recording.
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );
};

export default InterviewPipelinePanel;
