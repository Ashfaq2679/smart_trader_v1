import type { ReactNode } from 'react';

interface CardProps {
  title?: string;
  children: ReactNode;
  className?: string;
  actions?: ReactNode;
}

/**
 * Reusable card container component.
 */
export const Card = ({ title, children, className = '', actions }: CardProps) => (
  <div className={`card shadow-sm ${className}`}>
    {(title ?? actions) && (
      <div className="card-header d-flex align-items-center justify-content-between bg-white">
        {title && <h5 className="card-title mb-0">{title}</h5>}
        {actions && <div className="d-flex gap-2">{actions}</div>}
      </div>
    )}
    <div className="card-body">{children}</div>
  </div>
);
