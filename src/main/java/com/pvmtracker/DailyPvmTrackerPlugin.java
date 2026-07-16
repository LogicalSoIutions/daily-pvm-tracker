package com.pvmtracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.Call;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Daily PvM Tracker",
	description = "Character-specific daily boss, raid, and loot history",
	tags = {"pvm", "boss", "raid", "daily", "loot", "tracker"}
)
@PluginDependency(LootTrackerPlugin.class)
public class DailyPvmTrackerPlugin extends Plugin
{
	private static final long INVALID_ACCOUNT = -1L;
	// Loot and completion chat do not arrive in a guaranteed order. Keep the loot event
	// through the following ticks so its exact in-game KC can be attached.
	private static final int LOOT_CHAT_MATCH_TICKS = 10;
	private static final Duration RECENT_LOOT_KC_MATCH_WINDOW = Duration.ofSeconds(15);
	private static final long PVM_HUB_UPLOAD_INTERVAL_MILLIS = 5L * 60L * 1000L;

	@Inject
	private Client client;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private HiscoreClient hiscoreClient;
	@Inject
	private ItemManager itemManager;
	@Inject
	private Gson gson;
	@Inject
	private DailyPvmTrackerConfig config;
	@Inject
	private OkHttpClient httpClient;
	@Inject
	private ClientThread clientThread;

	private final DailySummaryService summaryService = new DailySummaryService();
	private final KillCountService killCountService = new KillCountService();
	private final List<PendingLoot> pendingLoot = new ArrayList<>();
	private final HighAlchTracker highAlchTracker = new HighAlchTracker();
	private ScheduledExecutorService storageExecutor;
	private TrackerStore store;
	private DailyPvmTrackerPanel panel;
	private NavigationButton navigationButton;
	private volatile long activeAccount = INVALID_ACCOUNT;
	private long loadedAccount = INVALID_ACCOUNT;
	private TrackerData data;
	private boolean initializeOnTick;
	private volatile boolean geSyncOnTick;
	private boolean hiscoreRequestInFlight;
	private RecentCompletion recentChatCompletion;
	private volatile String trackingStatus = "Tracking locally.";
	private long lastUploadAttemptAt;
	private volatile Call uploadCall;
	private volatile ScheduledFuture<?> scheduledUpload;
	private boolean uploadPending;

