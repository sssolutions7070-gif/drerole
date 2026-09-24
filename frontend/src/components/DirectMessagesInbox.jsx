import React, { useEffect, useState } from 'react';
import { MessageCircle } from 'lucide-react';
import { directMessageAPI } from '../services/api';
import DirectMessageThread from './DirectMessageThread';

/** Lists a recruiter's or a candidate's open direct-message threads (outreach outside of any
 *  application - see DirectMessageThread) and opens one on click. */
const DirectMessagesInbox = ({ role, id }) => {
  const [threads, setThreads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [openThread, setOpenThread] = useState(null);

  useEffect(() => {
    (async () => {
      try {
        const response = role === 'recruiter'
          ? await directMessageAPI.listForRecruiter(id)
          : await directMessageAPI.listForCandidate(id);
        setThreads(response.data);
      } catch (err) {
        setError(err.response?.data?.error || 'Failed to load messages');
      } finally {
        setLoading(false);
      }
    })();
  }, [role, id]);

  const formatTime = (isoString) => {
    if (!isoString) return '';
    return new Date(isoString).toLocaleString(undefined, {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  };

  if (loading) return <p className="text-gray-500 text-sm">Loading...</p>;
  if (error) return <p className="text-red-600 text-sm">{error}</p>;

  return (
    <div>
      {threads.length === 0 ? (
        <div className="text-center py-12 bg-white rounded-lg shadow">
          <MessageCircle className="mx-auto h-16 w-16 text-gray-400 mb-4" />
          <p className="text-gray-500">
            {role === 'recruiter' ? 'No conversations yet - message a candidate from Discover Candidates.' : 'No messages yet.'}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {threads.map((thread) => {
            const key = role === 'recruiter' ? thread.candidateUserId : thread.recruiterId;
            const name = role === 'recruiter' ? thread.candidateName : thread.companyName;
            return (
              <button
                key={key}
                onClick={() => setOpenThread(thread)}
                className="w-full text-left bg-white border border-gray-200 rounded-lg p-4 hover:bg-gray-50 flex items-center justify-between"
              >
                <div className="min-w-0">
                  <p className="font-medium text-gray-900">{name}</p>
                  <p className="text-sm text-gray-500 truncate">{thread.lastMessage}</p>
                </div>
                <span className="text-xs text-gray-400 flex-shrink-0 ml-3">{formatTime(thread.lastMessageAt)}</span>
              </button>
            );
          })}
        </div>
      )}

      {openThread && (
        <DirectMessageThread
          recruiterId={role === 'recruiter' ? id : openThread.recruiterId}
          candidateUserId={role === 'recruiter' ? openThread.candidateUserId : id}
          title={role === 'recruiter' ? openThread.candidateName : openThread.companyName}
          onClose={() => setOpenThread(null)}
        />
      )}
    </div>
  );
};

export default DirectMessagesInbox;
