import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Mic, MicOff, Video, VideoOff, PhoneOff, Users, Code, Circle, Download,
  CheckCircle2, Play, Terminal, RotateCcw, Trash2, ChevronDown, ChevronUp, Loader2,
  Sparkles, Copy, Check, RefreshCw, Zap
} from 'lucide-react';
import { interviewRoundsAPI } from '../services/api';
import { WS_ORIGIN } from '../config';
import { executeCode, CODE_STARTERS } from '../utils/codeRunner';

const LANGUAGES = ['JavaScript', 'Python', 'Java', 'C++', 'SQL', 'Other'];

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

  // Live In-Interview Code Runner state
  const [terminalOpen, setTerminalOpen] = useState(true);
  const [isRunningCode, setIsRunningCode] = useState(false);
  const [terminalTab, setTerminalTab] = useState('output'); // 'output' | 'stdin'
  const [customInput, setCustomInput] = useState('');
  const [terminalLogs, setTerminalLogs] = useState([]);
  const [executionStats, setExecutionStats] = useState(null); // { status, durationMs, ranBy }

  // Interview Recording state
  const [isRecording, setIsRecording] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [recordedBlobUrl, setRecordedBlobUrl] = useState(null);
  const [recordingStatus, setRecordingStatus] = useState('idle'); // idle | recording | saving | saved | error
  const [recordingStatusMessage, setRecordingStatusMessage] = useState('');

  // AI Live Interview Copilot state (recruiter-only)
  const [isAnalyzingCopilot, setIsAnalyzingCopilot] = useState(false);
  const [copilotData, setCopilotData] = useState(null);
  const [copilotCopiedIdx, setCopilotCopiedIdx] = useState(null);
  const [copilotError, setCopilotError] = useState('');

  const localVideoRef = useRef(null);
  const remoteVideoRef = useRef(null);
  const localStreamRef = useRef(null);
  const pcRef = useRef(null);
  const wsRef = useRef(null);
  const editorRef = useRef(null);
  const codeChangeTimeoutRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const recordedChunksRef = useRef([]);
  const recordingTimerRef = useRef(null);

  useEffect(() => {
    interviewRoundsAPI.getJoinInfo(roundId)
      .then((res) => {
        setJoinInfo(res.data);
        const isTech = res.data.roundType === 'TECHNICAL';
        setShowEditor(isTech);
        setPhase('lobby');
      })
      .catch((err) => {
        setError(err.response?.data?.error || 'This interview round could not be loaded.');
        setPhase('error');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roundId]);

  const teardown = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      try { mediaRecorderRef.current.stop(); } catch (e) {}
    }
    clearInterval(recordingTimerRef.current);
    wsRef.current?.close();
    pcRef.current?.close();
    localStreamRef.current?.getTracks().forEach((track) => track.stop());
    wsRef.current = null;
    pcRef.current = null;
    localStreamRef.current = null;
  };

  useEffect(() => () => teardown(), []);

  const startRecording = () => {
    if (typeof MediaRecorder === 'undefined') {
      setError('Recording is not supported in this browser.');
      return;
    }

    try {
      recordedChunksRef.current = [];
      const stream = remoteVideoRef.current?.srcObject || localStreamRef.current;
      if (!stream) {
        setError('No active media stream to record.');
        return;
      }

      let options = {};
      if (typeof MediaRecorder.isTypeSupported === 'function') {
        if (MediaRecorder.isTypeSupported('video/webm;codecs=vp9,opus')) {
          options = { mimeType: 'video/webm;codecs=vp9,opus' };
        } else if (MediaRecorder.isTypeSupported('video/webm')) {
          options = { mimeType: 'video/webm' };
        }
      }

      const recorder = new MediaRecorder(stream, options);
      recorder.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) {
          recordedChunksRef.current.push(event.data);
        }
      };

      recorder.onstop = async () => {
        clearInterval(recordingTimerRef.current);
        const blob = new Blob(recordedChunksRef.current, { type: 'video/webm' });
        if (blob.size > 0) {
          const url = URL.createObjectURL(blob);
          setRecordedBlobUrl(url);
          setRecordingStatus('saving');
          try {
            const file = new File([blob], `round_${roundId}.webm`, { type: 'video/webm' });
            await interviewRoundsAPI.uploadRecording(roundId, file);
            setRecordingStatus('saved');
            setRecordingStatusMessage('Interview round recording uploaded & saved!');
          } catch (err) {
            console.error('Failed to upload recording:', err);
            setRecordingStatus('error');
            setRecordingStatusMessage('Recording saved locally, but cloud upload failed.');
          }
        }
      };

      recorder.start(1000);
      mediaRecorderRef.current = recorder;
      setIsRecording(true);
      setRecordingTime(0);
      setRecordingStatus('recording');
      recordingTimerRef.current = setInterval(() => {
        setRecordingTime((t) => t + 1);
      }, 1000);
    } catch (err) {
      console.error('Failed to start recording:', err);
      setError('Failed to start recording: ' + err.message);
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
      setIsRecording(false);
      clearInterval(recordingTimerRef.current);
    }
  };

  const toggleRecording = () => {
    if (isRecording) {
      stopRecording();
    } else {
      startRecording();
    }
  };

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
        sendSignal({ type: 'offer', sdp: offer.sdp });
        break;
      }
      case 'offer': {
        await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: message.sdp }));
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        sendSignal({ type: 'answer', sdp: answer.sdp });
        break;
      }
      case 'answer':
        await pc.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: message.sdp }));
        break;
      case 'ice-candidate':
        await pc.addIceCandidate(new RTCIceCandidate(message.candidate));
        break;
      case 'peer-left':
        setPeerPresent(false);
        if (remoteVideoRef.current) {
          remoteVideoRef.current.srcObject = null;
        }
        break;
      case 'code-update':
        setCode(message.code);
        if (message.language) setLanguage(message.language);
        break;
      case 'code-run-started':
        setIsRunningCode(true);
        setTerminalOpen(true);
        setTerminalLogs([{ type: 'system', text: `▶ Executing code (initiated by ${message.ranBy || 'peer'})...` }]);
        break;
      case 'code-run-result':
        setIsRunningCode(false);
        setTerminalLogs(message.logs || []);
        setExecutionStats({
          status: message.status || 'SUCCESS',
          durationMs: message.durationMs || 0,
          ranBy: message.ranBy || 'Peer'
        });
        setTerminalOpen(true);
        break;
      case 'code-stdin-update':
        setCustomInput(message.input || '');
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

      createPeerConnection();

      const token = localStorage.getItem('token');
      const wsUrl = `${WS_ORIGIN}/ws/interview-round?roomId=${joinInfo.roomId}&token=${token}`;
      const ws = new WebSocket(wsUrl);

      ws.onmessage = (event) => {
        try {
          handleSignal(JSON.parse(event.data));
        } catch (e) {
          console.error('Failed to parse signal message:', e);
        }
      };

      ws.onerror = () => {
        setError('Connection error. Please refresh and try again.');
        setPhase('error');
      };

      ws.onclose = () => {
        if (phase === 'in-call' || phase === 'connecting') {
          setPhase('ended');
        }
      };

      wsRef.current = ws;
      await interviewRoundsAPI.updateStatus(roundId, 'IN_PROGRESS');
      setPhase('in-call');
    } catch (err) {
      console.error('Failed to access media devices:', err);
      setError('Camera/microphone access was denied. Please grant access and try again.');
      setPhase('error');
    }
  };

  const endCall = () => {
    if (isRecording) {
      stopRecording();
    }
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
    const prevStarter = CODE_STARTERS[language];
    let nextCode = code;
    if (!code || !code.trim() || code === prevStarter) {
      nextCode = CODE_STARTERS[newLanguage] || '';
      setCode(nextCode);
    }
    sendSignal({ type: 'code-update', code: nextCode, language: newLanguage });
  };

  const handleKeyDown = (e) => {
    // Run code shortcut: Ctrl+Enter or Cmd+Enter
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      handleRunCode();
      return;
    }
    // Tab key inserts 2 spaces
    if (e.key === 'Tab') {
      e.preventDefault();
      const start = e.target.selectionStart;
      const end = e.target.selectionEnd;
      const newCode = code.substring(0, start) + '  ' + code.substring(end);
      handleCodeChange(newCode);
      setTimeout(() => {
        if (editorRef.current) {
          editorRef.current.selectionStart = editorRef.current.selectionEnd = start + 2;
        }
      }, 0);
    }
  };

  const handleResetCode = () => {
    const starter = CODE_STARTERS[language] || '';
    handleCodeChange(starter);
  };

  const handleCustomInputChange = (e) => {
    const val = e.target.value;
    setCustomInput(val);
    sendSignal({ type: 'code-stdin-update', input: val });
  };

  const handleClearConsole = () => {
    setTerminalLogs([]);
    setExecutionStats(null);
  };

  const fetchCopilotProbes = async () => {
    setIsAnalyzingCopilot(true);
    setCopilotError('');
    try {
      const res = await interviewRoundsAPI.getCopilotProbes(roundId, {
        language,
        code: code || ''
      });
      setCopilotData(res.data);
    } catch (err) {
      console.error('Failed to fetch AI copilot probes:', err);
      setCopilotError(err.response?.data?.error || 'Failed to generate AI copilot probes.');
    } finally {
      setIsAnalyzingCopilot(false);
    }
  };

  const handleCopyProbe = (text, idx) => {
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(text);
    }
    setCopilotCopiedIdx(idx);
    setTimeout(() => setCopilotCopiedIdx(null), 2000);
  };

  const handleRunCode = async () => {
    if (isRunningCode) return;
    setIsRunningCode(true);
    setTerminalOpen(true);
    setTerminalTab('output');
    const runnerRole = joinInfo?.role === 'candidate' ? 'Candidate' : 'Interviewer';

    sendSignal({ type: 'code-run-started', ranBy: runnerRole });

    try {
      const res = await executeCode(code, language, customInput);
      setTerminalLogs(res.output);
      setExecutionStats({
        status: res.status,
        durationMs: res.durationMs,
        ranBy: 'You'
      });
      sendSignal({
        type: 'code-run-result',
        logs: res.output,
        status: res.status,
        durationMs: res.durationMs,
        ranBy: runnerRole
      });
    } catch (err) {
      const errorOutput = [{ type: 'stderr', text: err?.message || 'Execution error' }];
      setTerminalLogs(errorOutput);
      setExecutionStats({
        status: 'ERROR',
        durationMs: 0,
        ranBy: 'You'
      });
      sendSignal({
        type: 'code-run-result',
        logs: errorOutput,
        status: 'ERROR',
        durationMs: 0,
        ranBy: runnerRole
      });
    } finally {
      setIsRunningCode(false);
    }
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
        <div className="text-center text-white max-w-md w-full p-6 bg-gray-800 rounded-lg shadow-xl">
          <p className="text-2xl font-bold mb-2">Call Ended</p>
          <p className="text-sm text-gray-400 mb-6">Thank you for participating in this interview round.</p>

          {recordingStatusMessage && (
            <div className={`mb-4 p-3 rounded-md text-xs flex items-center justify-center gap-2 ${
              recordingStatus === 'saved' ? 'bg-green-900/60 text-green-200 border border-green-700' : 'bg-yellow-900/60 text-yellow-200 border border-yellow-700'
            }`}>
              <CheckCircle2 className="h-4 w-4 flex-shrink-0" />
              <span>{recordingStatusMessage}</span>
            </div>
          )}

          {recordedBlobUrl && (
            <div className="mb-6 space-y-3">
              <video src={recordedBlobUrl} controls className="w-full rounded border border-gray-700 bg-black aspect-video" />
              <a
                href={recordedBlobUrl}
                download={`interview_round_${roundId}.webm`}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-gray-700 text-white text-sm rounded-md hover:bg-gray-600"
              >
                <Download className="h-4 w-4" /> Download Recording
              </a>
            </div>
          )}

          <button onClick={() => navigate(-1)} className="w-full px-4 py-2 bg-blue-600 rounded-md hover:bg-blue-700 font-medium">
            Back to Application
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
          <div className="w-1/2 bg-gray-950 flex flex-col border-l border-gray-700 h-full overflow-hidden">
            {/* Top Toolbar */}
            <div className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 bg-gray-900 border-b border-gray-700 select-none">
              <div className="flex items-center gap-2">
                <span className="text-gray-300 text-xs font-semibold uppercase tracking-wider">Shared Code Editor</span>
                <select
                  value={language}
                  onChange={(e) => handleLanguageChange(e.target.value)}
                  aria-label="Code language"
                  className="bg-gray-800 text-gray-200 text-xs rounded px-2 py-1 border border-gray-700 font-medium focus:ring-1 focus:ring-blue-500"
                >
                  {LANGUAGES.map((lang) => <option key={lang} value={lang}>{lang}</option>)}
                </select>
                <button
                  onClick={handleResetCode}
                  title="Reset to starter template"
                  aria-label="Reset code template"
                  className="p-1 text-gray-400 hover:text-gray-200 hover:bg-gray-800 rounded transition"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                </button>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-gray-500 text-xs hidden md:inline font-mono">Ctrl+Enter to run</span>
                {joinInfo?.role === 'recruiter' && (
                  <button
                    onClick={() => {
                      if (!terminalOpen) setTerminalOpen(true);
                      setTerminalTab('copilot');
                      if (!copilotData && !isAnalyzingCopilot) {
                        fetchCopilotProbes();
                      }
                    }}
                    aria-label="AI Interviewer Copilot"
                    title="AI Live Copilot: Probing questions, edge cases & complexity analysis"
                    className={`flex items-center gap-1.5 px-2.5 py-1 text-xs rounded font-semibold transition ${
                      terminalOpen && terminalTab === 'copilot'
                        ? 'bg-purple-600 text-white shadow ring-1 ring-purple-400'
                        : 'bg-purple-950/80 text-purple-300 hover:bg-purple-900 border border-purple-800'
                    }`}
                  >
                    <Sparkles className="h-3.5 w-3.5 text-purple-300" />
                    <span>AI Copilot</span>
                    {isAnalyzingCopilot && <Loader2 className="h-3 w-3 animate-spin ml-0.5" />}
                  </button>
                )}

                <button
                  onClick={() => setTerminalOpen((prev) => !prev)}
                  aria-label={terminalOpen ? 'Hide terminal' : 'Show terminal'}
                  title={terminalOpen ? 'Collapse terminal' : 'Open terminal'}
                  className={`flex items-center gap-1 px-2 py-1 text-xs rounded font-medium transition ${
                    terminalOpen && terminalTab !== 'copilot' ? 'bg-gray-800 text-gray-200' : 'bg-gray-800/60 text-gray-400 hover:text-gray-200'
                  }`}
                >
                  <Terminal className="h-3.5 w-3.5" />
                  <span>Terminal</span>
                </button>

                <button
                  onClick={handleRunCode}
                  disabled={isRunningCode}
                  aria-label="Run code"
                  className="flex items-center gap-1.5 px-3 py-1 bg-green-600 hover:bg-green-500 disabled:bg-gray-700 text-white text-xs font-semibold rounded shadow transition"
                >
                  {isRunningCode ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span>Running...</span>
                    </>
                  ) : (
                    <>
                      <Play className="h-3.5 w-3.5 fill-current" />
                      <span>Run Code</span>
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* Code Textarea */}
            <div className="flex-1 flex flex-col min-h-0 relative">
              <textarea
                ref={editorRef}
                value={code}
                onChange={(e) => handleCodeChange(e.target.value)}
                onKeyDown={handleKeyDown}
                spellCheck={false}
                aria-label="Code editor"
                className="w-full h-full bg-gray-950 text-green-300 font-mono text-xs sm:text-sm p-3 resize-none outline-none overflow-y-auto leading-relaxed"
                placeholder="// Shared with the other participant in real time"
              />
            </div>

            {/* Live Terminal & Console Output */}
            {terminalOpen && (
              <div className={`${terminalTab === 'copilot' ? 'h-72' : 'h-52'} bg-black border-t border-gray-800 flex flex-col flex-shrink-0 transition-all duration-150`}>
                <div className="flex items-center justify-between px-3 py-1.5 bg-gray-900/90 border-b border-gray-800 text-xs">
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => setTerminalTab('output')}
                      className={`flex items-center gap-1 font-semibold pb-0.5 border-b-2 transition ${
                        terminalTab === 'output' ? 'text-blue-400 border-blue-500' : 'text-gray-400 border-transparent hover:text-gray-200'
                      }`}
                    >
                      <Terminal className="h-3 w-3" />
                      <span>Console Output</span>
                    </button>
                    <button
                      onClick={() => setTerminalTab('stdin')}
                      className={`font-semibold pb-0.5 border-b-2 transition ${
                        terminalTab === 'stdin' ? 'text-blue-400 border-blue-500' : 'text-gray-400 border-transparent hover:text-gray-200'
                      }`}
                    >
                      <span>Custom Input (stdin)</span>
                      {customInput && <span className="ml-1 w-1.5 h-1.5 inline-block bg-blue-500 rounded-full"></span>}
                    </button>
                    {joinInfo?.role === 'recruiter' && (
                      <button
                        onClick={() => {
                          setTerminalTab('copilot');
                          if (!copilotData && !isAnalyzingCopilot) {
                            fetchCopilotProbes();
                          }
                        }}
                        className={`flex items-center gap-1 font-semibold pb-0.5 border-b-2 transition ${
                          terminalTab === 'copilot' ? 'text-purple-400 border-purple-500' : 'text-gray-400 border-transparent hover:text-purple-300'
                        }`}
                      >
                        <Sparkles className="h-3 w-3 text-purple-400" />
                        <span>AI Copilot</span>
                        <span className="text-[9px] px-1 py-0.5 bg-purple-950 text-purple-300 border border-purple-800 rounded font-medium">
                          Private
                        </span>
                      </button>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    {terminalTab === 'copilot' ? (
                      <button
                        onClick={fetchCopilotProbes}
                        disabled={isAnalyzingCopilot}
                        title="Analyze current code and regenerate probes"
                        className="flex items-center gap-1 px-2 py-0.5 bg-purple-950/80 hover:bg-purple-900 border border-purple-800 text-purple-300 text-xs rounded transition"
                      >
                        <RefreshCw className={`h-3 w-3 ${isAnalyzingCopilot ? 'animate-spin' : ''}`} />
                        <span>{isAnalyzingCopilot ? 'Analyzing...' : 'Analyze Code'}</span>
                      </button>
                    ) : (
                      <>
                        {executionStats && (
                          <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono font-medium ${
                            executionStats.status === 'SUCCESS' ? 'bg-green-900/60 text-green-300 border border-green-700' :
                            executionStats.status === 'TIMEOUT' ? 'bg-yellow-900/60 text-yellow-300 border border-yellow-700' :
                            'bg-red-900/60 text-red-300 border border-red-700'
                          }`}>
                            {executionStats.status} ({executionStats.durationMs}ms) • {executionStats.ranBy}
                          </span>
                        )}
                        <button
                          onClick={handleClearConsole}
                          title="Clear console"
                          aria-label="Clear console"
                          className="p-1 text-gray-400 hover:text-gray-200 rounded"
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      </>
                    )}
                    <button
                      onClick={() => setTerminalOpen(false)}
                      title="Minimize drawer"
                      aria-label="Minimize drawer"
                      className="p-1 text-gray-400 hover:text-gray-200 rounded"
                    >
                      <ChevronDown className="h-3 w-3" />
                    </button>
                  </div>
                </div>

                <div className="flex-1 p-3 overflow-y-auto font-mono text-xs select-text">
                  {terminalTab === 'output' ? (
                    terminalLogs.length === 0 ? (
                      <div className="text-gray-500 italic select-none">
                        Press "Run Code" or (Ctrl + Enter) to execute and view stdout/stderr output in real time.
                      </div>
                    ) : (
                      <div className="space-y-1">
                        {terminalLogs.map((log, idx) => (
                          <div
                            key={idx}
                            className={`whitespace-pre-wrap leading-relaxed ${
                              log.type === 'stderr' ? 'text-red-400' :
                              log.type === 'system' ? 'text-blue-400 italic' :
                              'text-green-300'
                            }`}
                          >
                            {log.text}
                          </div>
                        ))}
                      </div>
                    )
                  ) : terminalTab === 'stdin' ? (
                    <div className="h-full flex flex-col">
                      <div className="text-[11px] text-gray-400 mb-1 select-none">
                        Input passed to the program via standard input:
                      </div>
                      <textarea
                        value={customInput}
                        onChange={handleCustomInputChange}
                        placeholder="Enter custom input test case here..."
                        className="flex-1 w-full bg-gray-900 text-gray-200 p-2 rounded border border-gray-800 text-xs font-mono outline-none resize-none"
                      />
                    </div>
                  ) : (
                    /* AI Live Interview Copilot View */
                    <div className="font-sans space-y-3">
                      {isAnalyzingCopilot && (
                        <div className="flex flex-col items-center justify-center py-6 text-gray-400">
                          <Loader2 className="h-5 w-5 animate-spin text-purple-400 mb-2" />
                          <span className="text-xs">Analyzing code semantics, algorithmic bounds, and generating probing questions...</span>
                        </div>
                      )}

                      {copilotError && (
                        <div className="p-2.5 bg-red-950/60 border border-red-800 rounded text-red-300 text-xs flex items-center justify-between">
                          <span>{copilotError}</span>
                          <button onClick={fetchCopilotProbes} className="underline text-red-200 hover:text-white">Retry</button>
                        </div>
                      )}

                      {!isAnalyzingCopilot && !copilotData && !copilotError && (
                        <div className="text-center py-6">
                          <Sparkles className="h-6 w-6 text-purple-400 mx-auto mb-2" />
                          <p className="text-gray-300 text-xs font-medium mb-2">Interviewer AI Copilot is Ready</p>
                          <p className="text-gray-500 text-[11px] max-w-md mx-auto mb-3">
                            Click below to analyze the candidate's live code and generate targeted complexity estimations, edge-case probes, and optimization questions.
                          </p>
                          <button
                            onClick={fetchCopilotProbes}
                            className="px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold rounded shadow transition inline-flex items-center gap-1.5"
                          >
                            <Sparkles className="h-3.5 w-3.5" />
                            <span>Analyze Candidate Code</span>
                          </button>
                        </div>
                      )}

                      {!isAnalyzingCopilot && copilotData && (
                        <>
                          {/* Complexity & High-Level Breakdown */}
                          <div className="p-2.5 bg-purple-950/40 border border-purple-800/60 rounded-md">
                            <div className="flex flex-wrap items-center gap-3 mb-1.5">
                              <span className="text-purple-300 text-xs font-semibold uppercase tracking-wider flex items-center gap-1">
                                <Zap className="h-3.5 w-3.5 text-yellow-400" /> Algorithmic Complexity:
                              </span>
                              <span className={`px-2 py-0.5 rounded text-[11px] font-mono font-bold ${
                                copilotData.estimatedComplexity?.time?.includes('²') ? 'bg-red-900/70 text-red-300 border border-red-700' :
                                copilotData.estimatedComplexity?.time?.includes('log') ? 'bg-yellow-900/70 text-yellow-300 border border-yellow-700' :
                                'bg-green-900/70 text-green-300 border border-green-700'
                              }`}>
                                Time: {copilotData.estimatedComplexity?.time || 'O(N)'}
                              </span>
                              <span className="px-2 py-0.5 bg-blue-900/70 text-blue-300 border border-blue-700 rounded text-[11px] font-mono font-bold">
                                Space: {copilotData.estimatedComplexity?.space || 'O(1)'}
                              </span>
                            </div>
                            <p className="text-gray-300 text-xs leading-relaxed">
                              {copilotData.estimatedComplexity?.explanation}
                            </p>

                            {copilotData.strengths && copilotData.strengths.length > 0 && (
                              <div className="flex flex-wrap gap-1.5 mt-2 pt-2 border-t border-purple-800/40">
                                {copilotData.strengths.map((str, i) => (
                                  <span key={i} className="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-900 text-green-300 rounded text-[10px] border border-green-900/60">
                                    <Check className="h-2.5 w-2.5 text-green-400" />
                                    <span>{str}</span>
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>

                          {/* Edge Cases Probes */}
                          {copilotData.edgeCases && copilotData.edgeCases.length > 0 && (
                            <div className="space-y-1.5">
                              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                                Edge Cases & Boundary Probes to Ask Candidate
                              </div>
                              <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                {copilotData.edgeCases.map((ec, idx) => (
                                  <div key={idx} className="p-2 bg-gray-900 border border-gray-800 rounded flex flex-col justify-between">
                                    <div>
                                      <span className="text-[10px] font-bold text-yellow-400 uppercase tracking-wider block mb-1">
                                        Scenario: {ec.scenario}
                                      </span>
                                      <p className="text-gray-200 text-xs leading-normal">
                                        "{ec.probe}"
                                      </p>
                                    </div>
                                    <div className="mt-2 flex justify-end">
                                      <button
                                        onClick={() => handleCopyProbe(ec.probe, `ec-${idx}`)}
                                        className="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-800 hover:bg-gray-700 text-gray-300 text-[10px] rounded transition"
                                      >
                                        {copilotCopiedIdx === `ec-${idx}` ? (
                                          <>
                                            <Check className="h-3 w-3 text-green-400" />
                                            <span className="text-green-300">Copied</span>
                                          </>
                                        ) : (
                                          <>
                                            <Copy className="h-3 w-3" />
                                            <span>Copy Question</span>
                                          </>
                                        )}
                                      </button>
                                    </div>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* Optimization Probes */}
                          {copilotData.optimizationProbes && copilotData.optimizationProbes.length > 0 && (
                            <div className="space-y-1.5">
                              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                                Algorithmic Optimization & Tradeoff Questions
                              </div>
                              <div className="space-y-2">
                                {copilotData.optimizationProbes.map((opt, idx) => (
                                  <div key={idx} className="p-2.5 bg-gray-900 border border-gray-800 rounded">
                                    <div className="flex items-center justify-between mb-1">
                                      <span className="text-purple-300 text-xs font-semibold">
                                        {opt.topic}
                                      </span>
                                      <button
                                        onClick={() => handleCopyProbe(opt.question, `opt-${idx}`)}
                                        className="inline-flex items-center gap-1 px-2 py-0.5 bg-gray-800 hover:bg-gray-700 text-gray-300 text-[10px] rounded transition"
                                      >
                                        {copilotCopiedIdx === `opt-${idx}` ? (
                                          <>
                                            <Check className="h-3 w-3 text-green-400" />
                                            <span className="text-green-300">Copied</span>
                                          </>
                                        ) : (
                                          <>
                                            <Copy className="h-3 w-3" />
                                            <span>Copy Question</span>
                                          </>
                                        )}
                                      </button>
                                    </div>
                                    <p className="text-gray-200 text-xs mb-1.5">
                                      "{opt.question}"
                                    </p>
                                    {opt.expectedInsight && (
                                      <div className="text-[11px] text-purple-300/80 bg-purple-950/40 p-1.5 rounded border border-purple-900/40">
                                        <span className="font-semibold text-purple-300">Look for: </span>
                                        {opt.expectedInsight}
                                      </div>
                                    )}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* Architecture & Scalability Follow-Ups */}
                          {copilotData.architecturalFollowUps && copilotData.architecturalFollowUps.length > 0 && (
                            <div className="space-y-1.5">
                              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                                Production Scale & System Design Follow-Ups
                              </div>
                              <ul className="space-y-1 list-disc list-inside text-xs text-gray-300">
                                {copilotData.architecturalFollowUps.map((item, idx) => (
                                  <li key={idx} className="leading-relaxed">
                                    {item}
                                  </li>
                                ))}
                              </ul>
                            </div>
                          )}
                        </>
                      )}
                    </div>
                  )}
                </div>
              </div>
            )}
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
        <button
          onClick={toggleRecording}
          aria-label={isRecording ? 'Stop recording interview' : 'Start recording interview'}
          title={isRecording ? `Recording in progress (${Math.floor(recordingTime / 60)}:${('0' + (recordingTime % 60)).slice(-2)}) - click to stop` : 'Record interview'}
          className={`flex items-center gap-1.5 px-3 py-2 rounded-full text-sm font-medium transition-all ${
            isRecording
              ? 'bg-red-600 text-white animate-pulse'
              : 'bg-gray-700 text-gray-200 hover:bg-gray-600'
          }`}
        >
          <Circle className={`h-4 w-4 ${isRecording ? 'fill-white text-white' : 'text-red-500 fill-red-500'}`} />
          <span>{isRecording ? `${Math.floor(recordingTime / 60)}:${('0' + (recordingTime % 60)).slice(-2)}` : 'Record'}</span>
        </button>
        <button onClick={endCall} aria-label="End call" className="p-3 rounded-full bg-red-600 text-white">
          <PhoneOff className="h-5 w-5" />
        </button>
      </div>
    </div>
  );
};

export default InterviewRoundRoom;
