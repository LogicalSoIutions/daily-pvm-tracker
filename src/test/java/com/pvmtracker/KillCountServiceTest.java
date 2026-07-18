package com.pvmtracker;

import java.time.LocalDate;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillCountServiceTest
{
	private final KillCountService service = new KillCountService();
	private final LocalDate date = LocalDate.parse("2026-07-15");

	@Test
	public void delayedLootDoesNotDuplicateAnExactChatCompletion()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Maggot King", 483);

		assertTrue(service.recordCompletion(data, date, "Maggot King", 484));
		assertFalse(service.recordLootCompletionIfMissing(data, date, "Maggot King", 1));

		assertEquals(Integer.valueOf(1), data.kcDays.get(date.toString()).kills.get("Maggot King"));
		assertEquals(Integer.valueOf(484), data.lastKnownKillCounts.get("Maggot King"));
	}

	@Test
	public void lootBeforeChatStillCountsOnlyOnce()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Maggot King", 483);

		assertTrue(service.recordLootCompletionIfMissing(data, date, "Maggot King", 1));
		assertFalse(service.recordCompletion(data, date, "Maggot King", 484));

		assertEquals(Integer.valueOf(1), data.kcDays.get(date.toString()).kills.get("Maggot King"));
		assertEquals(Integer.valueOf(484), data.lastKnownKillCounts.get("Maggot King"));
	}

	@Test
	public void recoveredKillsDoNotHideANewLootOnlyCompletion()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Maggot King", 483);
		service.reconcile(data, date, Collections.singletonMap("Maggot King", 484));

		assertTrue(service.recordLootCompletionIfMissing(data, date, "Maggot King", 1));

		TrackerData.KcDay day = data.kcDays.get(date.toString());
		assertEquals(Integer.valueOf(2), day.kills.get("Maggot King"));
		assertEquals(Integer.valueOf(1), day.recoveredKills.get("Maggot King"));
		assertEquals(Integer.valueOf(485), data.lastKnownKillCounts.get("Maggot King"));
	}

	@Test
	public void firstLocallyTrackedRaidModeInfersItsStartFromExactChatCount()
	{
		TrackerData data = new TrackerData();

		assertTrue(service.recordCompletion(data, date, "Theatre of Blood: Entry Mode", 2));

		TrackerData.KcDay day = data.kcDays.get(date.toString());
		assertEquals(Integer.valueOf(1), day.kills.get("Theatre of Blood: Entry Mode"));
		assertEquals(Integer.valueOf(1), day.startingKillCounts.get("Theatre of Blood: Entry Mode"));
		assertEquals(Integer.valueOf(2), day.endingKillCounts.get("Theatre of Blood: Entry Mode"));
		assertEquals(Integer.valueOf(2), data.lastKnownKillCounts.get("Theatre of Blood: Entry Mode"));
	}

	@Test
	public void exactChatJumpRecordsOnlyTheObservedCompletion()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Chambers of Xeric", 67);

		assertTrue(service.recordCompletion(data, date, "Chambers of Xeric", 69));

		TrackerData.KcDay day = data.kcDays.get(date.toString());
		assertEquals(Integer.valueOf(1), day.kills.get("Chambers of Xeric"));
		assertFalse(day.recoveredKills.containsKey("Chambers of Xeric"));
		assertEquals(Integer.valueOf(68), day.startingKillCounts.get("Chambers of Xeric"));
		assertEquals(Integer.valueOf(69), day.endingKillCounts.get("Chambers of Xeric"));
	}

	@Test
	public void exactChatCountCorrectsALootOnlyCounterThatDriftedAhead()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("The Corrupted Gauntlet", 76);
		for (int i = 1; i <= 24; i++)
		{
			assertTrue(service.recordLootCompletionIfMissing(data, date, "The Corrupted Gauntlet", i));
		}

		assertEquals(Integer.valueOf(100), data.lastKnownKillCounts.get("The Corrupted Gauntlet"));
		assertTrue(service.recordCompletion(data, date, "The Corrupted Gauntlet", 99));

		TrackerData.KcDay day = data.kcDays.get(date.toString());
		assertEquals(Integer.valueOf(24), day.kills.get("The Corrupted Gauntlet"));
		assertEquals(Integer.valueOf(75), day.startingKillCounts.get("The Corrupted Gauntlet"));
		assertEquals(Integer.valueOf(99), day.endingKillCounts.get("The Corrupted Gauntlet"));
		assertEquals(Integer.valueOf(99), data.lastKnownKillCounts.get("The Corrupted Gauntlet"));
	}

	@Test
	public void hiscoresCorrectALootOnlyCounterThatDriftedAhead()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("The Gauntlet", 1);
		data.lastKnownKillCounts.put("The Corrupted Gauntlet", 76);
		data.lastKnownKillCountsAt = date.toString();
		for (int i = 1; i <= 24; i++)
		{
			assertTrue(service.recordLootCompletionIfMissing(data, date, "The Corrupted Gauntlet", i));
		}

		java.util.Map<String, Integer> hiscores = new java.util.LinkedHashMap<>();
		hiscores.put("The Gauntlet", 1);
		hiscores.put("The Corrupted Gauntlet", 99);
		assertEquals(0, service.reconcile(data, date, hiscores));

		TrackerData.KcDay day = data.kcDays.get(date.toString());
		assertEquals(Integer.valueOf(24), day.kills.get("The Corrupted Gauntlet"));
		assertEquals(Integer.valueOf(75), day.startingKillCounts.get("The Corrupted Gauntlet"));
		assertEquals(Integer.valueOf(99), day.endingKillCounts.get("The Corrupted Gauntlet"));
		assertEquals(Integer.valueOf(1), data.lastKnownKillCounts.get("The Gauntlet"));
		assertEquals(Integer.valueOf(99), data.lastKnownKillCounts.get("The Corrupted Gauntlet"));
	}
}
