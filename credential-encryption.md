CREDENTIAL_ENCRYPTION_KEY is not derived from client credentials — it is an independent, randomly generated AES-256 secret key used to encrypt/decrypt the Coinbase credentials at rest.

Key points:

	•  Any cryptographically random 32-byte value works
	•  It has no relationship to Coinbase API keys/secrets
	•  It's your application's own encryption key for protecting stored credentials
	•  It must remain the same across application restarts (otherwise you can't decrypt previously stored credentials)


Option 1 — Java:
import javax.crypto.KeyGenerator;
import java.util.Base64;

KeyGenerator keyGen = KeyGenerator.getInstance("AES");
keyGen.init(256);
String key = Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
System.out.println(key);

Option 2 — Command line:
openssl rand -base64 32
credential.encryption.key=<paste-base64-encoded-key-here>
OR
set CREDENTIAL_ENCRYPTION_KEY=<paste-base64-encoded-key-here>
Summary:

| Property | Value | |---|---| | Type | Random AES-256 key (Base64-encoded) | | Length | 32 bytes (before encoding) | | Derived from credentials? | No — completely independent | | Can be any value? | Must be cryptographically random, but yes, any valid 32-byte key | | Must persist? | Yes — same key needed to decrypt previously encrypted credentials | | Where to store? | Environment variable, secrets manager, or encrypted config — never hardcoded |

The flow in your app is: ClientService → CoinbaseClientFactory → CredentialEncryptionService uses this key to decrypt stored Coinbase credentials → builds CoinbaseAdvancedClient.