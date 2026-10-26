import mark from '../assets/lapsus-mark.png';

export function LapsusMark({ size = 28 }: { size?: number }) {
  return (
    <img src={mark} width={size} height={size} alt="" className="brand-mark" draggable={false} />
  );
}

export function Wordmark({ onDark }: { onDark?: boolean }) {
  return (
    <span className={`wordmark ${onDark ? 'wordmark--on-dark' : ''}`}>
      <LapsusMark size={26} />
      <span className="wordmark__text">Lapsus</span>
    </span>
  );
}
