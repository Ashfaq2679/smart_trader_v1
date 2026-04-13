import { NavLink } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

const navItems = [
  { to: '/', label: 'Dashboard', icon: '📊' },
  { to: '/scanner', label: 'Market Scanner', icon: '🔍' },
  { to: '/orders', label: 'Orders', icon: '📋' },
  { to: '/settings', label: 'Settings', icon: '⚙️' },
];

/**
 * Sidebar navigation component with active state highlighting.
 */
export const Sidebar = () => {
  const { user } = useAuth();

  return (
    <aside className="flex h-screen w-64 flex-col border-r border-gray-200 bg-gray-50">
      <div className="border-b border-gray-200 px-6 py-5">
        <h1 className="text-xl font-bold text-blue-600">Smart Trader</h1>
        <p className="mt-1 text-xs text-gray-500">v1.0</p>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4" aria-label="Main navigation">
        {navItems.map(({ to, label, icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
              }`
            }
          >
            <span className="text-lg">{icon}</span>
            {label}
          </NavLink>
        ))}
      </nav>

      {user && (
        <div className="border-t border-gray-200 px-4 py-4">
          <p className="truncate text-sm font-medium text-gray-700">
            {user.displayName || user.userId}
          </p>
          <p className="truncate text-xs text-gray-500">{user.email}</p>
        </div>
      )}
    </aside>
  );
};
