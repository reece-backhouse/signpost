package dev.reece.nta.kb;

import com.google.gson.Gson;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task 34: {@code methods.json}/{@code materials.json} loading into {@link MethodEntry}/{@link MaterialEntry}. */
class KnowledgeBaseMethodsTest
{
	private static final String EMPTY_QUESTS_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"quests\":[]}";
	private static final String EMPTY_DIARIES_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"diaries\":[]}";
	private static final String EMPTY_MILESTONES_JSON = "{\"version\":1,\"milestones\":[]}";
	private static final String EMPTY_PRIORITIES_JSON = "{\"version\":1,\"overrides\":{}}";
	private static final String EMPTY_MATERIALS_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"materials\":[]}";

	@Test
	void bundledMethodsAndMaterialsLoad()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		assertFalse(kb.getMethods().isEmpty());
		assertFalse(kb.getMaterials().isEmpty());
		assertFalse(kb.methodsFor(Skill.HERBLORE).isEmpty());
	}

	@Test
	void prayerPotionThreeResolvesWithTwoMaterialIds()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		MethodEntry prayerPot = kb.methodsFor(Skill.HERBLORE).stream()
			.filter(m -> m.getName().equals("Prayer potion(3)"))
			.findFirst()
			.orElseThrow();

		assertTrue(prayerPot.isUsable());
		assertEquals(2, prayerPot.getMaterials().size());
		for (ItemQuantity material : prayerPot.getMaterials())
		{
			assertNotNull(material.getId(), material.getName() + " should have resolved an id");
		}
		assertEquals(1, prayerPot.getOutputs().size());
		assertNotNull(kb.materialById(prayerPot.getMaterials().get(0).getId()));
	}

	@Test
	void ranarrPotionUnfIsFlaggedIntermediateWithZeroXp()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		MethodEntry unf = kb.methodsFor(Skill.HERBLORE).stream()
			.filter(m -> m.getName().equals("Ranarr potion (unf)"))
			.findFirst()
			.orElseThrow();

		assertTrue(unf.isIntermediate());
		assertEquals(0, unf.getXpPerAction());
	}

	@Test
	void materialByNameAndByIdResolveTheSameEntry()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		MaterialEntry byName = kb.materialByName("Ranarr weed");
		assertNotNull(byName);
		assertNotNull(byName.getId());
		assertEquals(byName, kb.materialById(byName.getId()));
		assertFalse(byName.getSources().isEmpty());
		assertEquals("https://oldschool.runescape.wiki/w/Ranarr_weed", byName.getWikiUrl());
	}

	@Test
	void materialWithoutAWikiUrlInJsonDerivesOneFromItsName()
	{
		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON,
			EMPTY_PRIORITIES_JSON, "{\"version\":1,\"generatedAt\":\"x\",\"methods\":[]}", materialsJsonWithRanarrWeed());

		assertEquals("https://oldschool.runescape.wiki/w/Ranarr_weed", kb.materialByName("Ranarr weed").getWikiUrl());
	}

	@Test
	void methodWithUnresolvedMaterialNameIsKeptButFlaggedUnusable()
	{
		String methodsJson = "{\"version\":1,\"generatedAt\":\"x\",\"methods\":[{\"skill\":\"Herblore\",\"name\":\"Test Method\","
			+ "\"title\":\"Test Method\",\"levelReq\":1,\"xpPerAction\":10,"
			+ "\"materials\":[{\"name\":\"Not A Real Material\",\"quantity\":1}],\"outputs\":[],\"types\":[],\"members\":false}]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON,
			EMPTY_PRIORITIES_JSON, methodsJson, EMPTY_MATERIALS_JSON);

		MethodEntry entry = kb.methodsFor(Skill.HERBLORE).get(0);
		assertFalse(entry.isUsable());
		assertNotNull(entry.getMaterials().get(0).getName());
		assertEquals(null, entry.getMaterials().get(0).getId());
	}

	@Test
	void methodWithAnUnresolvedOutputButResolvableMaterialsIsUsable()
	{
		String methodsJson = "{\"version\":1,\"generatedAt\":\"x\",\"methods\":[{\"skill\":\"Herblore\",\"name\":\"Test Method\","
			+ "\"title\":\"Test Method\",\"levelReq\":1,\"xpPerAction\":10,\"materials\":[{\"name\":\"Ranarr weed\",\"quantity\":1}],"
			+ "\"outputs\":[{\"name\":\"Not A Real Output\",\"quantity\":1}],\"types\":[],\"members\":false}]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON,
			EMPTY_PRIORITIES_JSON, methodsJson, materialsJsonWithRanarrWeed());

		MethodEntry entry = kb.methodsFor(Skill.HERBLORE).get(0);
		assertTrue(entry.isUsable(), "an unresolved output must not block usability");
		assertNotNull(entry.getMaterials().get(0).getId());
		assertEquals(null, entry.getOutputs().get(0).getId());
	}

	@Test
	void outputNamedLikeOneOfTheMethodsOwnMaterialsIsDroppedAtLoad()
	{
		// A stale methods.json (pre C1 fix) listed "Dragon bones" as both material and output; a
		// method must never refill the simulated bank with what it just consumed.
		String methodsJson = "{\"version\":1,\"generatedAt\":\"x\",\"methods\":[{\"skill\":\"Herblore\",\"name\":\"Ranarr weed\","
			+ "\"title\":\"Ranarr weed\",\"levelReq\":1,\"xpPerAction\":10,\"materials\":[{\"name\":\"Ranarr weed\",\"quantity\":1}],"
			+ "\"outputs\":[{\"name\":\"Ranarr weed\",\"quantity\":1},{\"name\":\"Something else\",\"quantity\":1}],\"types\":[],\"members\":false}]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON,
			EMPTY_PRIORITIES_JSON, methodsJson, materialsJsonWithRanarrWeed());

		MethodEntry entry = kb.methodsFor(Skill.HERBLORE).get(0);
		assertEquals(1, entry.getOutputs().size(), "the self-consuming output must be dropped, the other kept");
		assertEquals("Something else", entry.getOutputs().get(0).getName());
		assertTrue(entry.isUsable());
	}

	@Test
	void noBundledMethodOutputsAnItemItConsumes()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		for (MethodEntry method : kb.getMethods())
		{
			for (ItemQuantity output : method.getOutputs())
			{
				for (ItemQuantity material : method.getMaterials())
				{
					assertFalse(material.getName().equals(output.getName()),
						method.getSkill() + " \"" + method.getTitle() + "\" outputs the \"" + output.getName() + "\" it consumes");
				}
			}
		}
	}

	@Test
	void bundledMagicMethodsAreMostlyUsableDespiteGenericOutputs()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		long usableTrainable = kb.methodsFor(Skill.MAGIC).stream()
			.filter(m -> m.isUsable() && !m.isIntermediate() && m.getXpPerAction() > 0)
			.count();

		assertTrue(usableTrainable >= 50, "expected at least 50 usable non-intermediate Magic methods, got " + usableTrainable);
	}

	private static String materialsJsonWithRanarrWeed()
	{
		return "{\"version\":1,\"generatedAt\":\"x\",\"materials\":[{\"name\":\"Ranarr weed\",\"id\":257,\"generic\":false,\"sources\":[]}]}";
	}

	@Test
	void methodWithLevelReqOutsideOneToNinetyNineFailsLoudlyNamingTheMethod()
	{
		String methodsJson = "{\"version\":1,\"generatedAt\":\"x\",\"methods\":[{\"skill\":\"Herblore\",\"name\":\"Bad Method\","
			+ "\"title\":\"Bad Method\",\"levelReq\":100,\"xpPerAction\":10,\"materials\":[],\"outputs\":[],\"types\":[],\"members\":false}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON,
			EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON, methodsJson, EMPTY_MATERIALS_JSON));

		assertTrue(e.getMessage().contains("Bad Method"), e.getMessage());
	}

	@Test
	void methodWithUnknownSkillFailsLoudlyNamingTheMethod()
	{
		String methodsJson = "{\"version\":1,\"generatedAt\":\"x\",\"methods\":[{\"skill\":\"Not A Skill\",\"name\":\"Bad Skill Method\","
			+ "\"title\":\"Bad Skill Method\",\"levelReq\":1,\"xpPerAction\":10,\"materials\":[],\"outputs\":[],\"types\":[],\"members\":false}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON,
			EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON, methodsJson, EMPTY_MATERIALS_JSON));

		assertTrue(e.getMessage().contains("Bad Skill Method"), e.getMessage());
	}
}
