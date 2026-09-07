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

		assertThrows(UnsupportedOperationException.class, () -> snapshot.getSkills().put(Skill.ATTACK, new SkillState(1, 0)));
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
