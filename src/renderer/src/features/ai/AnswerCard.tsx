import { useState } from 'react';
import {
  Copy,
  Check,
  AlertTriangle,
  Target,
  BookOpenCheck,
  Lightbulb,
  HelpCircle,
} from 'lucide-react';
import type { AIAnswer } from '@shared/ai';
import { IconButton, Pill, ProgressRing } from '../../components/ui';
import { SafeText, answerToText } from './SafeText';

const CONF = { low: 0.33, medium: 0.66, high: 0.95 } as const;

export function AnswerCard({ answer, source }: { answer: AIAnswer; source: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(answerToText(answer));
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };
  return (
    <article className="answer" aria-label="AI explanation">
      <header className="answer__head">
        <Pill tone="ai">AI explanation</Pill>
        <span className="answer__source">{source}</span>
        <IconButton label={copied ? 'Copied' : 'Copy response'} onClick={() => void copy()}>
          {copied ? <Check size={15} /> : <Copy size={15} />}
        </IconButton>
      </header>

      {answer.warning && (
        <div className="answer__warning" role="note">
          <AlertTriangle size={15} aria-hidden="true" />
          <span>{answer.warning}</span>
        </div>
      )}

      <p className="answer__summary" data-selectable>
        {answer.questionSummary}
      </p>

      <div className="answer__lead">
        <Target size={16} aria-hidden="true" />
        {answer.leadingOption ? (
          <span>
            <span className="answer__lead-label">Leading option</span>
            <strong data-selectable>{answer.leadingOption}</strong>
            <span className="answer__unverified">Not verified</span>
          </span>
        ) : (
          <span className="answer__lead-none">
            No leading option. The material does not support a confident choice.
          </span>
        )}
      </div>

      <div className="answer__body" data-selectable>
        <SafeText text={answer.explanation} />
      </div>

      {answer.evidence.length > 0 && (
        <section className="answer__section">
          <h4>
            <BookOpenCheck size={14} aria-hidden="true" /> Supported by the material
          </h4>
          <ul data-selectable>
            {answer.evidence.map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
        </section>
      )}
      {answer.inferences.length > 0 && (
        <section className="answer__section">
          <h4>
            <Lightbulb size={14} aria-hidden="true" /> Reasoning (inference)
          </h4>
          <ul data-selectable>
            {answer.inferences.map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
        </section>
      )}
      {answer.missingInformation.length > 0 && (
        <section className="answer__section">
          <h4>
            <HelpCircle size={14} aria-hidden="true" /> Missing information
          </h4>
          <ul data-selectable>
            {answer.missingInformation.map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
        </section>
      )}

      <footer className="answer__confidence">
        <ProgressRing
          value={CONF[answer.confidence]}
          size={40}
          stroke={5}
          label={`Confidence ${answer.confidence}`}
          tone={
            answer.confidence === 'high'
              ? 'success'
              : answer.confidence === 'medium'
                ? 'gold'
                : 'maroon'
          }
        />
        <div>
          <span className="answer__conf-label">
            {answer.confidence[0]!.toUpperCase() + answer.confidence.slice(1)} confidence
          </span>
          <span className="answer__conf-reason">{answer.confidenceReason}</span>
        </div>
      </footer>
      <p className="answer__foot">AI can be wrong. Check this against your course material.</p>
    </article>
  );
}
