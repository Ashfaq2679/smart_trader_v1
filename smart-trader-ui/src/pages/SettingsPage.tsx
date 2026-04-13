import { CredentialsManager } from '../components/credentials/CredentialsManager';
import { PreferencesForm } from '../components/preferences/PreferencesForm';

/**
 * Settings page — manage credentials and trading preferences.
 */
const SettingsPage = () => (
  <div className="space-y-6">
    <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
    <CredentialsManager />
    <PreferencesForm />
  </div>
);

export default SettingsPage;
