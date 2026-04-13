import { CredentialsManager } from '../components/credentials/CredentialsManager';
import { PreferencesForm } from '../components/preferences/PreferencesForm';

/**
 * Settings page — manage credentials and trading preferences.
 */
const SettingsPage = () => (
  <div className="d-flex flex-column gap-4">
    <h1 className="h4 fw-bold">Settings</h1>
    <CredentialsManager />
    <PreferencesForm />
  </div>
);

export default SettingsPage;
