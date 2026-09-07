export const USER_AGENT =
  'next-target-advisor-kb-build (https://github.com/reece; reece@develp.io)';

export type FetchLike = typeof fetch;

const API_URL = 'https://oldschool.runescape.wiki/api.php';
const REVISIONS_BATCH_SIZE = 50;
const BUCKET_PAGE_SIZE = 5000;

interface RevisionsApiResponse {
  query?: {
    pages?: Array<{
      title: string;
      missing?: boolean;
      revisions?: Array<{
        timestamp: string;
        slots: { main: { content: string } };
      }>;
    }>;
  };
}

export async function fetchRevisions(
  titles: string[],
  f: FetchLike = fetch,
): Promise<Map<string, { content: string; timestamp: string }>> {
  const result = new Map<string, { content: string; timestamp: string }>();

  for (let i = 0; i < titles.length; i += REVISIONS_BATCH_SIZE) {
    const batch = titles.slice(i, i + REVISIONS_BATCH_SIZE);
    const url = new URL(API_URL);
    url.searchParams.set('action', 'query');
    url.searchParams.set('prop', 'revisions');
    url.searchParams.set('rvprop', 'content|timestamp');
    url.searchParams.set('rvslots', 'main');
    url.searchParams.set('formatversion', '2');
    url.searchParams.set('format', 'json');
    url.searchParams.set('maxlag', '5');
    url.searchParams.set('titles', batch.join('|'));

    const response = await f(url, { headers: { 'User-Agent': USER_AGENT } });
    const body = (await response.json()) as RevisionsApiResponse;
    const pages = body.query?.pages ?? [];

    const missing = pages.filter((page) => page.missing).map((page) => page.title);
    if (missing.length > 0) {
      throw new Error(`Wiki pages missing: ${missing.join(', ')}`);
    }

    for (const page of pages) {
      const revision = page.revisions?.[0];
      if (!revision) {
        throw new Error(`Wiki page has no revisions: ${page.title}`);
      }
      result.set(page.title, {
        content: revision.slots.main.content,
        timestamp: revision.timestamp,
      });
    }
  }

  return result;
}

interface BucketApiResponse<T> {
  bucket?: T[];
}

export async function bucket<T>(query: string, f: FetchLike = fetch): Promise<T[]> {
  const rows: T[] = [];

  for (let offset = 0; ; offset += BUCKET_PAGE_SIZE) {
    const url = new URL(API_URL);
    url.searchParams.set('action', 'bucket');
    url.searchParams.set('format', 'json');
    url.searchParams.set('query', `${query}.limit(${BUCKET_PAGE_SIZE}).offset(${offset}).run()`);

    const response = await f(url, { headers: { 'User-Agent': USER_AGENT } });
    const body = (await response.json()) as BucketApiResponse<T>;
    const page = body.bucket ?? [];
    rows.push(...page);

    if (page.length < BUCKET_PAGE_SIZE) {
      break;
    }
  }

  return rows;
}
