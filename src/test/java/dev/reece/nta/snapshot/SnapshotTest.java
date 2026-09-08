package dev.reece.nta.snapshot;

import java.time.Instant;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotTest
{
	@Test
	void builderDefaultsToEmptyImmutableCollectionsAndFalseBank()
	{
		Snapshot snapshot = Snapshot.builder()
			.accountHash(123L)
			.accountType(AccountType.NORMAL)
			.build();

		assertEquals(123L, snapshot.getAccountHash());
		assertTrue(snapshot.getSkills().isEmpty());
		assertTrue(snapshot.getQuests().isEmpty());
		assertTrue(snapshot.getBank().isEmpty());
		assertTrue(snapshot.getInventory().isEmpty());
		assertTrue(snapshot.getEquipment().isEmpty());
		assertTrue(snapshot.getItemNames().isEmpty());
		assertTrue(snapshot.getDiaryTiers().isEmpty());
		assertTrue(snapshot.getDiaryVarps().isEmpty());
		assertTrue(snapshot.getKaramjaVarbits().isEmpty());
		assertTrue(snapshot.getDiaryCountVarbits().isEmpty());
		assertTrue(snapshot.getCombatAchievementTiers().isEmpty());
		assertFalse(snapshot.isBankKnown());
		assertNull(snapshot.getBankAsOf());
		assertTrue(snapshot.getGroupStorage().isEmpty());
		assertFalse(snapshot.isGroupStorageKnown());
		assertNull(snapshot.getGroupStorageAsOf());
		assertEquals(0, snapshot.getQuestPoints());
		assertEquals(0, snapshot.getKudos());

		assertThrows(UnsupportedOperationException.class, () -> snapshot.getSkills().put(Skill.ATTACK, new SkillState(1, 0)));
	}

	/** RL-003: group storage rides on the snapshot exactly like the bank, and the UI asks how much of a quantity it holds. */
	@Test
	void withGroupStorageCopiesTheCachedContainerAndShareCapsAtWhatItHolds()
	{
		Instant asOf = Instant.parse("2026-09-08T10:00:00Z");
		Snapshot snapshot = Snapshot.builder().accountHash(1L).accountType(AccountType.GROUP).build()
			.withGroupStorage(new CachedBank(Map.of(4151, 200), asOf, true));

		assertTrue(snapshot.isGroupStorageKnown());
		assertEquals(asOf, snapshot.getGroupStorageAsOf());
		assertEquals(Map.of(4151, 200), snapshot.getGroupStorage());
		assertEquals(200, snapshot.groupStorageShare(4151, 500));
		assertEquals(50, snapshot.groupStorageShare(4151, 50));
		assertEquals(0, snapshot.groupStorageShare(995, 50));
	}

	@Test
	void combatLevelIsDerivedFromSkillLevels()
	{
		// Fresh-account stats (every combat skill at 1, Hitpoints at its floor of 10) give combat
		// level 3 - matches net.runelite.api.Experience.getCombatLevel's own formula.
		Snapshot snapshot = Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.NORMAL)
			.skills(Map.of(
				Skill.ATTACK, new SkillState(1, 0),
				Skill.STRENGTH, new SkillState(1, 0),
				Skill.DEFENCE, new SkillState(1, 0),
				Skill.HITPOINTS, new SkillState(10, 1154),
				Skill.MAGIC, new SkillState(1, 0),
				Skill.RANGED, new SkillState(1, 0),
				Skill.PRAYER, new SkillState(1, 0)))
			.build();

		assertEquals(3, snapshot.combatLevel());
	}

	@Test
	void combatLevelForAMidLevelMeleeBuild()
	{
		Snapshot snapshot = Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.NORMAL)
			.skills(Map.of(
				Skill.ATTACK, new SkillState(60, 0),
				Skill.STRENGTH, new SkillState(60, 0),
				Skill.DEFENCE, new SkillState(60, 0),
				Skill.HITPOINTS, new SkillState(60, 0),
				Skill.MAGIC, new SkillState(1, 0),
				Skill.RANGED, new SkillState(1, 0),
				Skill.PRAYER, new SkillState(43, 0)))
			.build();

		assertEquals(74, snapshot.combatLevel());
	}

	@Test
	void withBankReturnsCopyCarryingBankFields()
	{
		Snapshot original = Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.IRONMAN)
			.build();

		Instant asOf = Instant.parse("2026-09-01T00:00:00Z");
		CachedBank bank = new CachedBank(Map.of(995, 1000), asOf, true);

		Snapshot updated = original.withBank(bank);

		assertTrue(original.getBank().isEmpty());
		assertFalse(original.isBankKnown());

		assertEquals(Map.of(995, 1000), updated.getBank());
		assertTrue(updated.isBankKnown());
		assertEquals(asOf, updated.getBankAsOf());
		assertEquals(original.getAccountHash(), updated.getAccountHash());
		assertEquals(original.getAccountType(), updated.getAccountType());
	}
}
