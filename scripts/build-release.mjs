// Normal release build entrypoint. Signing identity persists outside disposable checkouts.
import { existsSync, mkdirSync, chmodSync, writeFileSync, renameSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, resolve } from 'node:path';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const signingDir = join(homedir(), '.config', 'h5-app', 'signing');
let properties = process.env.H5_RELEASE_SIGNING_PROPERTIES;
if (properties) {
  if (!existsSync(properties)) throw Error('Configured release signing properties do not exist');
} else if (existsSync('keystore.properties')) {
  properties = resolve('keystore.properties');
} else {
  mkdirSync(signingDir, { recursive: true, mode: 0o700 });
  chmodSync(signingDir, 0o700);
  properties = join(signingDir, 'keystore.properties');
  const key = join(signingDir, 'release.p12');
  if (!existsSync(properties)) {
    // Never replace a surviving key when its configuration is missing.
    if (existsSync(key) || existsSync(key + '.pending')) {
      throw Error('Existing release key needs its signing properties restored; refusing to replace it');
    }
    const password = randomBytes(36).toString('hex');
    const keytool = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'keytool') : 'keytool';
    try {
      execFileSync(keytool, ['-genkeypair', '-keystore', key + '.pending', '-storetype', 'PKCS12',
        '-alias', 'h5-app-release', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
        '-dname', 'CN=H5 App Release', '-storepass:env', 'H5_KEY_PASSWORD',
        '-keypass:env', 'H5_KEY_PASSWORD'], {
        env: { ...process.env, H5_KEY_PASSWORD: password }, stdio: 'ignore'
      });
    } catch {
      throw Error('Release key creation failed; signing directory retained for recovery');
    }
    chmodSync(key + '.pending', 0o600);
    renameSync(key + '.pending', key);
    const escapedPath = key.replaceAll('\\', '\\\\').replaceAll(':', '\\:');
    writeFileSync(properties, `storeFile=${escapedPath}\nstorePassword=${password}\nkeyAlias=h5-app-release\nkeyPassword=${password}\n`, { mode: 0o600, flag: 'wx' });
    console.log('Created persistent release identity. Back up the private signing directory securely: ' + signingDir);
  }
}
execFileSync('sh', ['./gradlew', '--no-daemon', '--console=plain', ':app:assembleRelease'], {
  env: { ...process.env, H5_RELEASE_SIGNING_PROPERTIES: properties }, stdio: 'inherit'
});
