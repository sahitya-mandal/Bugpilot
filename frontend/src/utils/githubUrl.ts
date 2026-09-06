/**
 * Safely parses a GitHub URL or owner/repository string into { owner, name }.
 * Handles:
 * - https://github.com/owner/repo
 * - http://github.com/owner/repo
 * - github.com/owner/repo
 * - owner/repo
 * - trailing slashes, .git suffix, and extra whitespace
 */
export function parseGitHubUrl(input: string): { owner: string; name: string } | null {
  if (!input) return null;
  let clean = input.trim();
  if (!clean) return null;

  // Remove protocol and www
  clean = clean.replace(/^https?:\/\//i, '').replace(/^www\./i, '');

  // Remove github.com/ prefix
  if (clean.toLowerCase().startsWith('github.com/')) {
    clean = clean.substring('github.com/'.length);
  }

  // Remove query params or hash
  clean = clean.split('?')[0].split('#')[0];

  // Remove trailing slashes
  clean = clean.replace(/\/+$/, '');

  // Remove .git suffix
  clean = clean.replace(/\.git$/i, '');

  const parts = clean.split('/').filter(Boolean);
  if (parts.length === 2) {
    const [owner, name] = parts;
    if (/^[a-zA-Z0-9_.-]+$/.test(owner) && /^[a-zA-Z0-9_.-]+$/.test(name)) {
      return { owner, name };
    }
  }

  return null;
}
