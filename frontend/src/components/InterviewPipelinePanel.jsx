import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { X, Plus, Video, Calendar, Award, Download, CalendarPlus, Clock, Trash2 } from 'lucide-react';
import { interviewRoundsAPI, scorecardAPI } from '../services/api';
import ScorecardForm from './ScorecardForm';

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
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
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
    </div>
  );
};

export default InterviewPipelinePanel;
