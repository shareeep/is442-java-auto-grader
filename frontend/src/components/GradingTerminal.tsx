import React, { useEffect, useRef, useState } from 'react';
import { Terminal, CheckCircle2, AlertCircle, User, Loader2, X } from 'lucide-react';

interface LogEntry {
  type: 'status' | 'student' | 'error' | 'complete' | 'info';
  timestamp: string;
  message: string;
  data?: any;
}

interface GradingTerminalProps {
  formData: FormData;
  onComplete: (submissions: any[], runId?: string) => void;
  onError: (error: string) => void;
  onClose?: () => void;
}

const GradingTerminal: React.FC<GradingTerminalProps> = ({ formData, onComplete, onError, onClose }) => {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [studentCount, setStudentCount] = useState(0);
  const [phase, setPhase] = useState('Connecting...');
  const [done, setDone] = useState(false);
  const termRef = useRef<HTMLDivElement>(null);
  const allStudents = useRef<any[]>([]);

  const now = () => {
    const d = new Date();
    const t = d.toLocaleTimeString('en-GB', { hour12: false });
    const ms = String(d.getMilliseconds()).padStart(3, '0');
    return `${t}.${ms}`;
  };

  const addLog = (entry: LogEntry) => {
    setLogs(prev => {
      const next = [...prev, entry];
      return next.length > 500 ? next.slice(-500) : next;
    });
  };

  useEffect(() => {
    allStudents.current = [];
    const controller = new AbortController();

    const runStream = async () => {
      addLog({ type: 'info', timestamp: now(), message: 'Connecting to grading stream...' });

      try {
        const res = await fetch('/api/grade/stream', {
          method: 'POST',
          body: formData,
          signal: controller.signal,
        });

        if (!res.ok) {
          throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }

        const reader = res.body?.getReader();
        if (!reader) throw new Error('No response body');

        const decoder = new TextDecoder();
        let buffer = '';
        // Declared outside the read loop so partial events survive chunk boundaries
        let eventName = '';
        let eventData = '';

        while (true) {
          const { done: streamDone, value } = await reader.read();
          if (streamDone) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            const trimmed = line.trimEnd(); // handle \r\n line endings
            if (trimmed.startsWith('event:')) {
              eventName = trimmed.slice(6).trim();
            } else if (trimmed.startsWith('data:')) {
              eventData = trimmed.slice(5).trim();
            } else if (trimmed === '' && eventName && eventData) {
              try {
                const parsed = JSON.parse(eventData);
                handleEvent(eventName, parsed);
              } catch {
                addLog({ type: 'info', timestamp: now(), message: eventData });
              }
              eventName = '';
              eventData = '';
            }
          }
        }

        // If stream ends without a 'complete' event, handle gracefully
        if (!done) {
          setDone(true);
          if (allStudents.current.length > 0) {
            onComplete(allStudents.current);
          }
        }

      } catch (err: any) {
        if (err.name === 'AbortError') return;
        addLog({ type: 'error', timestamp: now(), message: `Stream error: ${err.message}` });
        onError(err.message);
      }
    };

    const handleEvent = (name: string, data: any) => {
      switch (name) {
        case 'session':
          addLog({ type: 'info', timestamp: now(), message: `Session: ${data.sessionId}` });
          break;
        case 'status':
          setPhase(data.message || data.phase);
          addLog({ type: 'status', timestamp: now(), message: data.message });
          break;
        case 'student': {
          allStudents.current.push(data);
          setStudentCount(prev => prev + 1);
          const score = `${data.totalScore}/${data.maxPossibleScore}`;
          const anomalyCount = data.anomalies?.length || 0;
          let msg = `Graded ${data.displayName || data.username} — ${score}`;
          if (anomalyCount > 0) msg += ` (${anomalyCount} anomalies)`;
          addLog({ type: 'student', timestamp: now(), message: msg, data });
          break;
        }
        case 'error':
          addLog({ type: 'error', timestamp: now(), message: data.message });
          onError(data.message);
          break;
        case 'complete':
          addLog({ type: 'complete', timestamp: now(), message: `${data.message} (${data.totalStudents} students)` });
          setDone(true);
          setPhase('Complete');
          onComplete(allStudents.current, data.runId);
          break;
      }
    };

    runStream();
    return () => controller.abort();
  }, []);

  // Auto-scroll
  useEffect(() => {
    if (termRef.current) {
      termRef.current.scrollTop = termRef.current.scrollHeight;
    }
  }, [logs]);

  return (
    <div className="rounded-2xl overflow-hidden border border-primary/10 shadow-xl">
      {/* Terminal header */}
      <div className="flex items-center justify-between px-4 py-3 bg-vsc-panel">
        <div className="flex items-center gap-3">
          <div className="flex gap-1.5">
            <div className="w-3 h-3 rounded-full bg-red-400" />
            <div className="w-3 h-3 rounded-full bg-yellow-400" />
            <div className="w-3 h-3 rounded-full bg-green-400" />
          </div>
          <div className="flex items-center gap-2 text-white/60">
            <Terminal size={14} />
            <span className="font-mono text-xs">auto-grader</span>
          </div>
        </div>
        <div className="flex items-center gap-3 text-white/60 text-xs font-mono">
          {!done && <Loader2 size={12} className="animate-spin" />}
          <span>{phase}</span>
          <span className="text-white/30">|</span>
          <span className="flex items-center gap-1">
            <User size={10} /> {studentCount}
          </span>
          {onClose && (
            <button
              onClick={onClose}
              className="ml-1 text-white/30 hover:text-white/70 transition-colors"
              title="Dismiss terminal"
            >
              <X size={12} />
            </button>
          )}
        </div>
      </div>

      {/* Terminal body */}
      <div
        ref={termRef}
        className="bg-[#0d1117] p-4 h-80 overflow-auto font-mono text-xs leading-relaxed"
      >
        {logs.map((log, i) => (
          <div key={i} className="flex gap-2">
            <span className="text-white/20 shrink-0 select-none">[{log.timestamp}]</span>
            {log.type === 'status' && (
              <span className="text-blue-400">{log.message}</span>
            )}
            {log.type === 'student' && (
              <span className="text-green-400">
                <CheckCircle2 size={10} className="inline mr-1" />
                {log.message}
              </span>
            )}
            {log.type === 'error' && (
              <span className="text-red-400">
                <AlertCircle size={10} className="inline mr-1" />
                {log.message}
              </span>
            )}
            {log.type === 'complete' && (
              <span className="text-emerald-300 font-bold">{log.message}</span>
            )}
            {log.type === 'info' && (
              <span className="text-white/50">{log.message}</span>
            )}
          </div>
        ))}
        {!done && (
          <div className="flex items-center gap-1 text-white/30 mt-1">
            <span className="animate-pulse">_</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default GradingTerminal;
