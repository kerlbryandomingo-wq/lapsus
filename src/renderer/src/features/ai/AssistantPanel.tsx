import { useEffect, useRef, useState } from 'react';
import {
  Sparkles,
  X,
  TextSelect,
  Send,
  Pencil,
  Trash2,
  Settings as SettingsIcon,
  ChevronDown,
  ShieldCheck,
  CircleAlert,
} from 'lucide-react';
import type { AIAnswer } from '@shared/ai';
import { useStore } from '../../lib/store';
import { api } from '../../lib/api';
import { Button, IconButton, Pill, ProgressRing } from '../../components/ui';
import { AnswerCard } from './AnswerCard';

type Entry =
  | { id: number; kind: 'answer'; question: string; answer: AIAnswer; source: string }
  | { id: number; kind: 'error'; question: string; error: string; code: string };

interface Review {
  text: string;
  truncated: boolean;
  redactions: number;
}

let entryId = 0;

const PROVIDER_NAME: Record<string, string> = {
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  demo: 'Demo mode',
  none: 'No provider',
};

export function AssistantPanel() {
  const { state, dispatch } = useStore();
  const ai = state.ai;
  const [material, setMaterial] = useState('');
  const [note, setNote] = useState('');
  const [review, setReview] = useState<Review | null>(null);
  const [busy, setBusy] = useState(false);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [showInfo, setShowInfo] = useState(false);
  const textarea = useRef<HTMLTextAreaElement>(null);
  const scroller = useRef<HTMLDivElement>(null);

  useEffect(() => {
    textarea.current?.focus();
  }, []);

  const pullSelection = async () => {
    const res = await api.invoke('ai:get-selection');
    if (res.reason === 'password') {
      dispatch({
        type: 'notice',
        notice: { level: 'warning', message: 'Lapsus never reads password fields.' },
      });
      return;
    }
    if (!res.text.trim()) {
      dispatch({
        type: 'notice',
        notice: {
          level: 'info',
          message: 'Select the question and its choices on the page first, then try again.',
        },
      });
      return;
    }
    setMaterial(res.text.trim());
    setReview(null);
    textarea.current?.focus();
  };

  useEffect(() => {
    if (state.askSelectionToken > 0) void pullSelection();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.askSelectionToken]);

  useEffect(() => {
    scroller.current?.scrollTo({ top: scroller.current.scrollHeight, behavior: 'smooth' });
  }, [entries.length, busy]);

  const startReview = async () => {
    const prepared = await api.invoke('ai:prepare', { material });
    setReview(prepared);
  };

  const send = async () => {
    if (!review) return;
    setBusy(true);
    const question = review.text;
    const result = await api.invoke('ai:ask', { material, note });
    setBusy(false);
    if (result.ok) {
      setEntries((e) => [
        ...e,
        {
          id: ++entryId,
          kind: 'answer',
          question,
          answer: result.answer,
          source: `${PROVIDER_NAME[result.provider]}${result.provider === 'demo' ? '' : ` · ${result.model}`}`,
        },
      ]);
      setMaterial('');
      setNote('');
      setReview(null);
    } else if (result.code !== 'cancelled') {
      setEntries((e) => [
        ...e,
        { id: ++entryId, kind: 'error', question, error: result.error, code: result.code },
      ]);
    }
  };

  const configured = ai && ai.provider !== 'none';
  const providerName = PROVIDER_NAME[ai?.provider ?? 'none'];

  return (
    <aside className="assistant" aria-label="AI Study Assistant">
      <header className="assistant__head">
        <div className="assistant__title">
          <span className="assistant__icon" aria-hidden="true">
            <Sparkles size={16} />
          </span>
          <div>
            <h2>Study Assistant</h2>
            <span className="assistant__status">
              <span className={`status-dot ${ai?.ready ? 'is-ok' : 'is-off'}`} aria-hidden="true" />
              {ai?.ready ? `${providerName} ready` : configured ? 'Needs setup' : 'Not configured'}
            </span>
          </div>
        </div>
        <div className="assistant__head-actions">
          {entries.length > 0 && (
            <IconButton label="Clear conversation" onClick={() => setEntries([])}>
              <Trash2 size={16} />
            </IconButton>
          )}
          <IconButton
            label="Close assistant"
            onClick={() => dispatch({ type: 'assistant', open: false })}
          >
            <X size={17} />
          </IconButton>
        </div>
      </header>

      <div className="assistant__notice">
        <button
          type="button"
          className="assistant__notice-toggle"
          onClick={() => setShowInfo((v) => !v)}
          aria-expanded={showInfo}
        >
          <ShieldCheck size={14} aria-hidden="true" />
          <span>Study mode · nothing is sent until you press Send</span>
          <ChevronDown size={14} aria-hidden="true" className={showInfo ? 'is-open' : ''} />
        </button>
        {showInfo && (
          <div className="assistant__notice-body">
            <p>
              Lapsus does not read the page by itself. You paste text or choose{' '}
              <em>Use selected text</em>, review exactly what will be sent, then press Send. The
              text goes from your computer to{' '}
              {configured ? providerName : 'the provider you choose'} and nowhere else. Passwords,
              cookies, whole pages and history are never sent. Things that look like passwords,
              tokens and email addresses are removed first.
            </p>
            <p>
              Use the assistant to understand material. Follow your course's rules for graded
              quizzes and exams.
            </p>
          </div>
        )}
      </div>

      <div className="assistant__scroll" ref={scroller}>
        {!configured && (
          <div className="assistant__empty">
            <ProgressRing value={0} label="Not configured" size={56} tone="navy">
              <Sparkles size={18} aria-hidden="true" />
            </ProgressRing>
            <h3>Set up the assistant</h3>
            <p>
              Choose OpenAI or Anthropic and add your own API key, or try the offline demo to see
              how answers look.
            </p>
            <div className="assistant__empty-actions">
              <Button
                variant="primary"
                icon={<SettingsIcon size={15} />}
                onClick={() => dispatch({ type: 'page', page: 'settings' })}
              >
                Open Settings
              </Button>
              <Button
                variant="secondary"
                onClick={() => void api.invoke('settings:update', { aiProvider: 'demo' })}
              >
                Try demo mode
              </Button>
            </div>
          </div>
        )}
        {configured && entries.length === 0 && !busy && (
          <div className="assistant__empty assistant__empty--hint">
            <h3>Ask about a practice question</h3>
            <p>
              Paste a question with its answer choices, or select it on the page and choose Use
              selected text.
            </p>
          </div>
        )}
        {entries.map((e) => (
          <div key={e.id} className="assistant__entry">
            <div className="assistant__q" data-selectable>
              {e.question.length > 280 ? `${e.question.slice(0, 280)}…` : e.question}
            </div>
            {e.kind === 'answer' ? (
              <AnswerCard answer={e.answer} source={e.source} />
            ) : (
              <div className="assistant__error" role="alert">
                <CircleAlert size={16} aria-hidden="true" />
                <div>
                  <strong>The assistant could not answer</strong>
                  <p>{e.error}</p>
                  {(e.code === 'not-configured' || e.code === 'auth' || e.code === 'limit') && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => dispatch({ type: 'page', page: 'settings' })}
                    >
                      Open Settings
                    </Button>
                  )}
                </div>
              </div>
            )}
          </div>
        ))}
        {busy && (
          <div className="assistant__loading" role="status">
            <ProgressRing label="Waiting for the AI" size={28} stroke={4} tone="navy" />
            <span>Thinking it through…</span>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => void api.invoke('ai:cancel').then(() => setBusy(false))}
            >
              Cancel
            </Button>
          </div>
        )}
      </div>

      {configured && (
        <div className="assistant__composer">
          {review ? (
            <div className="review">
              <div className="review__head">
                <Pill tone="gold">Review before sending</Pill>
                <span className="review__meta">
                  {review.text.length} characters
                  {review.redactions > 0 &&
                    ` · ${review.redactions} item${review.redactions > 1 ? 's' : ''} removed`}
                  {review.truncated && ' · shortened'}
                </span>
              </div>
              <pre className="review__text" data-selectable>
                {review.text || '(nothing to send)'}
              </pre>
              {note.trim() && <p className="review__note">Your question: {note.trim()}</p>}
              <div className="review__actions">
                <Button
                  variant="ghost"
                  icon={<Pencil size={15} />}
                  onClick={() => setReview(null)}
                  disabled={busy}
                >
                  Edit
                </Button>
                <Button
                  variant="primary"
                  icon={<Send size={15} />}
                  onClick={() => void send()}
                  disabled={busy || !review.text || !ai?.ready}
                >
                  Send to {providerName}
                </Button>
              </div>
            </div>
          ) : (
            <>
              <textarea
                ref={textarea}
                className="composer__text"
                value={material}
                onChange={(e) => setMaterial(e.target.value)}
                placeholder="Paste a practice question and its answer choices…"
                aria-label="Question to analyse"
                rows={5}
                maxLength={50000}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && e.ctrlKey && material.trim()) void startReview();
                }}
              />
              <input
                className="composer__note"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="What would you like to understand? (optional)"
                aria-label="Your question for the assistant"
                maxLength={500}
              />
              <div className="composer__actions">
                <Button
                  variant="secondary"
                  icon={<TextSelect size={15} />}
                  onClick={() => void pullSelection()}
                >
                  Use selected text
                </Button>
                <Button
                  variant="primary"
                  onClick={() => void startReview()}
                  disabled={!material.trim()}
                >
                  Review
                </Button>
              </div>
            </>
          )}
        </div>
      )}
    </aside>
  );
}
