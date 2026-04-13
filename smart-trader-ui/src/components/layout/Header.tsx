import { useAuth } from '../../hooks/useAuth';
import { Button } from '../common/Button';

/**
 * Application header with user info and logout action.
 */
export const Header = () => {
  const { user, logout } = useAuth();

  return (
    <header className="flex h-16 items-center justify-between border-b border-gray-200 bg-white px-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800">
          Welcome, {user?.displayName ?? 'Trader'}
        </h2>
      </div>

      <div className="flex items-center gap-4">
        <span className="text-sm text-gray-500">{user?.email}</span>
        <Button variant="ghost" size="sm" onClick={logout}>
          Sign out
        </Button>
      </div>
    </header>
  );
};
