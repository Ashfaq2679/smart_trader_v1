interface AlertProps {
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  onDismiss?: () => void;
}

const alertStyles: Record<string, string> = {
  success: 'alert-success',
  error: 'alert-danger',
  warning: 'alert-warning',
  info: 'alert-info',
};

/**
 * Reusable alert banner for displaying status messages.
 */
export const Alert = ({ type, message, onDismiss }: AlertProps) => (
  <div
    className={`alert ${alertStyles[type]} d-flex align-items-center justify-content-between mb-0 ${onDismiss ? 'alert-dismissible' : ''}`}
    role="alert"
  >
    <span className="small fw-medium">{message}</span>
    {onDismiss && (
      <button
        type="button"
        className="btn-close"
        onClick={onDismiss}
        aria-label="Dismiss"
      />
    )}
  </div>
);
