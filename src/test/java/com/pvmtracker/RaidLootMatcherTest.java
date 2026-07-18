package com.pvmtracker;

import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RaidLootMatcherTest
{
	@Test
	public void ignoresVerzikPatientRecordButNotTheRewardChest()
	{
		assertTrue(RaidLootMatcher.isNonRewardLoot("Theatre of Blood", 1, ItemID.TOB_BOOK_VERZIK));
		assertFalse(RaidLootMatcher.isNonRewardLoot("Theatre of Blood", 1, ItemID.MOLTEN_GLASS));
		assertFalse(RaidLootMatcher.isNonRewardLoot("Theatre of Blood", 2, ItemID.TOB_BOOK_VERZIK));
	}

	@Test
	public void keepsRaidCompletionUntilTheMatchingChestArrives()
	{
		assertTrue(DailyPvmTrackerPlugin.canMatchCompletionLoot(10_000, 100,
			"Theatre of Blood", "Theatre of Blood: Entry Mode"));
		assertFalse(DailyPvmTrackerPlugin.canMatchCompletionLoot(10_000, 100,
			"Vorkath", "Theatre of Blood: Entry Mode"));
		assertFalse(DailyPvmTrackerPlugin.canMatchCompletionLoot(10_000, 100,
			"Vorkath", "Vorkath"));
	}

	@Test
	public void keepsGauntletCompletionUntilTheRewardChestArrives()
	{
		assertTrue(DailyPvmTrackerPlugin.canMatchCompletionLoot(10_000, 100,
			"The Corrupted Gauntlet", "The Corrupted Gauntlet"));
		assertTrue(DailyPvmTrackerPlugin.canMatchCompletionLoot(10_000, 100,
			"The Gauntlet", "The Gauntlet"));
		assertFalse(DailyPvmTrackerPlugin.canMatchCompletionLoot(10_000, 100,
			"The Gauntlet", "The Corrupted Gauntlet"));
	}

	@Test
	public void attributesGenericToaLootToNearbyExpertCompletion()
	{
		TrackerData.RaidCompletion completion = new TrackerData.RaidCompletion();
		completion.source = "Tombs of Amascut: Expert Mode";
		completion.occurredAt = "2026-07-17T06:25:41.901931200Z";

		assertEquals("Tombs of Amascut: Expert Mode", RaidLootMatcher.resolveSource(
			"Tombs of Amascut", "2026-07-17T06:26:02.896577800Z",
			Collections.singletonList(completion)));
	}

	@Test
	public void doesNotAttributeGenericToaLootToAnOldExpertCompletion()
	{
		TrackerData.RaidCompletion completion = new TrackerData.RaidCompletion();
		completion.source = "Tombs of Amascut: Expert Mode";
		completion.occurredAt = "2026-07-17T06:20:00Z";

		assertEquals("Tombs of Amascut", RaidLootMatcher.resolveSource(
			"Tombs of Amascut", "2026-07-17T06:26:02Z", Collections.singletonList(completion)));
	}

	@Test
	public void attributesOnlyGenericRaidSourcesToNearbyVariants()
	{
		TrackerData.RaidCompletion challengeCompletion = completion(
			"Chambers of Xeric: Challenge Mode", "2026-07-17T06:25:41Z");
		TrackerData.RaidCompletion entryCompletion = completion(
			"Theatre of Blood: Entry Mode", "2026-07-17T06:25:41Z");

		assertEquals("Chambers of Xeric: Challenge Mode", RaidLootMatcher.resolveSource(
			"Chambers of Xeric", "2026-07-17T06:26:02Z", Collections.singletonList(challengeCompletion)));
		assertEquals("Theatre of Blood: Entry Mode", RaidLootMatcher.resolveSource(
			"Theatre of Blood", "2026-07-17T06:26:02Z", Collections.singletonList(entryCompletion)));
	}

	@Test
	public void preservesSpecificRaidSourceNearDifferentVariantCompletion()
	{
		TrackerData.RaidCompletion normalToa = completion(
			"Tombs of Amascut", "2026-07-17T06:25:41Z");
		TrackerData.RaidCompletion normalCox = completion(
			"Chambers of Xeric", "2026-07-17T06:25:41Z");
		TrackerData.RaidCompletion normalTob = completion(
			"Theatre of Blood", "2026-07-17T06:25:41Z");

		assertEquals("Tombs of Amascut: Expert Mode", RaidLootMatcher.resolveSource(
			"Tombs of Amascut: Expert Mode", "2026-07-17T06:26:02Z", Collections.singletonList(normalToa)));
		assertEquals("Chambers of Xeric: Challenge Mode", RaidLootMatcher.resolveSource(
			"Chambers of Xeric: Challenge Mode", "2026-07-17T06:26:02Z", Collections.singletonList(normalCox)));
		assertEquals("Theatre of Blood: Hard Mode", RaidLootMatcher.resolveSource(
			"Theatre of Blood: Hard Mode", "2026-07-17T06:26:02Z", Collections.singletonList(normalTob)));
	}

	@Test
	public void repairsPreviouslyStoredGenericToaLootAndInferredNormalKc()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Tombs of Amascut", 111);
		TrackerData.KcDay kcDay = new TrackerData.KcDay();
		kcDay.kills.put("Tombs of Amascut", 1);
		kcDay.startingKillCounts.put("Tombs of Amascut", 110);
		kcDay.endingKillCounts.put("Tombs of Amascut", 111);
		data.kcDays.put("2026-07-16", kcDay);
		TrackerData.LootDay lootDay = new TrackerData.LootDay();
		TrackerData.LootSource source = new TrackerData.LootSource();
		source.drops = 1;
		TrackerData.LootItem item = new TrackerData.LootItem(1615, "Dragonstone");
		item.quantity = 44;
		item.totalValue = 475_816L;
		source.items.put(item.itemId, item);
		lootDay.sources.put("Tombs of Amascut", source);
		data.lootDays.put("2026-07-16", lootDay);
		TrackerData.KillLogEntry kill = new TrackerData.KillLogEntry();
		kill.date = "2026-07-16";
		kill.occurredAt = "2026-07-17T06:26:02.896577800Z";
		kill.source = "Tombs of Amascut";
		kill.items.add(new TrackerData.KillLootItem(1615, "Dragonstone", 44, 475_816L));
		data.killLog.add(kill);
		TrackerData.RaidCompletion completion = new TrackerData.RaidCompletion();
		completion.source = "Tombs of Amascut: Expert Mode";
		completion.occurredAt = "2026-07-17T06:25:41.901931200Z";
		data.raidCompletions.add(completion);

		assertTrue(RaidLootMatcher.repairGenericRaidLootAttribution(data));
		assertEquals("Tombs of Amascut: Expert Mode", kill.source);
		assertFalse(lootDay.sources.containsKey("Tombs of Amascut"));
		assertEquals(1, lootDay.sources.get("Tombs of Amascut: Expert Mode").drops);
		assertFalse(kcDay.kills.containsKey("Tombs of Amascut"));
		assertEquals(Integer.valueOf(110), kcDay.endingKillCounts.get("Tombs of Amascut"));
		assertEquals(Integer.valueOf(110), data.lastKnownKillCounts.get("Tombs of Amascut"));
	}

	private static TrackerData.RaidCompletion completion(String source, String occurredAt)
	{
		TrackerData.RaidCompletion completion = new TrackerData.RaidCompletion();
		completion.source = source;
		completion.occurredAt = occurredAt;
		return completion;
	}
}
