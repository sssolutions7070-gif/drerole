import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Mic, MicOff, Video, VideoOff, PhoneOff, Users, Code } from 'lucide-react';
import { interviewRoundsAPI } from '../services/api';
import { WS_ORIGIN } from '../config';

const LANGUAGES = ['JavaScript', 'Python', 'Java', 'C++', 'Other'];

// The server only ever relays two kinds of messages it builds itself - {"type":"peer-joined"}
// and {"type":"peer-left"} - everything else (SDP offers/answers, ICE candidates, and this page's
// own code-editor sync messages) is opaque JSON this page both writes and reads. Whichever side
// receives "peer-joined" is, by construction, the one who was already waiting in the room, so
// that side is the one that creates the WebRTC offer, sidestepping any offer/answer race without
// needing a fixed caller role.
const InterviewRoundRoom = () => {
  const { roundId } = useParams();
  const navigate = useNavigate();

  const [joinInfo, setJoinInfo] = useState(null);
  const [error, setError] = useState('');
  const [phase, setPhase] = useState('loading'); // loading | lobby | connecting | in-call | ended
  const [micOn, setMicOn] = useState(true);
  const [cameraOn, setCameraOn] = useState(true);
  const [peerPresent, setPeerPresent] = useState(false);
  const [showEditor, setShowEditor] = useState(false);
  const [code, setCode] = useState('');
  const [language, setLanguage] = useState('JavaScript');

  const localVideoRef = useRef(null);
  const remoteVideoRef = useRef(null);
  const localStreamRef = useRef(null);
  const pcRef = useRef(null);
  const wsRef = useRef(null);
  const codeChangeTimeoutRef = useRef(null);

  useEffect(() => {
    interviewRoundsAPI.getJoinInfo(roundId)
      .then((res) => {
        setJoinInfo(res.data);
        setShowEditor(res.data.roundType === 'TECHNICAL');
        setPhase('lobby');
      })
      .catch((err) => {
        setError(err.response?.data?.error || 'This interview round could not be loaded.');
        setPhase('error');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roundId]);

  const teardown = () => {
    wsRef.current?.close();
    pcRef.current?.close();
    localStreamRef.current?.getTracks().forEach((track) => track.stop());
    wsRef.current = null;
    pcRef.current = null;
    localStreamRef.current = null;
  };

  useEffect(() => () => teardown(), []);

  const sendSignal = (message) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(message));
    }
  };

  const createPeerConnection = () => {
    const pc = new RTCPeerConnection({ iceServers: joinInfo.iceServers });

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignal({ type: 'ice-candidate', candidate: event.candidate.toJSON() });
      }
    };

    pc.ontrack = (event) => {
      if (remoteVideoRef.current) {
        remoteVideoRef.current.srcObject = event.streams[0];
        remoteVideoRef.current.play().catch(() => {});
      }
      setPeerPresent(true);
    };

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'connected') {
        setPhase('in-call');
      }
    };

    localStreamRef.current.getTracks().forEach((track) => pc.addTrack(track, localStreamRef.current));
    pcRef.current = pc;
    return pc;
  };

  const handleSignal = async (message) => {
    const pc = pcRef.current;

    switch (message.type) {
      case 'peer-joined': {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        sendSignal({ type: 'offer', description: { type: offer.type, sdp: offer.sdp } });
        break;
      }
      case 'offer': {
        await pc.setRemoteDescription(new RTCSessionDescription(message.description));
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        sendSignal({ type: 'answer', description: { type: answer.type, sdp: answer.sdp } });
        break;
      }
      case 'answer':
        await pc.setRemoteDescription(new RTCSessionDescription(message.description));
        break;
      case 'ice-candidate':
        try {
          await pc.addIceCandidate(new RTCIceCandidate(message.candidate));
        } catch (e) {
          // A candidate that arrives before the remote description is set is harmless to drop.
        }
        break;
      case 'code-update':
        setCode(message.code);
        setLanguage(message.language);
        break;
      case 'peer-left':
        setPeerPresent(false);
        if (remoteVideoRef.current) {
          remoteVideoRef.current.srcObject = null;
        }
        break;
      default:
        break;
    }
  };

  const joinCall = async () => {
    setPhase('connecting');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      localStreamRef.current = stream;
      if (localVideoRef.current) {
        localVideoRef.current.srcObject = stream;
      }
    } catch (e) {
      setError('Could not access your camera/microphone. Check your browser permissions and try again.');
      setPhase('lobby');
      return;
    }

    createPeerConnection();

    const token = localStorage.getItem('token');
    const ws = new WebSocket(`${WS_ORIGIN}/ws/interview-round?token=${encodeURIComponent(token)}&roomId=${encodeURIComponent(joinInfo.roomId)}`);
    wsRef.current = ws;

    ws.onmessage = (event) => {
      try {
        handleSignal(JSON.parse(event.data));
      } catch (e) {
        // Ignore malformed signaling payloads rather than crashing the call.
      }
    };
    ws.onclose = () => {
      setPhase((current) => (current === 'ended' ? current : 'ended'));
    };

    interviewRoundsAPI.updateStatus(roundId, 'IN_PROGRESS').catch(() => {});
    setPhase('in-call');
  };

  const endCall = () => {
    teardown();
    interviewRoundsAPI.updateStatus(roundId, 'COMPLETED').catch(() => {});
    setPhase('ended');
  };

  const toggleMic = () => {
    const track = localStreamRef.current?.getAudioTracks()[0];
    if (track) {
      track.enabled = !track.enabled;
      setMicOn(track.enabled);
    }
  };

  const toggleCamera = () => {
    const track = localStreamRef.current?.getVideoTracks()[0];
    if (track) {
      track.enabled = !track.enabled;
      setCameraOn(track.enabled);
    }
  };

  const handleCodeChange = (newCode) => {
    setCode(newCode);
    clearTimeout(codeChangeTimeoutRef.current);
    codeChangeTimeoutRef.current = setTimeout(() => {
      sendSignal({ type: 'code-update', code: newCode, language });
    }, 250);
  };

  const handleLanguageChange = (newLanguage) => {
    setLanguage(newLanguage);
    sendSignal({ type: 'code-update', code, language: newLanguage });
  };

  if (phase === 'loading') {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="w-12 h-12 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  if (phase === 'error') {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="text-center max-w-md">
          <p className="text-red-600 mb-4">{error}</p>
          <button onClick={() => navigate(-1)} className="text-blue-600 hover:text-blue-500 font-medium">
            Go back
          </button>
        </div>
      </div>
    );
  }

  if (phase === 'ended') {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center p-4">
        <div className="text-center text-white">
          <p className="text-xl mb-4">Call ended</p>
          <button onClick={() => navigate(-1)} className="px-4 py-2 bg-blue-600 rounded-md hover:bg-blue-700">
            Back to applications
          </button>
        </div>
      </div>
    );
  }

  if (phase === 'lobby') {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-lg p-8 max-w-md w-full text-center">
          <Users className="h-10 w-10 text-blue-600 mx-auto mb-3" />
          <h2 className="text-xl font-semibold text-gray-900 mb-1">{joinInfo.title}</h2>
          <p className="text-sm text-gray-500 mb-1">with {joinInfo.counterpartName}</p>
          {joinInfo.scheduledAt && (
            <p className="text-sm text-gray-500 mb-4">
              Scheduled for {new Date(joinInfo.scheduledAt).toLocaleString()}
            </p>
          )}
          {error && <p className="text-red-600 text-sm mb-4">{error}</p>}
          <button
            onClick={joinCall}
            className="w-full px-4 py-2 bg-blue-600 text-white rounded-md font-medium hover:bg-blue-700"
          >
            Join call
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 flex flex-col">
      <div className="flex-1 flex overflow-hidden">
        <div className="flex-1 relative">
          <video ref={remoteVideoRef} autoPlay playsInline className="w-full h-full object-cover bg-black" />
          {!peerPresent && (
            <div className="absolute inset-0 flex items-center justify-center text-gray-400">
              Waiting for {joinInfo.counterpartName} to join...
            </div>
          )}
          <video
            ref={localVideoRef}
            autoPlay
            playsInline
            muted
            className="absolute bottom-4 right-4 w-40 h-28 object-cover rounded-md border-2 border-gray-700 bg-black"
          />
        </div>

        {showEditor && (
          <div className="w-1/2 bg-gray-950 flex flex-col border-l border-gray-700">
            <div className="flex justify-between items-center px-3 py-2 border-b border-gray-700">
              <span className="text-gray-300 text-sm font-medium">Shared code editor</span>
              <select
                value={language}
                onChange={(e) => handleLanguageChange(e.target.value)}
                className="bg-gray-800 text-gray-200 text-sm rounded px-2 py-1 border border-gray-700"
              >
                {LANGUAGES.map((lang) => <option key={lang} value={lang}>{lang}</option>)}
              </select>
            </div>
            <textarea
              value={code}
              onChange={(e) => handleCodeChange(e.target.value)}
              spellCheck={false}
              className="flex-1 bg-gray-950 text-green-300 font-mono text-sm p-3 resize-none outline-none"
              placeholder="// Shared with the other participant in real time"
            />
          </div>
        )}
      </div>

      <div className="flex justify-center items-center gap-4 py-4 bg-gray-800">
        <button
          onClick={toggleMic}
          aria-label={micOn ? 'Mute microphone' : 'Unmute microphone'}
          className={`p-3 rounded-full ${micOn ? 'bg-gray-700 text-white' : 'bg-red-600 text-white'}`}
        >
          {micOn ? <Mic className="h-5 w-5" /> : <MicOff className="h-5 w-5" />}
        </button>
        <button
          onClick={toggleCamera}
          aria-label={cameraOn ? 'Turn camera off' : 'Turn camera on'}
          className={`p-3 rounded-full ${cameraOn ? 'bg-gray-700 text-white' : 'bg-red-600 text-white'}`}
        >
          {cameraOn ? <Video className="h-5 w-5" /> : <VideoOff className="h-5 w-5" />}
        </button>
        <button
          onClick={() => setShowEditor((prev) => !prev)}
          aria-label={showEditor ? 'Hide code editor' : 'Show code editor'}
          className={`p-3 rounded-full ${showEditor ? 'bg-blue-600 text-white' : 'bg-gray-700 text-white'}`}
        >
          <Code className="h-5 w-5" />
        </button>
        <button onClick={endCall} aria-label="End call" className="p-3 rounded-full bg-red-600 text-white">
          <PhoneOff className="h-5 w-5" />
        </button>
      </div>
    </div>
  );
};

export default InterviewRoundRoom;
