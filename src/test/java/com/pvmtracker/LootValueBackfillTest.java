package com.pvmtracker;

import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LootValueBackfillTest
{
	@Test
	public void fillsPreviouslyUnpricedItemsAndSourceTotal()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootDay day = new TrackerData.LootDay();
		TrackerData.LootSource source = new TrackerData.LootSource();
		TrackerData.LootItem item = new TrackerData.LootItem(99, "New item");
		item.quantity = 3;
		source.items.put(99, item);
		day.sources.put("New boss", source);
		data.lootDays.put("2026-07-15", day);

		assertTrue(LootValueBackfill.missingItemIds(data).contains(99));
		assertTrue(LootValueBackfill.apply(data, Collections.singletonMap(99, 600L)));
		assertEquals(1_800L, item.totalValue);
		assertEquals(1_800L, source.totalValue);
	}
}
