package com.pvmtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeSaleMatcherTest
{
	@Test
	public void matchesDelayedSaleToOldestLootFirst()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootItem oldLoot = addLoot(data, "2026-07-10", "Nex", 4151, 2);
		TrackerData.LootItem newLoot = addLoot(data, "2026-07-14", "Abyssal Sire", 4151, 3);

		GeSaleMatcher.MatchResult match = GeSaleMatcher.match(data, 4151, 4, 4_000_000L);

		assertEquals(4, match.quantity);
		assertEquals(4_000_000L, match.value);
		assertEquals(2, oldLoot.confirmedQuantity);
		assertEquals(2_000_000L, oldLoot.confirmedValue);
		assertEquals(2, newLoot.confirmedQuantity);
		assertEquals(2_000_000L, newLoot.confirmedValue);
	}

	@Test
	public void confirmsOnlyTheTrackedPartOfAMixedSale()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootItem loot = addLoot(data, "2026-07-10", "Vorkath", 11286, 1);

		GeSaleMatcher.MatchResult match = GeSaleMatcher.match(data, 11286, 4, 8_000_000L);

		assertEquals(1, match.quantity);
		assertEquals(2_000_000L, match.value);
		assertEquals(1, loot.confirmedQuantity);
		assertEquals(2_000_000L, loot.confirmedValue);
	}

	@Test
	public void neverConfirmsTheSameLootTwice()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootItem loot = addLoot(data, "2026-07-10", "Vorkath", 11286, 1);

		GeSaleMatcher.match(data, 11286, 1, 2_000_000L);
		GeSaleMatcher.MatchResult duplicate = GeSaleMatcher.match(data, 11286, 1, 3_000_000L);

		assertEquals(0, duplicate.quantity);
		assertEquals(0, duplicate.value);
		assertEquals(1, loot.confirmedQuantity);
		assertEquals(2_000_000L, loot.confirmedValue);
	}

	private static TrackerData.LootItem addLoot(TrackerData data, String date, String boss, int itemId, long quantity)
	{
		TrackerData.LootDay day = data.lootDays.computeIfAbsent(date, ignored -> new TrackerData.LootDay());
		TrackerData.LootSource source = day.sources.computeIfAbsent(boss, ignored -> new TrackerData.LootSource());
		TrackerData.LootItem item = new TrackerData.LootItem(itemId, "Tracked item");
		item.quantity = quantity;
		item.totalValue = quantity * 1_500_000L;
		source.items.put(itemId, item);
		source.totalValue += item.totalValue;
		return item;
	}
}
