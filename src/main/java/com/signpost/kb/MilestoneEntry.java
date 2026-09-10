package com.signpost.kb;

import com.signpost.snapshot.SlayerReward;
import java.util.List;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * One curated milestone (gear upgrade, unlock, prayer, spellbook, slayer target, or boss), from
 * the hand-curated {@code /kb/milestones.json}. Unlike quests/diaries this data isn't wiki-scraped
 * verbatim - it's a hand-drafted priority list. {@code subcategory}, {@code combatLevel},
 * {@code questPoints}, and {@code gearTier} are {@code null} when the entry doesn't specify one.
 * {@code stage} (1 early game .. 4 endgame) defaults to 2 when the bundled data predates it.
 * {@code recommended} is the curated
 * "actually ready" profile layered on top of the hard {@code requirements} (e.g. a boss's
 * recommended combat stats and gear); {@code null} when the entry has none. {@code obtainedFrom}
 * is the {@code id} of the boss milestone this entry's gear drops from, {@code null} when
 * it isn't a drop from a curated boss (e.g. a non-drop gear milestone, or the boss entry itself);
 * always resolves to a known milestone id (validated at {@link KnowledgeBase} load time).
 * {@code ownedIfMin} is how many distinct {@code ownedIf} items must be held before the
 * milestone counts as owned - 1 for a single item with variants, the set size for an outfit; {@code
 * speedsUp} is the skill a method-linked untradeable accelerates, {@code null} when none.
 * {@code prerequisite} is the id of a milestone that must be owned first (a portal nexus
 * needs its portal chamber); {@code null} when none, always a known, acyclic milestone id
 * (validated at load). {@code notes} are display-only lines, never evaluated - e.g. that a
 * {@link MilestoneCategory#POH} room is only ever finished by "Own it".
 * {@code bossRewards} names individual progression rewards for bosses, rather than generic loot
 * or collection-log goals; empty when omitted. Collection completion does not imply current ownership.
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
	String obtainedFrom;
	int ownedIfMin;
	Skill speedsUp;
	String prerequisite;
	List<String> notes;
	SlayerReward slayerReward;
	Integer slayerPoints;
	SlayerReward requiredSlayerUnlock;
	List<BossReward> bossRewards;
}