	@Override
	protected void startUp()
	{
		storageExecutor = Executors.newSingleThreadScheduledExecutor(runnable ->
		{
			Thread thread = new Thread(runnable, "daily-pvm-tracker-storage");
			thread.setDaemon(true);
			return thread;
		});
		store = new TrackerStore(gson);
		panel = new DailyPvmTrackerPanel(new DailyPvmTrackerPanel.Actions()
		{
			@Override
			public void saveSplit(LocalDate date, String boss, long value)
			{
				updateManualAdjustment(date, boss, value);
			}

			@Override
			public void deleteBossLoot(LocalDate date, String boss)
			{
				DailyPvmTrackerPlugin.this.deleteBossLoot(date, boss);
			}

			@Override
			public void deleteDayGp(LocalDate date)
			{
				DailyPvmTrackerPlugin.this.deleteDayGp(date);
			}

			@Override
			public void setLootHidden(String boss, int itemId, boolean hidden)
			{
				DailyPvmTrackerPlugin.this.setLootHidden(boss, itemId, hidden);
			}

			@Override
			public void saveData(Path destination)
			{
				DailyPvmTrackerPlugin.this.saveData(destination);
			}

		});
		navigationButton = NavigationButton.builder()
			.tooltip("Daily PvM Tracker")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		initializeOnTick = client.getGameState() == GameState.LOGGED_IN;
		log.debug("Daily PvM Tracker started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigationButton);
		activeAccount = INVALID_ACCOUNT;
		cancelUpload();
		if (storageExecutor != null)
		{
			storageExecutor.shutdownNow();
		}
		data = null;
		loadedAccount = INVALID_ACCOUNT;
		initializeOnTick = false;
		geSyncOnTick = false;
		hiscoreRequestInFlight = false;
		pendingLoot.clear();
		highAlchTracker.clear();
		recentChatCompletion = null;
		trackingStatus = "Tracking locally.";
		lastUploadAttemptAt = 0L;
		uploadPending = false;
		log.debug("Daily PvM Tracker stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			initializeOnTick = true;
		}
		else if (!pendingLoot.isEmpty())
		{
			flushPendingLoot(Integer.MAX_VALUE);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		flushPendingLoot(client.getTickCount());
		if (initializeOnTick)
		{
			Player player = client.getLocalPlayer();
			long accountHash = client.getAccountHash();
			if (player != null && player.getName() != null && accountHash != INVALID_ACCOUNT)
			{
				initializeOnTick = false;
				activateCharacter(accountHash, player.getName(), HiscoreEndpoint.fromWorldTypes(client.getWorldType()));
			}
		}
		if (geSyncOnTick && activeAccount != INVALID_ACCOUNT)
		{
			geSyncOnTick = false;
			List<GeOfferUpdate> offers = new ArrayList<>();
			GrandExchangeOffer[] currentOffers = client.getGrandExchangeOffers();
			for (int slot = 0; slot < currentOffers.length; slot++)
			{
				offers.add(captureOffer(slot, currentOffers[slot]));
			}
			long accountHash = activeAccount;
			executeStorage(() -> processGeOffers(accountHash, offers));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (activeAccount == INVALID_ACCOUNT || (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION))
		{
			return;
		}
		KillCountMessageParser.Observation observation = KillCountMessageParser.parse(event.getMessage());
		if (observation == null)
		{
			return;
		}
		String source = observation.source;
		TrackerData.RaidCompletion raidCompletion = captureRaidCompletion(source, observation.killCount);
		int tick = client.getTickCount();
		for (int i = pendingLoot.size() - 1; i >= 0; i--)
		{
			PendingLoot pending = pendingLoot.get(i);
			if (!pending.completionHandled && isWithinLootChatMatchWindow(tick, pending.tick))
			{
				pending.source = source;
				pending.killCount = observation.killCount;
				pending.completionHandled = true;
				break;
			}
		}
		recentChatCompletion = new RecentCompletion(source, observation.killCount, tick);
		long accountHash = activeAccount;
		LocalDate date = LocalDate.now();
		executeStorage(() -> recordCompletion(accountHash, date, source, observation.killCount, raidCompletion));
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		if (activeAccount == INVALID_ACCOUNT)
		{
			return;
		}
		GeOfferUpdate update = captureOffer(event.getSlot(), event.getOffer());
		long accountHash = activeAccount;
		executeStorage(() -> processGeOffers(accountHash, java.util.Collections.singletonList(update)));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (activeAccount == INVALID_ACCOUNT || !"Cast".equals(event.getMenuOption())
			|| !event.getMenuTarget().contains("High Level Alchemy"))
		{
			return;
		}
		int itemId = itemManager.canonicalize(event.getItemId());
		ItemComposition composition = itemManager.getItemComposition(itemId);
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		highAlchTracker.begin(itemId, inventoryQuantity(inventory, itemId),
			inventoryQuantity(inventory, ItemID.COINS), composition.getHaPrice(), client.getTickCount());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (activeAccount == INVALID_ACCOUNT || event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		ItemContainer inventory = event.getItemContainer();
		int itemId = highAlchTracker.pendingItemId();
		if (itemId < 0)
		{
			return;
		}
		HighAlchTracker.Confirmation confirmation = highAlchTracker.observe(itemId,
			inventoryQuantity(inventory, itemId), inventoryQuantity(inventory, ItemID.COINS),
			client.getTickCount());
		if (confirmation != null)
		{
			long accountHash = activeAccount;
			executeStorage(() -> processHighAlch(accountHash, confirmation));
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.PLAYER || activeAccount == INVALID_ACCOUNT)
		{
			return;
		}
		List<CapturedItem> items = captureItems(event.getItems());
		if (LootTransformationService.isSupported(event.getName()))
		{
			String intermediateName = event.getName();
			List<LootTransformationService.ReplacementItem> replacements = new ArrayList<>();
			for (CapturedItem item : items)
			{
				replacements.add(new LootTransformationService.ReplacementItem(
					item.itemId, item.name, item.quantity, item.value));
			}
			long accountHash = activeAccount;
			executeStorage(() -> recordTransformation(accountHash, intermediateName, replacements));
			return;
		}
		String canonicalSource = BossRegistry.canonicalize(event.getName());
		if (canonicalSource == null)
		{
			return;
		}

		int tick = client.getTickCount();
		boolean completionHandled = recentChatCompletion != null
			&& isWithinLootChatMatchWindow(tick, recentChatCompletion.tick);
		String source = completionHandled ? recentChatCompletion.source : canonicalSource;
		pendingLoot.add(new PendingLoot(activeAccount, LocalDate.now(), source,
			Math.max(1, event.getAmount()), items, tick, completionHandled,
			completionHandled ? recentChatCompletion.killCount : null, Instant.now().toString()));
	}

	private List<CapturedItem> captureItems(java.util.Collection<ItemStack> stacks)
	{
		List<CapturedItem> items = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			int itemId = itemManager.canonicalize(stack.getId());
			ItemComposition composition = itemManager.getItemComposition(itemId);
			long quantity = stack.getQuantity();
			long unitValue = ItemValuation.unitValue(itemManager.getItemPrice(itemId),
				composition.getHaPrice(), config.highAlchPricesOnly());
			long value = unitValue * quantity;
			items.add(new CapturedItem(itemId, composition.getName(), quantity, value));
		}
		return items;
	}

	private long inventoryQuantity(ItemContainer inventory, int canonicalItemId)
	{
		if (inventory == null)
		{
			return 0;
		}
		long quantity = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() >= 0 && itemManager.canonicalize(item.getId()) == canonicalItemId)
			{
				quantity += item.getQuantity();
			}
		}
		return quantity;
	}

	private void flushPendingLoot(int currentTick)
	{
		Iterator<PendingLoot> iterator = pendingLoot.iterator();
		while (iterator.hasNext())
		{
			PendingLoot pending = iterator.next();
			if (currentTick != Integer.MAX_VALUE && isWithinLootChatMatchWindow(currentTick, pending.tick))
			{
				continue;
			}
			iterator.remove();
			executeStorage(() -> recordLoot(pending));
		}
		if (recentChatCompletion != null && currentTick != Integer.MAX_VALUE
			&& currentTick - recentChatCompletion.tick > LOOT_CHAT_MATCH_TICKS)
		{
			recentChatCompletion = null;
		}
	}

	static boolean isWithinLootChatMatchWindow(int firstTick, int secondTick)
	{
		return Math.abs(firstTick - secondTick) <= LOOT_CHAT_MATCH_TICKS;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("pvm-raid-daily-tracker".equals(event.getGroup()))
		{
			executeStorage(() ->
			{
				if ("uploadToPvmHub".equals(event.getKey()))
				{
					if (config.uploadToPvmHub())
					{
						maybeUploadSnapshot(activeAccount, true);
					}
					else
					{
						cancelUpload();
					}
				}
				refreshPanel();
			});
		}
	}

	private void activateCharacter(long accountHash, String name, HiscoreEndpoint endpoint)
	{
		activeAccount = accountHash;
		cancelUpload();
		lastUploadAttemptAt = 0L;
		trackingStatus = "Tracking locally. Checking hiscores for missed KC…";
		SwingUtilities.invokeLater(() -> panel.update(name, "Loading your private timeline…", new ArrayList<>()));
		executeStorage(() ->
		{
			try
			{
				TrackerData loaded = store.load(accountHash);
				if (activeAccount != accountHash)
				{
					return;
				}
				loadedAccount = accountHash;
				data = loaded;
				data.lastKnownName = name;
				backfillMissingValues(accountHash, LootValueBackfill.missingItemIds(data));
				geSyncOnTick = true;
				refreshPanel();
				maybeUploadSnapshot(accountHash, false);
				requestHiscoreReconciliation(accountHash, name, endpoint, LocalDate.now());
			}
			catch (IOException ex)
			{
				log.debug("Unable to load Daily PvM Tracker data", ex);
				showStatus(name, "Could not read this character's local history.");
			}
		});
	}

	private void backfillMissingValues(long accountHash, Set<Integer> itemIds)
	{
		if (itemIds.isEmpty())
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			Map<Integer, Long> unitValues = new LinkedHashMap<>();
			boolean highAlchOnly = config.highAlchPricesOnly();
			for (int itemId : itemIds)
			{
				ItemComposition composition = itemManager.getItemComposition(itemId);
				unitValues.put(itemId, ItemValuation.unitValue(itemManager.getItemPrice(itemId),
					composition.getHaPrice(), highAlchOnly));
			}
			executeStorage(() ->
			{
				if (loadedAccount == accountHash && activeAccount == accountHash && data != null
					&& LootValueBackfill.apply(data, unitValues))
				{
					saveAndRefresh(accountHash);
				}
			});
		});
	}

	private void requestHiscoreReconciliation(long accountHash, String name, HiscoreEndpoint endpoint, LocalDate date)
	{
		if (loadedAccount != accountHash || activeAccount != accountHash || data == null || hiscoreRequestInFlight)
		{
			return;
		}
		hiscoreRequestInFlight = true;
		hiscoreClient.lookupAsync(name, endpoint).whenComplete((result, error) ->
			executeStorage(() -> finishHiscoreReconciliation(accountHash, name, date, result, error)));
	}

	private void finishHiscoreReconciliation(long accountHash, String name, LocalDate date,
		HiscoreResult result, Throwable error)
	{
		hiscoreRequestInFlight = false;
		if (loadedAccount != accountHash || activeAccount != accountHash || data == null)
		{
			return;
		}
		if (error != null || result == null)
		{
			log.debug("Unable to retrieve hiscores for {}", name, error);
			trackingStatus = "Tracking locally. Hiscores were unavailable at login.";
			refreshPanel();
			return;
		}

		int recovered = killCountService.reconcile(data, date, extractKillCounts(result));
		data.lastKnownName = name;
		trackingStatus = recovered == 0
			? "Tracking locally. Hiscores checked at login."
			: "Tracking locally. Recovered " + recovered + " missed KC from hiscores.";
		saveAndRefresh(accountHash);
	}

	private Map<String, Integer> extractKillCounts(HiscoreResult result)
	{
		Map<String, Integer> killCounts = new LinkedHashMap<>();
		for (HiscoreSkill hiscoreSkill : HiscoreSkill.values())
		{
			if (hiscoreSkill.getType() != HiscoreSkillType.BOSS)
			{
				continue;
			}
			Skill skill = result.getSkill(hiscoreSkill);
			killCounts.put(hiscoreSkill.getName(), skill == null ? 0 : Math.max(0, skill.getLevel()));
		}
		return killCounts;
	}

	private TrackerData.RaidCompletion captureRaidCompletion(String source, int killCount)
	{
		if (!source.startsWith("Chambers of Xeric") && !source.startsWith("Theatre of Blood")
			&& !source.startsWith("Tombs of Amascut"))
		{
			return null;
		}
		TrackerData.RaidCompletion raid = new TrackerData.RaidCompletion();
		raid.id = UUID.randomUUID().toString();
		raid.date = LocalDate.now().toString();
		raid.occurredAt = Instant.now().toString();
		raid.source = source;
		raid.killCount = killCount;
		if (source.startsWith("Chambers of Xeric"))
		{
			raid.personalPoints = Math.max(0, client.getVarpValue(VarPlayerID.RAIDS_PLAYERSCORE));
			raid.lootPoints = raid.personalPoints;
			raid.teamPoints = Math.max(0, client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSCORE));
		}
		else if (source.startsWith("Tombs of Amascut"))
		{
			raid.personalPoints = Math.max(0, client.getVarpValue(VarPlayerID.TOA_PERSONAL_CONTRIBUTION));
			raid.lootPoints = Math.max(0, raid.personalPoints - 5_000);
			raid.raidLevel = Math.max(0, client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL));
		}
		int points = raid.lootPoints == null ? 0 : raid.lootPoints;
		int level = raid.raidLevel == null ? 0 : raid.raidLevel;
		RaidValueEstimator.Estimate estimate = RaidValueEstimator.estimate(source, points, level);
		raid.uniqueChance = estimate.uniqueChance;
		raid.expectedUniqueValue = estimate.expectedUniqueValue;
		raid.estimateBasis = estimate.basis;
		return raid;
	}

	private void recordCompletion(long accountHash, LocalDate date, String sourceName, int exactKillCount,
		TrackerData.RaidCompletion raidCompletion)
	{
		if (loadedAccount != accountHash || activeAccount != accountHash || data == null)
		{
			return;
		}
		boolean completionRecorded = killCountService.recordCompletion(data, date, sourceName, exactKillCount);
		boolean changed = completionRecorded;
		changed |= attachKillCountToRecentLoot(data, sourceName, exactKillCount);
		if (changed)
		{
			if (raidCompletion != null && completionRecorded)
			{
				data.raidCompletions.add(raidCompletion);
			}
			saveAndRefresh(accountHash);
		}
	}

	private boolean attachKillCountToRecentLoot(TrackerData trackerData, String sourceName, int exactKillCount)
	{
		Instant now = Instant.now();
		for (int i = trackerData.killLog.size() - 1; i >= 0; i--)
		{
			TrackerData.KillLogEntry kill = trackerData.killLog.get(i);
			if (kill.killCount != null || !sourceName.equals(kill.source) || kill.occurredAt == null)
			{
				continue;
			}
			try
			{
				if (Duration.between(Instant.parse(kill.occurredAt), now).abs().compareTo(RECENT_LOOT_KC_MATCH_WINDOW) <= 0)
				{
					kill.killCount = exactKillCount;
					return true;
				}
			}
			catch (java.time.format.DateTimeParseException ignored)
			{
				// Older malformed timestamps cannot be safely attributed to this KC.
			}
		}
		return false;
	}

	private void recordLoot(PendingLoot pending)
	{
		if (loadedAccount != pending.accountHash || activeAccount != pending.accountHash || data == null)
		{
			return;
		}
		TrackerData.LootDay day = data.lootDays.computeIfAbsent(pending.date.toString(), ignored -> new TrackerData.LootDay());
		TrackerData.LootSource source = day.sources.computeIfAbsent(pending.source, ignored -> new TrackerData.LootSource());
		source.drops += pending.drops;
		for (CapturedItem item : pending.items)
		{
			TrackerData.LootItem aggregate = source.items.computeIfAbsent(item.itemId,
				ignored -> new TrackerData.LootItem(item.itemId, item.name));
			aggregate.quantity += item.quantity;
			aggregate.totalValue += item.value;
			if (!data.isLootHidden(pending.source, item.itemId))
			{
				source.totalValue += item.value;
			}
		}
		TrackerData.KillLogEntry kill = new TrackerData.KillLogEntry();
		kill.id = UUID.randomUUID().toString();
		kill.date = pending.date.toString();
		kill.occurredAt = pending.occurredAt;
		kill.source = pending.source;
		kill.killCount = pending.killCount;
		kill.kills = pending.drops;
		for (CapturedItem item : pending.items)
		{
			kill.items.add(new TrackerData.KillLootItem(item.itemId, item.name, item.quantity, item.value));
			if (!data.isLootHidden(pending.source, item.itemId))
			{
				kill.totalValue += item.value;
			}
		}
		data.killLog.add(kill);
		if (!pending.completionHandled)
		{
			killCountService.recordLootCompletionIfMissing(data, pending.date, pending.source, source.drops);
		}
		saveAndRefresh(pending.accountHash);
	}

	private void recordTransformation(long accountHash, String intermediateName,
		List<LootTransformationService.ReplacementItem> replacements)
	{
		if (loadedAccount != accountHash || activeAccount != accountHash || data == null)
		{
			return;
		}
		if (LootTransformationService.replace(data, intermediateName, replacements))
		{
			saveAndRefresh(accountHash);
		}
	}

	private void updateManualAdjustment(LocalDate date, String boss, long value)
	{
		long accountHash = activeAccount;
		executeStorage(() ->
		{
			if (loadedAccount != accountHash || activeAccount != accountHash || data == null)
			{
				return;
			}
			if (TrackerDataEditor.setSplit(data, date, boss, value))
			{
				saveAndRefresh(accountHash);
			}
		});
	}

	private void deleteBossLoot(LocalDate date, String boss)
	{
		long accountHash = activeAccount;
		executeStorage(() ->
		{
			if (loadedAccount == accountHash && activeAccount == accountHash && data != null
				&& TrackerDataEditor.deleteBossLoot(data, date, boss))
			{
				saveAndRefresh(accountHash);
			}
		});
	}

	private void deleteDayGp(LocalDate date)
	{
		long accountHash = activeAccount;
		executeStorage(() ->
		{
			if (loadedAccount != accountHash || activeAccount != accountHash || data == null)
			{
				return;
			}
			if (TrackerDataEditor.deleteDayGp(data, date))
			{
				saveAndRefresh(accountHash);
			}
		});
	}

	private void setLootHidden(String boss, int itemId, boolean hidden)
	{
		long accountHash = activeAccount;
		executeStorage(() ->
		{
			if (loadedAccount == accountHash && activeAccount == accountHash && data != null
				&& TrackerDataEditor.setLootHidden(data, boss, itemId, hidden))
			{
				saveAndRefresh(accountHash);
			}
		});
	}

	private void saveData(Path destination)
	{
		long accountHash = activeAccount;
		executeStorage(() ->
		{
			boolean successful = false;
			if (loadedAccount == accountHash && activeAccount == accountHash && data != null)
			{
				try
				{
					store.export(destination, data);
					successful = true;
				}
				catch (IOException ex)
				{
					log.debug("Unable to export Daily PvM Tracker data", ex);
				}
			}
			boolean result = successful;
			SwingUtilities.invokeLater(() -> panel.showSaveResult(destination, result));
		});
	}

	private GeOfferUpdate captureOffer(int slot, GrandExchangeOffer offer)
	{
		int itemId = offer.getState() == GrandExchangeOfferState.EMPTY
			? -1 : itemManager.canonicalize(offer.getItemId());
		return new GeOfferUpdate(slot, itemId, offer.getTotalQuantity(), offer.getPrice(),
			offer.getQuantitySold(), offer.getSpent(), offer.getState());
	}

	private void processGeOffers(long accountHash, List<GeOfferUpdate> updates)
	{
		if (loadedAccount != accountHash || activeAccount != accountHash || data == null)
		{
			return;
		}
		boolean changed = false;
		boolean confirmedValueChanged = false;
		for (GeOfferUpdate update : updates)
		{
			if (!update.isSell())
			{
				changed |= data.geOffers.remove(update.slot) != null;
				continue;
			}

			TrackerData.GeOffer previous = data.geOffers.get(update.slot);
			if (previous != null && update.sameOffer(previous))
			{
				int quantityDelta = update.quantitySold - previous.quantitySold;
				long proceedsDelta = (long) update.spent - previous.spent;
				if (quantityDelta > 0 && proceedsDelta > 0)
				{
					GeSaleMatcher.MatchResult match = GeSaleMatcher.match(data, update.itemId, quantityDelta, proceedsDelta);
					confirmedValueChanged |= match.value > 0;
				}
			}
			data.geOffers.put(update.slot, update.toStoredOffer());
			changed = true;
		}
		if (!changed)
		{
			return;
		}
		try
		{
			store.save(accountHash, data);
		}
		catch (IOException ex)
		{
			log.debug("Unable to save GE confirmation state", ex);
		}
		if (confirmedValueChanged)
		{
			refreshPanel();
		}
		maybeUploadSnapshot(accountHash, false);
	}

	private void processHighAlch(long accountHash, HighAlchTracker.Confirmation confirmation)
	{
		if (loadedAccount != accountHash || activeAccount != accountHash || data == null)
		{
			return;
		}
		GeSaleMatcher.MatchResult match = GeSaleMatcher.match(data, confirmation.itemId, 1,
			confirmation.value, GeSaleMatcher.ConfirmationMethod.HIGH_ALCHEMY);
		if (match.quantity > 0)
		{
			saveAndRefresh(accountHash);
		}
	}

	private void saveAndRefresh(long accountHash)
	{
		boolean saved = false;
		try
		{
			store.save(accountHash, data);
			saved = true;
		}
		catch (IOException ex)
		{
			log.debug("Unable to save Daily PvM Tracker data", ex);
		}
		refreshPanel();
		if (saved)
		{
			maybeUploadSnapshot(accountHash, false);
		}
	}

	private void maybeUploadSnapshot(long accountHash, boolean force)
	{
		if (!config.uploadToPvmHub() || data == null || accountHash == INVALID_ACCOUNT
			|| loadedAccount != accountHash || activeAccount != accountHash)
		{
			return;
		}
		uploadPending = true;
		if (uploadCall != null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (!force && now - lastUploadAttemptAt < PVM_HUB_UPLOAD_INTERVAL_MILLIS)
		{
			schedulePendingUpload(accountHash, PVM_HUB_UPLOAD_INTERVAL_MILLIS - (now - lastUploadAttemptAt));
			return;
		}
		cancelScheduledUpload();
		uploadPending = false;
		lastUploadAttemptAt = now;
		uploadCall = PvmHubUploader.uploadSnapshot(data, gson, httpClient, (completedCall, successful, responseCode) ->
			executeStorage(() ->
			{
				if (uploadCall != completedCall)
				{
					return;
				}
				uploadCall = null;
				if (successful)
				{
					log.debug("PvM Hub snapshot upload completed");
				}
				else if (responseCode == -1 || responseCode == 429 || responseCode >= 500)
				{
					uploadPending = true;
				}
				if (uploadPending)
				{
					maybeUploadSnapshot(accountHash, false);
				}
			}));
	}

	private void schedulePendingUpload(long accountHash, long delayMillis)
	{
		if (scheduledUpload != null && !scheduledUpload.isDone())
		{
			return;
		}
		ScheduledExecutorService executor = storageExecutor;
		if (executor != null && !executor.isShutdown())
		{
			scheduledUpload = executor.schedule(() ->
			{
				scheduledUpload = null;
				maybeUploadSnapshot(accountHash, false);
			}, Math.max(1L, delayMillis), TimeUnit.MILLISECONDS);
		}
	}

	private void cancelScheduledUpload()
	{
		ScheduledFuture<?> future = scheduledUpload;
		scheduledUpload = null;
		if (future != null)
		{
			future.cancel(false);
		}
	}

	private void cancelUpload()
	{
		cancelScheduledUpload();
		uploadPending = false;
		Call call = uploadCall;
		uploadCall = null;
		if (call != null)
		{
			call.cancel();
		}
	}

	private void refreshPanel()
	{
		if (data == null || loadedAccount != activeAccount)
		{
			return;
		}
		List<DailySummary> summaries = summaryService.build(data, LocalDate.now(), config.showEmptyDays());
		if (summaries.size() > 45)
		{
			summaries = new ArrayList<>(summaries.subList(0, 45));
		}
		String name = data.lastKnownName;
		List<DailySummary> panelSummaries = summaries;
		String status = trackingStatus;
		SwingUtilities.invokeLater(() -> panel.update(name, status, panelSummaries));
	}

	private void showStatus(String name, String status)
	{
		List<DailySummary> summaries = data == null ? new ArrayList<>()
			: summaryService.build(data, LocalDate.now(), config.showEmptyDays());
		SwingUtilities.invokeLater(() -> panel.update(name, status, summaries));
	}

	private void executeStorage(Runnable task)
	{
		ScheduledExecutorService executor = storageExecutor;
		if (executor != null && !executor.isShutdown())
		{
			executor.execute(task);
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(246, 194, 76));
		g.fillRoundRect(1, 1, 14, 14, 5, 5);
		g.setColor(new Color(25, 31, 43));
		g.fillOval(4, 4, 8, 8);
		g.setColor(new Color(83, 214, 167));
		g.fillOval(6, 6, 4, 4);
		g.dispose();
		return image;
	}

	@Provides
	DailyPvmTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DailyPvmTrackerConfig.class);
	}

	private static final class RecentCompletion
	{
		private final String source;
		private final Integer killCount;
		private final int tick;

		private RecentCompletion(String source, Integer killCount, int tick)
		{
			this.source = source;
			this.killCount = killCount;
			this.tick = tick;
		}
	}

	private static final class PendingLoot
	{
		private final long accountHash;
		private final LocalDate date;
		private String source;
		private final int drops;
		private final List<CapturedItem> items;
		private final int tick;
		private Integer killCount;
		private final String occurredAt;
		private boolean completionHandled;

		private PendingLoot(long accountHash, LocalDate date, String source, int drops,
			List<CapturedItem> items, int tick, boolean completionHandled, Integer killCount,
			String occurredAt)
		{
			this.accountHash = accountHash;
			this.date = date;
			this.source = source;
			this.drops = drops;
			this.items = items;
			this.tick = tick;
			this.completionHandled = completionHandled;
			this.killCount = killCount;
			this.occurredAt = occurredAt;
		}
	}

	private static final class CapturedItem
	{
		private final int itemId;
		private final String name;
		private final long quantity;
		private final long value;

		private CapturedItem(int itemId, String name, long quantity, long value)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.value = value;
		}
	}

	private static final class GeOfferUpdate
	{
		private final int slot;
		private final int itemId;
		private final int totalQuantity;
		private final int price;
		private final int quantitySold;
		private final int spent;
		private final GrandExchangeOfferState state;

		private GeOfferUpdate(int slot, int itemId, int totalQuantity, int price,
			int quantitySold, int spent, GrandExchangeOfferState state)
		{
			this.slot = slot;
			this.itemId = itemId;
			this.totalQuantity = totalQuantity;
			this.price = price;
			this.quantitySold = quantitySold;
			this.spent = spent;
			this.state = state;
		}

		private boolean isSell()
		{
			return state == GrandExchangeOfferState.SELLING
				|| state == GrandExchangeOfferState.SOLD
				|| state == GrandExchangeOfferState.CANCELLED_SELL;
		}

		private boolean sameOffer(TrackerData.GeOffer previous)
		{
			return itemId == previous.itemId && totalQuantity == previous.totalQuantity && price == previous.price;
		}

		private TrackerData.GeOffer toStoredOffer()
		{
			return new TrackerData.GeOffer(itemId, totalQuantity, price, quantitySold, spent, state.name());
		}
	}
}
