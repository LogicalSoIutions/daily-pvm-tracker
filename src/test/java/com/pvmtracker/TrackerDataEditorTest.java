package com.pvmtracker;

import java.time.LocalDate;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrackerDataEditorTest
{
	private static final LocalDate DATE = LocalDate.parse("2026-07-15");

	@Test
	public void deletesSplitWithoutDeletingCapturedLoot()
	{
		TrackerData data = dataWithLootAndSplit();

		assertTrue(TrackerDataEditor.setSplit(data, DATE, "Nex", 0L));
		assertFalse(data.lootDays.get(DATE.toString()).sources.isEmpty());
		assertTrue(data.lootDays.get(DATE.toString()).manualAdjustments.isEmpty());
	}

	@Test
	public void deletesBossLootWithoutDeletingSplit()
	{
		TrackerData data = dataWithLootAndSplit();

		assertTrue(TrackerDataEditor.deleteBossLoot(data, DATE, "Nex"));
		assertTrue(data.lootDays.get(DATE.toString()).sources.isEmpty());
		assertFalse(data.lootDays.get(DATE.toString()).manualAdjustments.isEmpty());
	}

	@Test
	public void deletesAllGpDataForDay()
	{
		TrackerData data = dataWithLootAndSplit();

		assertTrue(TrackerDataEditor.deleteDayGp(data, DATE));
		assertFalse(data.lootDays.containsKey(DATE.toString()));
	}

	private static TrackerData dataWithLootAndSplit()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootDay day = new TrackerData.LootDay();
		day.sources.put("Nex", new TrackerData.LootSource());
		day.manualAdjustments.put("Nex", 10_000_000L);
		data.lootDays.put(DATE.toString(), day);
		return data;
	}
}
