package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * One curated milestone (gear upgrade, unlock, prayer, spellbook, slayer target, or boss), from
 * the hand-curated {@code /kb/milestones.json}. Unlike quests/diaries this data isn't wiki-scraped
 * verbatim - it's a hand-drafted priority list (see the ticket's B4/B5 and the draft's build
 * notes). {@code subcategory}, {@code combatLevel}, {@code questPoints}, and {@code gearTier} are
 * {@code null} when the entry doesn't specify one. {@code stage} (1 early game .. 4 endgame, spec
 * ruling 27) defaults to 2 when the bundled data predates it. {@code recommended} is the curated
 * "actually ready" profile layered on top of the hard {@code requirements} (e.g. a boss's
 * recommended combat stats and gear); {@code null} when the entry has none.
 */
@Value
public class MilestoneEntry
{
	String id;
	MilestoneCategory category;
	String subcategory;
	String name;
	String wikiTitle;
	int priority;
	String reason;
	List<String> unlocks;
	List<SkillReq> skills;
	List<String> quests;
	List<DiaryRef> diaries;
	Integer combatLevel;
	Integer questPoints;
	List<ItemReq> items;
	List<OwnedItem> ownedIf;
	Integer gearTier;
	List<String> sources;
	int stage;
	RecommendedProfile recommended;
}
