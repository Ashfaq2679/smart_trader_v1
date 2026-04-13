interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  message?: string;
}

const sizeClasses = {
  sm: 'spinner-border-sm',
  md: '',
  lg: 'spinner-lg',
};

/**
 * Reusable loading spinner component with optional message.
 */
export const LoadingSpinner = ({
  size = 'md',
  message,
}: LoadingSpinnerProps) => (
  <div className="d-flex flex-column align-items-center justify-content-center gap-3 p-4" role="status">
    <div className={`spinner-border text-primary ${sizeClasses[size]}`}>
      <span className="visually-hidden">Loading...</span>
    </div>
    {message && <p className="small text-secondary mb-0">{message}</p>}
  </div>
);
