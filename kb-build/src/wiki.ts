export const USER_AGENT =
  'Signpost-KB/1.0';

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

export interface WikiSource {
  title: string;
  revid: number;
  timestamp: string;
}

export interface WikiRevision extends WikiSource {
  content: string;
}

interface RevisionsApiResponse {
  query?: {
    normalized?: TitleMapping[];
    redirects?: TitleMapping[];
    pages?: Array<{
      title: string;
      missing?: boolean;
      revisions?: Array<{
        revid: number;
        timestamp: string;
        slots?: { main: { content: string } };
      }>;
    }>;
  };
}

/** Current metadata only; missing/deleted pages are represented by null. */
export async function fetchCurrentRevisions(
  titles: string[],
  f: FetchLike = fetch,
): Promise<Map<string, WikiSource | null>> {
  return queryRevisions(titles, false, f);
}

export async function fetchRevisions(
  titles: string[],
  f: FetchLike = fetch,
): Promise<Map<string, WikiRevision>> {
  const pages = await queryRevisions(titles, true, f);
  const result = new Map<string, WikiRevision>();
  for (const [title, page] of pages) {
    if (!page) throw new Error(`Wiki pages missing: ${title}`);
    if (page.content === undefined) throw new Error(`Wiki page has no content: ${title}`);
    result.set(title, { ...page, content: page.content });
  }
  return result;
}

async function queryRevisions(
  titles: string[],
  content: boolean,
  f: FetchLike,
): Promise<Map<string, (WikiSource & { content?: string }) | null>> {
  const result = new Map<string, (WikiSource & { content?: string }) | null>();
  const uniqueTitles = [...new Set(titles)];
  for (let i = 0; i < uniqueTitles.length; i += REVISIONS_BATCH_SIZE) {
    const batch = uniqueTitles.slice(i, i + REVISIONS_BATCH_SIZE);
    const url = new URL(API_URL);
    url.searchParams.set('action', 'query');
    url.searchParams.set('prop', 'revisions');
    url.searchParams.set('rvprop', content ? 'ids|content|timestamp' : 'ids|timestamp');
    if (content) url.searchParams.set('rvslots', 'main');
    url.searchParams.set('formatversion', '2');
    url.searchParams.set('format', 'json');
    url.searchParams.set('maxlag', '5');
    url.searchParams.set('titles', batch.join('|'));

    const body = await requestJson<RevisionsApiResponse>(url, f);
    const pages = new Map((body.query?.pages ?? []).map((page) => [page.title, page]));
    const mappings = new Map([...body.query?.normalized ?? [], ...body.query?.redirects ?? []]
      .map((mapping) => [mapping.from, mapping.to]));
    for (const requested of batch) {
      let title = requested;
      const seen = new Set<string>();
      while (mappings.has(title) && !seen.has(title)) {
        seen.add(title);
        title = mappings.get(title)!;
      }
      const page = pages.get(title);
      if (!page) throw new Error(`Wiki page missing from response: ${requested}`);
      if (page.missing) {
        result.set(requested, null);
        continue;
      }
      const revision = page.revisions?.[0];
      if (!revision || !Number.isSafeInteger(revision.revid) || revision.revid <= 0
        || typeof revision.timestamp !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(revision.timestamp)
        || !Number.isFinite(Date.parse(revision.timestamp))) {
        throw new Error(`Wiki page has invalid revision metadata: ${title}`);
      }
      result.set(requested, {
        title: page.title,
        revid: revision.revid,
        timestamp: revision.timestamp,
        ...(content ? { content: revision.slots?.main.content } : {}),
      });
    }
  }
  return result;
}

/** Retain every fetched revision, even when the same page was fetched twice during a build. */
export function sourceManifest(...groups: Iterable<WikiSource>[]): WikiSource[] {
  const sources = new Map<string, WikiSource>();
  for (const group of groups) {
    for (const { title, revid, timestamp } of group) {
      sources.set(`${title}\0${revid}`, { title, revid, timestamp });
    }
  }
  return [...sources.values()].sort((a, b) => a.title < b.title ? -1 : a.title > b.title ? 1 : a.revid - b.revid);
}

interface BucketApiResponse<T> {
  bucket?: T[];
}

const MAPPING_URL = 'https://prices.runescape.wiki/api/v1/osrs/mapping';

export interface PriceMappingEntry {
  id: number;
  name: string;
  members: boolean;
  value: number;
  limit?: number;
}

/** Fetches the OSRS Wiki prices API's item mapping (tradeable items only). */
export async function fetchPriceMapping(f: FetchLike = fetch): Promise<PriceMappingEntry[]> {
  const response = await f(MAPPING_URL, { headers: { 'User-Agent': USER_AGENT } });
  if (!response.ok) {
    throw new Error(`Price mapping request failed: HTTP ${response.status} ${response.statusText}`);
  }
  return (await response.json()) as PriceMappingEntry[];
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
