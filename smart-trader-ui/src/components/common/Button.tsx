import type { ButtonHTMLAttributes } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isLoading?: boolean;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary: 'btn-primary',
  secondary: 'btn-outline-secondary',
  danger: 'btn-danger',
  ghost: 'btn-link text-secondary text-decoration-none',
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'btn-sm',
  md: '',
  lg: 'btn-lg',
};

/**
 * Reusable button component with variant and size support.
 */
export const Button = ({
  variant = 'primary',
  size = 'md',
  isLoading = false,
  children,
  disabled,
  className = '',
  ...props
}: ButtonProps) => (
  <button
    type="button"
    disabled={disabled || isLoading}
    className={`btn ${variantClasses[variant]} ${sizeClasses[size]} d-inline-flex align-items-center gap-2 ${className}`}
    {...props}
  >
    {isLoading && (
      <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true" />
    )}
    {children}
  </button>
);
