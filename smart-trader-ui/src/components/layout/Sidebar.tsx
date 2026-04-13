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
    <aside className="sidebar d-flex flex-column border-end bg-light">
      <div className="border-bottom px-4 py-3">
        <h1 className="h5 fw-bold text-primary mb-0">Smart Trader</h1>
        <small className="text-secondary">v1.0</small>
      </div>

      <nav className="flex-grow-1 px-3 py-3" aria-label="Main navigation">
        <ul className="nav flex-column gap-1">
          {navItems.map(({ to, label, icon }) => (
            <li className="nav-item" key={to}>
              <NavLink
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  `nav-link d-flex align-items-center gap-2 py-2 px-3 small fw-medium ${
                    isActive ? 'active' : 'text-secondary'
                  }`
                }
              >
                <span className="fs-6">{icon}</span>
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      {user && (
        <div className="border-top px-4 py-3">
          <p className="mb-0 small fw-medium text-dark text-truncate">
            {user.displayName || user.userId}
          </p>
          <small className="text-secondary text-truncate d-block">{user.email}</small>
        </div>
      )}
    </aside>
  );
};
