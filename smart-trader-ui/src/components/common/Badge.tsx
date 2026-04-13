interface BadgeProps {
  label: string;
  className?: string;
}

/**
 * Reusable badge component for displaying status/signal labels.
 */
export const Badge = ({ label, className = '' }: BadgeProps) => (
  <span className={`badge rounded-pill ${className}`}>
    {label}
  </span>
);
