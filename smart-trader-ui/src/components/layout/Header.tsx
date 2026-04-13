import { useAuth } from '../../hooks/useAuth';
import { Button } from '../common/Button';

/**
 * Application header with user info and logout action.
 */
export const Header = () => {
  const { user, logout } = useAuth();

  return (
    <header className="navbar bg-white border-bottom px-4">
      <div>
        <h2 className="h6 fw-semibold text-dark mb-0">
          Welcome, {user?.displayName ?? 'Trader'}
        </h2>
      </div>

      <div className="d-flex align-items-center gap-3">
        <span className="small text-secondary">{user?.email}</span>
        <Button variant="ghost" size="sm" onClick={logout}>
          Sign out
        </Button>
      </div>
    </header>
  );
};
