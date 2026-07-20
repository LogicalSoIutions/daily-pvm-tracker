package com.pvmtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrackerDataMigrationTest
{
	@Test
	public void v5RemovesPreviouslyRecoveredKillsFromTheTimeline()
	{
		TrackerData data = new TrackerData();
		data.schemaVersion = 4;
		TrackerData.KcDay mixed = new TrackerData.KcDay();
		mixed.kills.put("Vorkath", 6);
		mixed.recoveredKills.put("Vorkath", 5);
		mixed.startingKillCounts.put("Vorkath", 100);
		mixed.endingKillCounts.put("Vorkath", 106);
		mixed.intervalEnd = "2026-07-19";
		data.kcDays.put("2026-07-18", mixed);

		TrackerData.KcDay recoveredOnly = new TrackerData.KcDay();
		recoveredOnly.kills.put("Zulrah", 3);
		recoveredOnly.recoveredKills.put("Zulrah", 3);
		recoveredOnly.startingKillCounts.put("Zulrah", 20);
		recoveredOnly.endingKillCounts.put("Zulrah", 23);
		data.kcDays.put("2026-07-17", recoveredOnly);

		assertTrue(TrackerDataMigration.migrateToCurrentVersion(data, 5));
		assertEquals(Integer.valueOf(1), mixed.kills.get("Vorkath"));
		assertEquals(Integer.valueOf(105), mixed.startingKillCounts.get("Vorkath"));
		assertTrue(mixed.recoveredKills.isEmpty());
		assertEquals(null, mixed.intervalEnd);
		assertTrue(recoveredOnly.kills.isEmpty());
		assertTrue(recoveredOnly.startingKillCounts.isEmpty());
		assertTrue(recoveredOnly.endingKillCounts.isEmpty());
	}

	@Test
	public void v4RemovesOnlyRaidCountsInferredBeyondExactLootCompletions()
	{
		TrackerData data = new TrackerData();
		data.schemaVersion = 3;
		TrackerData.KcDay day = new TrackerData.KcDay();
		day.kills.put("Chambers of Xeric", 2);
		day.recoveredKills.put("Chambers of Xeric", 1);
		day.startingKillCounts.put("Chambers of Xeric", 67);
		day.endingKillCounts.put("Chambers of Xeric", 69);
		data.kcDays.put("2026-07-17", day);
		TrackerData.KillLogEntry kill = new TrackerData.KillLogEntry();
		kill.date = "2026-07-17";
		kill.source = "Chambers of Xeric";
		kill.killCount = 69;
		data.killLog.add(kill);

		assertTrue(TrackerDataMigration.migrateToCurrentVersion(data, 4));
		assertEquals(4, data.schemaVersion);
		assertEquals(Integer.valueOf(1), day.kills.get("Chambers of Xeric"));
		assertFalse(day.recoveredKills.containsKey("Chambers of Xeric"));
		assertEquals(Integer.valueOf(68), day.startingKillCounts.get("Chambers of Xeric"));

		assertFalse(TrackerDataMigration.migrateToCurrentVersion(data, 4));
	}
}
