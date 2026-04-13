import { Link } from 'react-router-dom';
import { Button } from '../components/common/Button';

/**
 * 404 Not Found page.
 */
const NotFoundPage = () => (
  <div className="flex min-h-[60vh] flex-col items-center justify-center text-center">
    <h1 className="text-6xl font-bold text-gray-200">404</h1>
    <h2 className="mt-4 text-xl font-semibold text-gray-800">Page Not Found</h2>
    <p className="mt-2 text-gray-500">
      The page you&apos;re looking for doesn&apos;t exist or has been moved.
    </p>
    <Link to="/" className="mt-6">
      <Button>Go to Dashboard</Button>
    </Link>
  </div>
);

export default NotFoundPage;
