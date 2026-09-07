export const USER_AGENT =
  'next-target-advisor-kb-build (https://github.com/reece; reece@develp.io)';

export type FetchLike = typeof fetch;

const API_URL = 'https://oldschool.runescape.wiki/api.php';
const REVISIONS_BATCH_SIZE = 50;
const BUCKET_PAGE_SIZE = 5000;

interface ApiErrorEnvelope {
  error?: { code: string; info: string };
}

/** Fetches `url` and returns its parsed JSON body, failing loudly on any transport or API-level error. */
async function requestJson<T>(url: URL, f: FetchLike): Promise<T> {
  const response = await f(url, { headers: { 'User-Agent': USER_AGENT } });
  if (!response.ok) {
    throw new Error(`Wiki API request failed: HTTP ${response.status} ${response.statusText} for ${url}`);
  }
  const body = (await response.json()) as T & ApiErrorEnvelope;
  if (body.error) {
    throw new Error(`Wiki API error ${body.error.code}: ${body.error.info} (${url})`);
  }
  return body;
}

interface TitleMapping {
  from: string;
  to: string;
}

interface RevisionsApiResponse {
  query?: {
    normalized?: TitleMapping[];
    redirects?: TitleMapping[];
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

    const body = await requestJson<RevisionsApiResponse>(url, f);
    const pages = body.query?.pages ?? [];

    const missing = pages.filter((page) => page.missing).map((page) => page.title);
    if (missing.length > 0) {
      throw new Error(`Wiki pages missing: ${missing.join(', ')}`);
    }

    // The API resolves each requested title through normalization and then
    // redirects before it appears as page.title, so walk both mappings
    // backwards to key the result by the title the caller actually asked for.
    const redirectSource = new Map(body.query?.redirects?.map((r) => [r.to, r.from]));
    const normalizedSource = new Map(body.query?.normalized?.map((n) => [n.to, n.from]));

    for (const page of pages) {
      const revision = page.revisions?.[0];
      if (!revision) {
        throw new Error(`Wiki page has no revisions: ${page.title}`);
      }
      const preRedirectTitle = redirectSource.get(page.title) ?? page.title;
      const requestedTitle = normalizedSource.get(preRedirectTitle) ?? preRedirectTitle;
      result.set(requestedTitle, {
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

    const body = await requestJson<BucketApiResponse<T>>(url, f);
    const page = body.bucket ?? [];
    rows.push(...page);

    if (page.length < BUCKET_PAGE_SIZE) {
      break;
    }
  }

  return rows;
}
