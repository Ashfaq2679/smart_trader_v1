import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

/**
 * Main layout component that wraps authenticated pages with sidebar and header.
 */
export const Layout = () => (
  <div className="d-flex vh-100 bg-light">
    <Sidebar />
    <div className="main-content d-flex flex-column overflow-hidden">
      <Header />
      <main className="flex-grow-1 overflow-auto p-4">
        <Outlet />
      </main>
    </div>
  </div>
);
