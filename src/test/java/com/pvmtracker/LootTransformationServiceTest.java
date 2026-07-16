package com.pvmtracker;

import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LootTransformationServiceTest
{
	@Test
	public void replacesIntermediateItemOnItsOriginalBossAndDay()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootDay day = new TrackerData.LootDay();
		TrackerData.LootSource source = new TrackerData.LootSource();
		source.drops = 2;
		TrackerData.LootItem tarnished = new TrackerData.LootItem(33691, "Tarnished necklace");
		tarnished.quantity = 2;
		source.items.put(tarnished.itemId, tarnished);
		day.sources.put("Maggot King", source);
		data.lootDays.put("2026-07-15", day);
		TrackerData.KillLogEntry kill = new TrackerData.KillLogEntry();
		kill.date = "2026-07-15";
		kill.source = "Maggot King";
		kill.items.add(new TrackerData.KillLootItem(33691, "Tarnished necklace", 2, 72_000));
		data.killLog.add(kill);

		boolean changed = LootTransformationService.replace(data, "Tarnished necklace",
			Collections.singletonList(new LootTransformationService.ReplacementItem(
				33692, "Revealed necklace", 1, 4_500_000L)));

		assertTrue(changed);
		assertEquals(1, source.items.get(33691).quantity);
		assertEquals(1, source.items.get(33692).quantity);
		assertEquals("Revealed necklace", source.items.get(33692).name);
		assertEquals(4_500_000L, source.totalValue);
		assertEquals(2, source.drops);
		assertEquals(1, kill.items.stream().filter(item -> item.itemId == 33691).findFirst().get().quantity);
		assertEquals(1, kill.items.stream().filter(item -> item.itemId == 33692).findFirst().get().quantity);
		assertEquals(4_536_000L, kill.totalValue);
	}

	@Test
	public void ignoresPolishingWhenNoTrackedIntermediateExists()
	{
		assertFalse(LootTransformationService.replace(new TrackerData(), "Tarnished ring",
			Collections.singletonList(new LootTransformationService.ReplacementItem(33688, "Ring", 1, 10L))));
	}
}
