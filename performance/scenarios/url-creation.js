/**
 * Authenticated URL creation. Writes rows — prefer a throwaway database.
 */
import { sleep } from 'k6';
import { createUrl, login } from '../lib/auth.js';
import { constantVus, summaryHandler } from '../lib/options.js';
import { pickUser, sharedSeed } from '../lib/seed.js';

const seed = sharedSeed();
let accessToken;

export const options = constantVus();

export const handleSummary = summaryHandler('url-creation');

export default function () {
  if (!accessToken) {
    const user = pickUser(seed);
    const result = login(user.email, user.password);
    if (!result.accessToken) {
      sleep(1);
      return;
    }
    accessToken = result.accessToken;
  }
  const alias = `k6${Date.now().toString(36)}${__VU}${__ITER}`;
  createUrl(accessToken, `https://example.com/created/${__VU}/${__ITER}`, alias);
}
