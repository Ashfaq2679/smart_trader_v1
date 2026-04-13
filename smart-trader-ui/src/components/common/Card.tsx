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
  <div className={`rounded-lg border border-gray-200 bg-white shadow-sm ${className}`}>
    {(title ?? actions) && (
      <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
        {title && <h3 className="text-lg font-semibold text-gray-900">{title}</h3>}
        {actions && <div className="flex gap-2">{actions}</div>}
      </div>
    )}
    <div className="p-6">{children}</div>
  </div>
);
