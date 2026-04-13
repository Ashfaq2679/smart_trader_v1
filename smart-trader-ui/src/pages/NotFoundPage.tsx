import { Link } from 'react-router-dom';
import { Button } from '../components/common/Button';

/**
 * 404 Not Found page.
 */
const NotFoundPage = () => (
  <div className="d-flex flex-column align-items-center justify-content-center text-center py-5 not-found-container">
    <h1 className="display-1 fw-bold text-body-tertiary">404</h1>
    <h2 className="h5 fw-semibold mt-3">Page Not Found</h2>
    <p className="text-secondary mt-2">
      The page you&apos;re looking for doesn&apos;t exist or has been moved.
    </p>
    <Link to="/" className="mt-4">
      <Button>Go to Dashboard</Button>
    </Link>
  </div>
);

export default NotFoundPage;
