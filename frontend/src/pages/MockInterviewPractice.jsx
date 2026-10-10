import React, { useEffect, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { Sparkles, CheckCircle } from 'lucide-react';
import { mockInterviewAPI } from '../services/api';

/** AI mock-interview practice: answer each AI-generated question for a job and get instant
 *  scored feedback before the real interview. */
const MockInterviewPractice = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const currentUser = JSON.parse(localStorage.getItem('user'));

  const [jobTitle, setJobTitle] = useState('');
  const [questions, setQuestions] = useState([]);
  const [drafts, setDrafts] = useState({});
  const [submittingId, setSubmittingId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchSession = async () => {
    try {
      const response = await mockInterviewAPI.get(currentUser.id, sessionId);
      setQuestions(response.data.questions);
      if (response.data.jobTitle) {
        setJobTitle(response.data.jobTitle);
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load this practice session');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSession();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  const handleSubmit = async (questionId) => {
    const answer = drafts[questionId];
    if (!answer || !answer.trim()) {
      setError('Type an answer before submitting');
      return;
    }
    setSubmittingId(questionId);
    setError('');
    try {
      await mockInterviewAPI.answer(currentUser.id, sessionId, questionId, answer.trim());
      await fetchSession();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to score your answer');
    } finally {
      setSubmittingId(null);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="w-12 h-12 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  if (error && questions.length === 0) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="text-center max-w-md">
          <p className="text-red-600 mb-4">{error}</p>
          <button onClick={() => navigate(-1)} className="text-blue-600 hover:text-blue-500 font-medium">Go back</button>
        </div>
      </div>
    );
  }

  const answeredCount = questions.filter((q) => q.answerText).length;

  return (
    <div className="min-h-screen bg-gray-50 py-8 px-4">
      <div className="max-w-3xl mx-auto">
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            <Sparkles className="h-6 w-6 text-purple-600" />
            <h1 className="text-2xl font-bold text-gray-900">Mock Interview: {jobTitle}</h1>
          </div>
          <Link to="/mock-interviews/history" className="text-sm text-purple-600 hover:text-purple-800 font-medium whitespace-nowrap">
            View practice history →
          </Link>
        </div>
        <p className="text-gray-500 mb-6">{answeredCount} of {questions.length} questions answered</p>

        {error && <p className="text-red-600 text-sm mb-4">{error}</p>}

        <div className="space-y-4">
          {questions.map((question) => (
            <div key={question.id} className="bg-white rounded-lg shadow p-5">
              <p className="font-medium text-gray-900 mb-3">Q{question.questionNumber}. {question.questionText}</p>
              <textarea
                value={drafts[question.id] ?? question.answerText ?? ''}
                onChange={(e) => setDrafts((prev) => ({ ...prev, [question.id]: e.target.value }))}
                rows={4}
                placeholder="Type your answer..."
                className="w-full border border-gray-300 rounded-md px-3 py-2"
              />
              <button
                onClick={() => handleSubmit(question.id)}
                disabled={submittingId === question.id}
                className="mt-2 px-4 py-2 bg-purple-600 text-white rounded-md text-sm font-medium hover:bg-purple-700 disabled:opacity-50"
              >
                {submittingId === question.id ? 'Scoring...' : question.answerText ? 'Resubmit Answer' : 'Submit Answer'}
              </button>

              {question.feedback && (
                <div className="mt-4 pt-4 border-t border-gray-200">
                  <p className="text-sm font-medium text-gray-700 mb-1">Score: {question.score}/5</p>
                  <p className="text-sm text-gray-600">{question.feedback}</p>
                </div>
              )}
            </div>
          ))}
        </div>

        {answeredCount === questions.length && questions.length > 0 && (
          <div className="mt-6 bg-green-50 border border-green-200 rounded-lg p-4 flex items-center">
            <CheckCircle className="h-5 w-5 text-green-600 mr-2" />
            <p className="text-green-800 text-sm">You've answered every question. Review the feedback above, or start a new session anytime.</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default MockInterviewPractice;
