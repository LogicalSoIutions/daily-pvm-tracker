package com.pvmtracker;

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
}
