import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Sparkles, TrendingUp, AlertCircle } from 'lucide-react';
import { mockInterviewAPI } from '../services/api';

const STATUS_LABELS = {
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
};

const ScoreTrend = ({ points }) => {
  const max = Math.max(5, ...points.map((p) => p.averageScore));
  return (
    <div className="flex items-end gap-1.5 h-24">
      {points.map((p) => (
        <div
          key={p.sessionId}
          title={`${p.jobTitle}: ${p.averageScore}/5`}
          className="flex-1 bg-purple-500 rounded-t min-w-[8px]"
          style={{ height: `${Math.max(4, (p.averageScore / max) * 100)}%` }}
        />
      ))}
    </div>
  );
};

/** A candidate's AI mock-interview practice history across every past session - previously each
 *  session's score/feedback was only ever shown once, right after finishing it, with no way to
 *  see whether practice was actually paying off or which questions kept tripping them up. */
const MockInterviewHistory = () => {
  const currentUser = JSON.parse(localStorage.getItem('user'));
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const response = await mockInterviewAPI.getHistory(currentUser.id);
        setHistory(response.data);
      } catch (err) {
        setError(err.response?.data?.error || 'Failed to load your practice history');
      } finally {
        setLoading(false);
      }
    })();
  }, [currentUser.id]);

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="w-12 h-12 border-4 border-purple-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <p className="text-red-600">{error}</p>
      </div>
    );
  }

  if (!history || history.totalSessions === 0) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="text-center max-w-md">
          <Sparkles className="mx-auto h-16 w-16 text-gray-400 mb-4" />
          <h1 className="text-lg font-medium text-gray-900 mb-2">No practice sessions yet</h1>
          <p className="text-gray-500">Start a mock interview from a job listing to begin building your practice history.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8 px-4">
      <div className="max-w-3xl mx-auto">
        <div className="flex items-center gap-2 mb-6">
          <Sparkles className="h-6 w-6 text-purple-600" />
          <h1 className="text-2xl font-bold text-gray-900">My Practice History</h1>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
          <div className="bg-white rounded-lg shadow p-4 text-center">
            <p className="text-2xl font-bold text-gray-900">{history.totalSessions}</p>
            <p className="text-xs text-gray-500 mt-1">Practice sessions</p>
          </div>
          <div className="bg-white rounded-lg shadow p-4 text-center">
            <p className="text-2xl font-bold text-gray-900">{history.totalQuestionsAnswered}</p>
            <p className="text-xs text-gray-500 mt-1">Questions answered</p>
          </div>
          <div className="bg-white rounded-lg shadow p-4 text-center">
            <p className="text-2xl font-bold text-purple-700">
              {history.overallAverageScore != null ? `${history.overallAverageScore} / 5` : '—'}
            </p>
            <p className="text-xs text-gray-500 mt-1">Overall average score</p>
          </div>
        </div>

        {history.scoreTrend.length > 1 && (
          <div className="bg-white rounded-lg shadow p-4 mb-6">
            <p className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-purple-600" /> Score trend over time (oldest → newest)
            </p>
            <ScoreTrend points={history.scoreTrend} />
          </div>
        )}

        {history.questionsToRevisit.length > 0 && (
          <div className="bg-white rounded-lg shadow p-4 mb-6">
            <p className="text-sm font-semibold text-gray-700 mb-1 flex items-center gap-2">
              <AlertCircle className="h-4 w-4 text-amber-500" /> Questions worth revisiting
            </p>
            <p className="text-xs text-gray-500 mb-3">Your lowest-scoring answers across every session.</p>
            <div className="space-y-3">
              {history.questionsToRevisit.map((q, i) => (
                <div key={i} className="border-l-4 border-amber-400 bg-amber-50 rounded-r p-3">
                  <p className="text-sm font-medium text-gray-900">{q.questionText}</p>
                  <p className="text-xs text-gray-500 mb-1">{q.jobTitle} · Score: {q.score}/5</p>
                  {q.feedback && <p className="text-sm text-gray-700">{q.feedback}</p>}
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="bg-white rounded-lg shadow p-4">
          <p className="text-sm font-semibold text-gray-700 mb-3">All sessions</p>
          <div className="space-y-2">
            {history.sessions.map((s) => (
              <Link
                key={s.sessionId}
                to={`/mock-interviews/${s.sessionId}`}
                className="flex items-center justify-between px-3 py-2 rounded-lg border border-gray-200 hover:border-purple-300 hover:bg-purple-50 transition-colors"
              >
                <div>
                  <p className="text-sm font-medium text-gray-900">{s.jobTitle}</p>
                  <p className="text-xs text-gray-500">
                    {STATUS_LABELS[s.status] || s.status} · {s.answeredCount}/{s.questionCount} answered
                  </p>
                </div>
                <span className="text-sm font-semibold text-purple-700">
                  {s.averageScore != null ? `${s.averageScore}/5` : '—'}
                </span>
              </Link>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default MockInterviewHistory;
