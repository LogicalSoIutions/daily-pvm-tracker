package com.pvmtracker;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LootChatMatchWindowTest
{
	@Test
	public void keepsLootPendingThroughTheLastMatchingTick()
	{
		assertTrue(DailyPvmTrackerPlugin.isWithinLootChatMatchWindow(110, 100));
		assertFalse(DailyPvmTrackerPlugin.isWithinLootChatMatchWindow(111, 100));
	}

	@Test
	public void matchesEitherEventOrder()
	{
		assertTrue(DailyPvmTrackerPlugin.isWithinLootChatMatchWindow(100, 110));
	}

	@Test
	public void attachesDelayedKillCountChatToRecentLoot()
	{
		Instant lootTime = Instant.parse("2026-07-18T05:04:47Z");

		assertTrue(DailyPvmTrackerPlugin.isWithinRecentLootKcMatchWindow(
			lootTime, lootTime.plusSeconds(90)));
		assertFalse(DailyPvmTrackerPlugin.isWithinRecentLootKcMatchWindow(
			lootTime, lootTime.plusSeconds(91)));
	}
}
