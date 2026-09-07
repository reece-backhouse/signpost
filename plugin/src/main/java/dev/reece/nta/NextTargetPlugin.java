package dev.reece.nta;

import com.google.gson.Gson;
import dev.reece.nta.engine.DiaryProgress;
import dev.reece.nta.engine.DiaryTierProgress;
import dev.reece.nta.engine.EngineRunner;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.CachedBank;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.snapshot.SnapshotCollector;
import dev.reece.nta.store.AccountData;
import dev.reece.nta.store.AccountStore;
import dev.reece.nta.ui.NextTargetPanel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
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

	private final Map<Skill, Integer> lastLevel = new EnumMap<>(Skill.class);

	private EngineRunner runner;
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

	@Override
	protected void startUp() throws Exception
	{
		runner = new EngineRunner();
		store = new AccountStore(new File(RuneLite.RUNELITE_DIR, "next-target").toPath(), gson);
		cachedBank = CachedBank.unknown();
		kb = null;
		lastLevel.clear();

		panel = new NextTargetPanel(this::requestSnapshot);
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
		logDiarySelfCheck(snapshot, loadedKb);
		runner.submit(() -> snapshot, result -> SwingUtilities.invokeLater(() -> panel.render(result)));
	}

	/**
	 * Cross-checks {@link DiaryProgress}'s bit-derived per-tier task counts against the game's own
	 * per-tier completed-task counter varbit, logging any tier where the bundled task->bit mapping
	 * disagrees with the game.
	 */
	private void logDiarySelfCheck(Snapshot snapshot, KnowledgeBase loadedKb)
	{
		Map<DiaryTier, DiaryTierProgress> progress = DiaryProgress.compute(snapshot, loadedKb);
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

		AccountData data = accountData;
		CachedBank bank = cachedBank;
		if (data != null)
		{
			data.setBank(new HashMap<>(bank.getItems()));
			data.setBankAsOf(bank.getAsOf());
			// Hand the engine thread its own copy: `data` keeps being mutated on the client
			// thread (e.g. by the next bank close) while this save is queued.
			AccountData toSave = data.copy();
			long hash = accountHash;
			runner.submit(() ->
			{
				store.save(hash, toSave);
				return null;
			}, ignored -> { });
		}

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
}
