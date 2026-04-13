interface BadgeProps {
  label: string;
  className?: string;
}

/**
 * Reusable badge component for displaying status/signal labels.
 */
export const Badge = ({ label, className = '' }: BadgeProps) => (
  <span
    className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${className}`}
  >
    {label}
  </span>
);
