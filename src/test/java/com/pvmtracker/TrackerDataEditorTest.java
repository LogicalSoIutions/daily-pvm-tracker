package com.pvmtracker;

import java.time.LocalDate;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
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

	@Test
	public void hidesAndRestoresAnItemForOnlyOneBoss()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootDay day = new TrackerData.LootDay();
		TrackerData.LootSource nex = sourceWithItem(100, 1_000L);
		TrackerData.LootSource vorkath = sourceWithItem(100, 1_000L);
		day.sources.put("Nex", nex);
		day.sources.put("Vorkath", vorkath);
		data.lootDays.put(DATE.toString(), day);
		TrackerData.KillLogEntry kill = new TrackerData.KillLogEntry();
		kill.id = "kill-1";
		kill.date = DATE.toString();
		kill.occurredAt = "2026-07-15T20:00:00Z";
		kill.source = "Nex";
		kill.totalValue = 1_000L;
		kill.items.add(new TrackerData.KillLootItem(100, "Item", 1, 1_000L));
		data.killLog.add(kill);

		assertTrue(TrackerDataEditor.setLootHidden(data, "Nex", 100, true));
		assertTrue(data.isLootHidden("Nex", 100));
		assertFalse(data.isLootHidden("Vorkath", 100));
		assertEquals(0L, nex.totalValue);
		assertEquals(0L, kill.totalValue);
		assertEquals(1_000L, vorkath.totalValue);

		assertTrue(TrackerDataEditor.setLootHidden(data, "Nex", 100, false));
		assertEquals(1_000L, nex.totalValue);
		assertEquals(1_000L, kill.totalValue);
		assertFalse(data.hiddenLootItems.containsKey("Nex"));
	}

	private static TrackerData.LootSource sourceWithItem(int itemId, long value)
	{
		TrackerData.LootSource source = new TrackerData.LootSource();
		TrackerData.LootItem item = new TrackerData.LootItem(itemId, "Item");
		item.quantity = 1;
		item.totalValue = value;
		source.items.put(itemId, item);
		source.totalValue = value;
		return source;
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
