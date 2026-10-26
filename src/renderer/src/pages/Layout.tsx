import type { ReactNode } from 'react';

export function PageLayout({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="page">
      <div className="page__inner">
        <header className="page__header">
          <div>
            <h1 className="page__title">{title}</h1>
            {subtitle && <p className="page__subtitle">{subtitle}</p>}
          </div>
          {actions && <div className="page__actions">{actions}</div>}
        </header>
        {children}
      </div>
    </div>
  );
}
