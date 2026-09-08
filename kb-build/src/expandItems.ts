export interface ItemIdRow {
  page_name: string;
  id: number[];
}

interface NamedItemRef {
  name: string;
  id: number;
  /** Hand-curated variant ids (a reskin on its own wiki page, e.g. Golden prospector helmet); kept and unioned with the wiki's. */
  ids?: number[];
}

interface RecommendedLike {
  gearOwnedAny: NamedItemRef[];
}

export interface MilestoneLike {
  ownedIf: NamedItemRef[];
  recommended: RecommendedLike | null;
  requirements: { items: NamedItemRef[] };
}

export interface ExpandSummary {
  /** Every ownedIf/gearOwnedAny/requirements.items entry the pass looked at. */
  itemsProcessed: number;
  /** Distinct names whose resolved `ids` has more than one entry. */
  multiId: string[];
  /** Distinct names with no matching `item_id` page (kept as `ids: [id]`). */
  unresolved: string[];
}

/**
 * Adds an `ids: number[]` list next to every milestone `ownedIf[]`, `recommended.gearOwnedAny[]`,
 * and `requirements.items[]` entry's existing `id`: every distinct numeric id the wiki's `item_id`
 * Bucket table carries for that item's page name, always including the entry's own `id`, sorted
 * ascending. `ownedIf` entries additionally fold in the ids of the "(t)" trimmed variant page (e.g.
 * "Dragon defender (t)") when one exists, since a trimmed item still counts as owning the gear
 * milestone; `gearOwnedAny` and `requirements.items` don't get the trimmed-page rule. A name with no
 * matching page keeps `ids: [id]` and is reported as unresolved.
 */
export function expandMilestoneItemIds<M extends MilestoneLike>(
  milestones: M[],
  itemIdRows: ItemIdRow[],
): { milestones: M[]; summary: ExpandSummary } {
  // The wiki's `item_id` Bucket table carries one *row* per id, not one row with every id for a
  // page - so a page with several ids (e.g. Dragon defender: 12954/20463/24143) shows up as
  // several rows sharing the same `page_name`. Group and union rather than overwrite.
  const byPageName = new Map<string, number[]>();
  for (const row of itemIdRows) {
    const key = row.page_name.toLowerCase();
    const existing = byPageName.get(key);
    if (existing) {
      existing.push(...row.id);
    } else {
      byPageName.set(key, [...row.id]);
    }
  }

  let itemsProcessed = 0;
  const multiId = new Set<string>();
  const unresolved = new Set<string>();

  function idsFor(name: string, id: number, includeTrimmed: boolean, existing: number[] = []): number[] {
    itemsProcessed++;
    const wikiIds = byPageName.get(name.toLowerCase());
    const trimmedIds = includeTrimmed ? (byPageName.get(`${name} (t)`.toLowerCase()) ?? []) : [];
    if (wikiIds === undefined && trimmedIds.length === 0) {
      unresolved.add(name);
    }
    const ids = [...new Set([id, ...existing, ...(wikiIds ?? []), ...trimmedIds])].sort((a, b) => a - b);
    if (ids.length > 1) {
      multiId.add(name);
    }
    return ids;
  }

  const expanded = milestones.map((milestone) => ({
    ...milestone,
    ownedIf: milestone.ownedIf.map((owned) => ({ ...owned, ids: idsFor(owned.name, owned.id, true, owned.ids) })),
    recommended:
      milestone.recommended === null
        ? null
        : {
            ...milestone.recommended,
            gearOwnedAny: milestone.recommended.gearOwnedAny.map((owned) => ({ ...owned, ids: idsFor(owned.name, owned.id, false, owned.ids) })),
          },
    requirements: {
      ...milestone.requirements,
      items: milestone.requirements.items.map((item) => ({ ...item, ids: idsFor(item.name, item.id, false, item.ids) })),
    },
  }));

  return {
    milestones: expanded,
    summary: { itemsProcessed, multiId: [...multiId].sort(), unresolved: [...unresolved].sort() },
  };
}
