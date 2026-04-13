interface AlertProps {
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  onDismiss?: () => void;
}

const alertStyles = {
  success: 'bg-green-50 border-green-200 text-green-800',
  error: 'bg-red-50 border-red-200 text-red-800',
  warning: 'bg-yellow-50 border-yellow-200 text-yellow-800',
  info: 'bg-blue-50 border-blue-200 text-blue-800',
};

/**
 * Reusable alert banner for displaying status messages.
 */
export const Alert = ({ type, message, onDismiss }: AlertProps) => (
  <div
    className={`flex items-center justify-between rounded-lg border p-4 ${alertStyles[type]}`}
    role="alert"
  >
    <p className="text-sm font-medium">{message}</p>
    {onDismiss && (
      <button
        type="button"
        onClick={onDismiss}
        className="ml-4 text-lg font-bold opacity-50 hover:opacity-100"
        aria-label="Dismiss"
      >
        ×
      </button>
    )}
  </div>
);
