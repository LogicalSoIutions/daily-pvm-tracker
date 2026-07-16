package com.pvmtracker;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
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
}
