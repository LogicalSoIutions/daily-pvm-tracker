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
		TrackerData.KillLootItem oldKillLoot = addKillLoot(data, "2026-07-10", "Nex", 4151, 2);
		TrackerData.KillLootItem newKillLoot = addKillLoot(data, "2026-07-14", "Abyssal Sire", 4151, 3);

		GeSaleMatcher.MatchResult match = GeSaleMatcher.match(data, 4151, 4, 4_000_000L);

		assertEquals(4, match.quantity);
		assertEquals(3_920_000L, match.value);
		assertEquals(2, oldLoot.confirmedQuantity);
		assertEquals(1_960_000L, oldLoot.confirmedValue);
		assertEquals(2, oldLoot.geConfirmedQuantity);
		assertEquals(1_960_000L, oldLoot.geConfirmedValue);
		assertEquals(2, newLoot.confirmedQuantity);
		assertEquals(1_960_000L, newLoot.confirmedValue);
		assertEquals(2, oldKillLoot.confirmedQuantity);
		assertEquals(1_960_000L, oldKillLoot.confirmedValue);
		assertEquals(2, newKillLoot.confirmedQuantity);
		assertEquals(1_960_000L, newKillLoot.confirmedValue);
	}

	@Test
	public void recordsHighAlchemyAsItsOwnConfirmationMethod()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootItem loot = addLoot(data, "2026-07-10", "Vorkath", 11286, 1);

		GeSaleMatcher.MatchResult match = GeSaleMatcher.match(data, 11286, 1, 60_000L,
			GeSaleMatcher.ConfirmationMethod.HIGH_ALCHEMY);

		assertEquals(1, match.quantity);
		assertEquals(60_000L, loot.confirmedValue);
		assertEquals(1, loot.alchConfirmedQuantity);
		assertEquals(60_000L, loot.alchConfirmedValue);
		assertEquals(0, loot.geConfirmedQuantity);
	}

	@Test
	public void confirmsOnlyTheTrackedPartOfAMixedSale()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootItem loot = addLoot(data, "2026-07-10", "Vorkath", 11286, 1);

		GeSaleMatcher.MatchResult match = GeSaleMatcher.match(data, 11286, 4, 8_000_000L);

		assertEquals(1, match.quantity);
		assertEquals(1_960_000L, match.value);
		assertEquals(1, loot.confirmedQuantity);
		assertEquals(1_960_000L, loot.confirmedValue);
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
		assertEquals(1_960_000L, loot.confirmedValue);
	}

	@Test
	public void recordsActualCoinsAfterGrandExchangeTax()
	{
		TrackerData data = new TrackerData();
		TrackerData.LootItem loot = addLoot(data, "2026-07-10", "Vorkath", 1619, 13);

		GeSaleMatcher.MatchResult match = GeSaleMatcher.match(data, 1619, 13, 14_976L);

		assertEquals(13, match.quantity);
		assertEquals(14_677L, match.value);
		assertEquals(14_677L, loot.confirmedValue);
		assertEquals(14_677L, loot.geConfirmedValue);
	}

	@Test
	public void roundsTaxDownPerItemAndCapsIt()
	{
		assertEquals(49L, GrandExchangeTax.netProceeds(1, 49L));
		assertEquals(49L, GrandExchangeTax.netProceeds(1, 50L));
		assertEquals(245_000_000L, GrandExchangeTax.netProceeds(1, 250_000_000L));
		assertEquals(295_000_000L, GrandExchangeTax.netProceeds(1, 300_000_000L));
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

	private static TrackerData.KillLootItem addKillLoot(TrackerData data, String date, String boss,
		int itemId, long quantity)
	{
		TrackerData.KillLogEntry kill = new TrackerData.KillLogEntry();
		kill.date = date;
		kill.source = boss;
		TrackerData.KillLootItem item = new TrackerData.KillLootItem(itemId, "Tracked item", quantity,
			quantity * 1_500_000L);
		kill.items.add(item);
		data.killLog.add(kill);
		return item;
	}
}
