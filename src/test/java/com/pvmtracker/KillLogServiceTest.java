package com.pvmtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class KillLogServiceTest
{
	private final KillLogService service = new KillLogService();

	@Test
	public void completionCreatesDurableKillBeforeLootExists()
	{
		TrackerData data = new TrackerData();

		assertTrue(service.recordCompletion(data, "2026-07-19", "2026-07-19T10:00:00Z",
			"Maggot King", 807));

		assertEquals(1, data.killLog.size());
		assertEquals(Integer.valueOf(807), data.killLog.get(0).killCount);
		assertFalse(data.killLog.get(0).hasCapturedLoot());
	}

	@Test
	public void delayedLootFindsTheOriginalCompletionWithoutATimeLimit()
	{
		TrackerData data = new TrackerData();
		service.recordCompletion(data, "2026-07-19", "2026-07-19T10:00:00Z", "Maggot King", 807);

		TrackerData.KillLogEntry match = service.claimOldestUnlootedCompletion(data, "Maggot King");

		assertSame(data.killLog.get(0), match);
		assertEquals("2026-07-19T10:00:00Z", match.occurredAt);
		assertTrue(match.hasCapturedLoot());
	}

	@Test
	public void delayedRaidLootMatchesItsSpecificCompletionVariant()
	{
		TrackerData data = new TrackerData();
		service.recordCompletion(data, "2026-07-19", "2026-07-19T10:00:00Z",
			"Tombs of Amascut: Expert Mode", 42);

		assertSame(data.killLog.get(0),
			service.claimOldestUnlootedCompletion(data, "Tombs of Amascut"));
	}

	@Test
	public void multipleDelayedCompletionsAreMatchedOldestFirst()
	{
		TrackerData data = new TrackerData();
		service.recordCompletion(data, "2026-07-19", "2026-07-19T10:00:00Z", "Maggot King", 807);
		service.recordCompletion(data, "2026-07-19", "2026-07-19T10:05:00Z", "Maggot King", 808);

		TrackerData.KillLogEntry first = service.claimOldestUnlootedCompletion(data, "Maggot King");

		assertEquals(Integer.valueOf(807), first.killCount);
		assertEquals(Integer.valueOf(808),
			service.claimOldestUnlootedCompletion(data, "Maggot King").killCount);
	}

	@Test
	public void lootJustBeforeCompletionIsReusedInsteadOfDuplicated()
	{
		TrackerData data = new TrackerData();
		TrackerData.KillLogEntry loot = service.createLootOnly("2026-07-19",
			"2026-07-19T10:00:00Z", "Vorkath", null, 1);
		data.killLog.add(loot);

		assertTrue(service.recordCompletion(data, "2026-07-19", "2026-07-19T10:00:05Z", "Vorkath", 100));
		assertEquals(1, data.killLog.size());
		assertEquals(Integer.valueOf(100), loot.killCount);
	}

	@Test
	public void oldLootOnlyRecordIsNotClaimedByANewCompletion()
	{
		TrackerData data = new TrackerData();
		TrackerData.KillLogEntry loot = service.createLootOnly("2026-07-19",
			"2026-07-19T10:00:00Z", "Vorkath", null, 1);
		data.killLog.add(loot);

		service.recordCompletion(data, "2026-07-19", "2026-07-19T10:10:00Z", "Vorkath", 100);

		assertEquals(2, data.killLog.size());
		assertNull(loot.killCount);
	}

	@Test
	public void legacyRowsStillMeanLootWasCaptured()
	{
		TrackerData.KillLogEntry legacy = new TrackerData.KillLogEntry();
		assertTrue(legacy.hasCapturedLoot());
	}

	@Test
	public void raidCompletionGetsAnExplicitKillLogLink()
	{
		TrackerData data = new TrackerData();
		service.recordCompletion(data, "2026-07-19", "2026-07-19T10:00:00Z",
			"Tombs of Amascut: Expert Mode", 42);
		TrackerData.RaidCompletion raid = new TrackerData.RaidCompletion();
		raid.source = "Tombs of Amascut: Expert Mode";
		raid.killCount = 42;
		data.raidCompletions.add(raid);

		assertTrue(service.linkRaidCompletions(data));
		assertEquals(data.killLog.get(0).id, raid.killId);
	}
}
