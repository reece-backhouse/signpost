package dev.reece.nta;

import com.google.gson.Gson;
import com.google.inject.Provides;
import dev.reece.nta.engine.BoostTable;
import dev.reece.nta.engine.DiaryTierProgress;
import dev.reece.nta.engine.Engine;
import dev.reece.nta.engine.EngineRunner;
import dev.reece.nta.engine.GapFingerprint;
import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.CachedBank;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.snapshot.SnapshotCollector;
import dev.reece.nta.store.AccountData;
import dev.reece.nta.store.AccountDataMutations;
import dev.reece.nta.store.AccountStore;
import dev.reece.nta.ui.GoalDetailPanel;
import dev.reece.nta.ui.NextTargetPanel;
import dev.reece.nta.ui.SuggestPanel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Next Target Advisor",
	description = "Suggests what to focus on next",
	tags = {"quest", "diary", "skilling"}
)
public class NextTargetPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Inject
	private NextTargetConfig config;

	private final Map<Skill, Integer> lastLevel = new EnumMap<>(Skill.class);

	private EngineRunner runner;
	private Engine engine;
	private AccountStore store;
	private NextTargetPanel panel;
	private NavigationButton navButton;

	private volatile CachedBank cachedBank = CachedBank.unknown();
	private volatile AccountData accountData;
	private volatile long accountHash;
	private volatile boolean accountLoaded;
	private volatile boolean firstTickPending;
	private volatile boolean snapshotRequested;
	private volatile KnowledgeBase kb;
	private volatile Snapshot cachedSnapshot;
	private volatile Advice cachedAdvice;

	@Provides
	NextTargetConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NextTargetConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		runner = new EngineRunner();
		engine = new Engine(new BoostTable());
		store = new AccountStore(new File(RuneLite.RUNELITE_DIR, "next-target").toPath(), gson);
		cachedBank = CachedBank.unknown();
		kb = null;
		cachedSnapshot = null;
		cachedAdvice = null;
		lastLevel.clear();

		SuggestPanel.Actions actions = new SuggestPanel.Actions(
			this::focus,
			this::snooze,
			this::ignore,
			this::pin,
			this::unpin,
			this::unsnooze,
			this::unignore,
			this::clearFocus);
		GoalDetailPanel.Actions detailActions = new GoalDetailPanel.Actions(this::focus, this::clearFocus);
		panel = new NextTargetPanel(this::requestSnapshot, actions, detailActions);
		BufferedImage icon = ImageUtil.loadImageResource(NextTargetPlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Next Target Advisor")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		runner.submit(() -> KnowledgeBase.load(gson), loaded ->
		{
			kb = loaded;
			SwingUtilities.invokeLater(() -> panel.showKbLoaded(loaded.getQuestsGeneratedAt(), loaded.getDiariesGeneratedAt()));
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		runner.shutdown();
		accountData = null;
		cachedBank = CachedBank.unknown();
		accountLoaded = false;
		firstTickPending = false;
		snapshotRequested = false;
		kb = null;
		engine = null;
		cachedSnapshot = null;
		cachedAdvice = null;
		lastLevel.clear();
	}

	/**
	 * Debounces a snapshot request to the next {@link GameTick}. Safe to call from any thread (the
	 * EDT via the Refresh button, or the client thread via event handlers).
	 */
	public void requestSnapshot()
	{
		snapshotRequested = true;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			long hash = client.getAccountHash();
			accountHash = hash;
			accountLoaded = false;
			runner.submit(() -> store.load(hash), data ->
			{
				accountData = data;
				cachedBank = data.toCachedBank();
				accountLoaded = true;
				firstTickPending = true;
			});
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			accountLoaded = false;
			firstTickPending = false;
			cachedSnapshot = null;
			cachedAdvice = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		KnowledgeBase loadedKb = kb;
		if (!accountLoaded || loadedKb == null || !(firstTickPending || snapshotRequested))
		{
			return;
		}

		firstTickPending = false;
		snapshotRequested = false;

		Snapshot snapshot = SnapshotCollector.collect(client, itemManager, cachedBank, loadedKb);
		cachedSnapshot = snapshot;
		Engine currentEngine = engine;
		AccountData data = accountData;
		AccountData dataForEngine = data != null ? data.copy() : AccountData.empty();
		runner.submit(() ->
		{
			long start = System.nanoTime();
			Advice advice = currentEngine.run(snapshot, loadedKb, dataForEngine, Instant.now());
			long ms = (System.nanoTime() - start) / 1_000_000;
			log.info("engine: {} goals evaluated in {} ms", advice.getStatuses().size(), ms);
			return advice;
		}, advice ->
		{
			cachedAdvice = advice;
			logDiarySelfCheck(advice.getDiaryProgress());
			maybeClearGoneFocus(advice, dataForEngine.getFocusGoalId());
			SwingUtilities.invokeLater(() -> panel.render(advice));
		});
	}

	/**
	 * If a goal is focused but {@link Advice#getFocus()} came back null (the focused goal is gone -
	 * e.g. completed), clears the focus once so the panel falls back to Suggest mode (ticket E7
	 * semantics). Guarded against looping: only fires when the focused id is also absent from
	 * {@code advice.getRanked()} and {@code advice.getLater()} - a focused goal that's merely hidden
	 * (snoozed/ignored) still has a status and so already gets a non-null {@code focus}, never
	 * reaching this check.
	 */
	private void maybeClearGoneFocus(Advice advice, String focusGoalId)
	{
		if (focusGoalId == null || advice.getFocus() != null)
		{
			return;
		}
		boolean stillPresent = advice.getRanked().stream().anyMatch(r -> r.getStatus().getGoal().getId().equals(focusGoalId))
			|| advice.getLater().stream().anyMatch(r -> r.getStatus().getGoal().getId().equals(focusGoalId));
		if (!stillPresent)
		{
			clearFocus();
		}
	}

	/**
	 * Cross-checks {@link dev.reece.nta.engine.DiaryProgress}'s bit-derived per-tier task counts
	 * against the game's own per-tier completed-task counter varbit, logging any tier where the
	 * bundled task->bit mapping disagrees with the game.
	 */
	private void logDiarySelfCheck(Map<DiaryTier, DiaryTierProgress> progress)
	{
		int mismatches = 0;
		for (Map.Entry<DiaryTier, DiaryTierProgress> entry : progress.entrySet())
		{
			DiaryTierProgress tierProgress = entry.getValue();
			if (tierProgress.isMismatch())
			{
				mismatches++;
				log.warn("diary bit map mismatch: {} kb={} game={}", entry.getKey(), tierProgress.getCompleted(), tierProgress.getGameCount());
			}
		}
		log.info("diary self-check: {} tiers, {} mismatches", progress.size(), mismatches);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK.getId())
		{
			return;
		}

		Map<Integer, Integer> items = new HashMap<>();
		for (Item item : event.getItemContainer().getItems())
		{
			if (SnapshotCollector.isRealItem(item))
			{
				items.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		cachedBank = new CachedBank(items, Instant.now(), true);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() != InterfaceID.BANKMAIN)
		{
			return;
		}

		CachedBank bank = cachedBank;
		Map<Integer, Integer> items = new HashMap<>(bank.getItems());
		Instant asOf = bank.getAsOf();
		mutateAccountData(data -> AccountDataMutations.bank(data, items, asOf));

		requestSnapshot();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int level = event.getLevel();
		Integer previous = lastLevel.put(skill, level);
		if (previous == null || previous != level)
		{
			requestSnapshot();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		KnowledgeBase loadedKb = kb;
		if (loadedKb == null)
		{
			return;
		}

		int varpId = event.getVarpId();
		int varbitId = event.getVarbitId();
		if (varpId == VarPlayer.QUEST_POINTS || loadedKb.diaryVarps().contains(varpId) || loadedKb.diaryVarbits().contains(varbitId))
		{
			requestSnapshot();
		}
	}

	// --- Panel actions (S4 ruling 5/19): invoked from the EDT by SuggestPanel's buttons. Each
	// queues its data transform onto the engine executor via mutateAccountData, which serialises
	// every accountData read-mutate-write (including the bank-close write in onWidgetClosed) on
	// that single thread, saves, and re-runs the engine against the CACHED snapshot (never
	// re-collects from Client); if no snapshot has been taken yet, only the save happens. ---

	/** D5: "Do this" - sets the goal as the active focus. */
	public void focus(String goalId)
	{
		mutateAccountData(data -> AccountDataMutations.focus(data, goalId));
	}

	/** E7: "Clear focus" - returns to Suggest mode. */
	public void clearFocus()
	{
		mutateAccountData(AccountDataMutations::clearFocus);
	}

	/** D6: "Not now" - snoozes the goal for {@link NextTargetConfig#snoozeDays()}, recording the goal's current gap fingerprint so a later change ends the snooze early. */
	public void snooze(String goalId)
	{
		GoalStatus status = statusFor(goalId);
		String fingerprint = status != null ? GapFingerprint.of(status) : "";
		Instant until = Instant.now().plus(config.snoozeDays(), ChronoUnit.DAYS);
		mutateAccountData(data -> AccountDataMutations.snooze(data, goalId, until, fingerprint));
	}

	/** D6: "Bring back" - ends a snooze early. */
	public void unsnooze(String goalId)
	{
		mutateAccountData(data -> AccountDataMutations.unsnooze(data, goalId));
	}

	/** D7: "Ignore" - hides the goal permanently until restored. */
	public void ignore(String goalId)
	{
		mutateAccountData(data -> AccountDataMutations.ignore(data, goalId));
	}

	/** D7: "Restore" - un-hides a previously ignored goal. */
	public void unignore(String goalId)
	{
		mutateAccountData(data -> AccountDataMutations.unignore(data, goalId));
	}

	/** D8: "Pin" - the goal stays at the top regardless of score. */
	public void pin(String goalId)
	{
		mutateAccountData(data -> AccountDataMutations.pin(data, goalId));
	}

	/** D8: "Unpin". */
	public void unpin(String goalId)
	{
		mutateAccountData(data -> AccountDataMutations.unpin(data, goalId));
	}

	private GoalStatus statusFor(String goalId)
	{
		Advice advice = cachedAdvice;
		if (advice == null)
		{
			return null;
		}
		for (GoalStatus status : advice.getStatuses())
		{
			if (status.getGoal().getId().equals(goalId))
			{
				return status;
			}
		}
		return null;
	}

	/**
	 * Queues {@code mutator} onto the engine executor: every call - from a panel action or from
	 * {@link #onWidgetClosed} - runs its read-mutate-write of {@link #accountData} there, so they
	 * serialise against each other and against the executor's other work instead of racing a
	 * client-thread or EDT write. Never touches {@link Client}.
	 */
	private void mutateAccountData(UnaryOperator<AccountData> mutator)
	{
		long hash = accountHash;
		Engine currentEngine = engine;

		runner.submit(() ->
		{
			AccountData current = accountData;
			AccountData updated = mutator.apply(current != null ? current : AccountData.empty());
			accountData = updated;
			store.save(hash, updated);

			Snapshot snapshot = cachedSnapshot;
			KnowledgeBase loadedKb = kb;
			return snapshot != null && loadedKb != null ? currentEngine.run(snapshot, loadedKb, updated, Instant.now()) : null;
		}, advice ->
		{
			if (advice != null)
			{
				cachedAdvice = advice;
				maybeClearGoneFocus(advice, accountData.getFocusGoalId());
				SwingUtilities.invokeLater(() -> panel.render(advice));
			}
		});
	}
}
