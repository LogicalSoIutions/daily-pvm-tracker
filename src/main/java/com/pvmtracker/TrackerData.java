package com.pvmtracker;

import java.util.LinkedHashMap;
import java.util.Map;

final class TrackerData
{
	int schemaVersion = 1;
	String lastKnownName = "";
	String lastKnownKillCountsAt;
	Map<String, Integer> lastKnownKillCounts = new LinkedHashMap<>();
	Map<String, KcDay> kcDays = new LinkedHashMap<>();
	Map<String, LootDay> lootDays = new LinkedHashMap<>();
	Map<Integer, GeOffer> geOffers = new LinkedHashMap<>();

	static final class KcDay
	{
		String intervalEnd;
		Map<String, Integer> kills = new LinkedHashMap<>();
		Map<String, Integer> recoveredKills = new LinkedHashMap<>();
		Map<String, Integer> startingKillCounts = new LinkedHashMap<>();
		Map<String, Integer> endingKillCounts = new LinkedHashMap<>();
	}

	static final class LootDay
	{
		Map<String, LootSource> sources = new LinkedHashMap<>();
		Map<String, Long> manualAdjustments = new LinkedHashMap<>();

	}

	static final class LootSource
	{
		int drops;
		long totalValue;
		Map<Integer, LootItem> items = new LinkedHashMap<>();

	}

	static final class LootItem
	{
		int itemId;
		String name;
		long quantity;
		long totalValue;
		long confirmedQuantity;
		long confirmedValue;

		LootItem()
		{
		}

		LootItem(int itemId, String name)
		{
			this.itemId = itemId;
			this.name = name;
		}
	}

	static final class GeOffer
	{
		int itemId;
		int totalQuantity;
		int price;
		int quantitySold;
		int spent;
		String state;

		GeOffer()
		{
		}

		GeOffer(int itemId, int totalQuantity, int price, int quantitySold, int spent, String state)
		{
			this.itemId = itemId;
			this.totalQuantity = totalQuantity;
			this.price = price;
			this.quantitySold = quantitySold;
			this.spent = spent;
			this.state = state;
		}
	}
}
