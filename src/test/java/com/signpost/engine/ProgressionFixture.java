package com.signpost.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.signpost.snapshot.AccountType;
import com.signpost.snapshot.BossProgress;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.SkillState;
import com.signpost.snapshot.Snapshot;
import com.signpost.store.AccountData;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Progression scenario with explicit non-gear assumptions to isolate equipment recommendations. */
final class ProgressionFixture
{
	private ProgressionFixture() {}

	static Snapshot snapshot()
	{
		JsonObject data;
		try (var stream = ProgressionFixture.class.getResourceAsStream("/fixtures/progression-account.json"))
		{
			if (stream == null) throw new IllegalStateException("Missing progression account fixture");
			data = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
		}
		catch (java.io.IOException exception)
		{
			throw new java.io.UncheckedIOException(exception);
		}
		Map<Skill, SkillState> skills = new EnumMap<>(Skill.class);
		data.getAsJsonObject("skills").entrySet().forEach(e -> {
			int level = e.getValue().getAsInt();
			skills.put(Skill.valueOf(e.getKey()), new SkillState(level, Experience.getXpForLevel(level)));
		});
		Map<Integer, Integer> bank = new HashMap<>();
		data.getAsJsonObject("items").entrySet().forEach(e -> bank.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt()));
		Set<Integer> absent = new HashSet<>();
		data.getAsJsonArray("confirmedAbsentItems").forEach(e -> absent.add(e.getAsInt()));
		Map<String, BossProgress> progress = new HashMap<>();
		data.getAsJsonArray("greenLoggedBosses").forEach(e -> progress.put(e.getAsString(), new BossProgress(true, false, List.of())));
		Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values()) quests.put(quest, QuestState.FINISHED);
		Map<DiaryTier, Boolean> diaries = new EnumMap<>(DiaryTier.class);
		for (DiaryTier tier : DiaryTier.values()) diaries.put(tier, !tier.name().endsWith("_ELITE") || tier == DiaryTier.KOUREND_ELITE);
		return Snapshot.builder().accountType(AccountType.GROUP).skills(skills).quests(quests).questPoints(400).kudos(230)
			.bank(bank).bankKnown(data.get("bankComplete").getAsBoolean()).confirmedAbsentItems(absent)
			.groupStorageEnabled(true).diaryTiers(diaries).combatAchievementTiers(Map.of(1, true, 2, true))
			.bossProgress(progress).build();
	}

	static AccountData preferences()
	{
		AccountData result = AccountData.empty();
		result.getOwnedManually().addAll(List.of("poh:house", "poh:costume-room", "poh:oak-armour-case", "poh:portal-chamber",
			"poh:superior-garden", "poh:restoration-pool", "poh:portal-nexus", "poh:achievement-gallery", "poh:basic-jewellery-box",
			"poh:ornate-jewellery-box", "poh:spirit-tree", "poh:fairy-ring"));
		return result;
	}
}
