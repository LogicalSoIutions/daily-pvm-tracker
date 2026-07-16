package com.pvmtracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

final class TrackerData
{
	int schemaVersion = 1;
	String lastKnownName = "";
	String lastKnownKillCountsAt;
	Map<String, Integer> lastKnownKillCounts = new LinkedHashMap<>();
	Map<String, KcDay> kcDays = new LinkedHashMap<>();
	Map<String, LootDay> lootDays = new LinkedHashMap<>();
	Map<String, Set<Integer>> hiddenLootItems = new LinkedHashMap<>();
	List<KillLogEntry> killLog = new ArrayList<>();
	List<RaidCompletion> raidCompletions = new ArrayList<>();
	Map<Integer, GeOffer> geOffers = new LinkedHashMap<>();

	boolean isLootHidden(String source, int itemId)
	{
		Set<Integer> hidden = hiddenLootItems.get(source);
		return hidden != null && hidden.contains(itemId);
	}

	Set<Integer> hiddenLootFor(String source)
	{
		return hiddenLootItems.computeIfAbsent(source, ignored -> new LinkedHashSet<>());
	}

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
		long geConfirmedQuantity;
		long geConfirmedValue;
		long alchConfirmedQuantity;
		long alchConfirmedValue;

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

	static final class KillLogEntry
	{
		String id;
		String date;
		String occurredAt;
		String source;
		Integer killCount;
		int kills = 1;
		long totalValue;
		List<KillLootItem> items = new ArrayList<>();
	}

	static final class KillLootItem
	{
		int itemId;
		String name;
		long quantity;
		long totalValue;
		long confirmedQuantity;
		long confirmedValue;

		KillLootItem()
		{
		}

		KillLootItem(int itemId, String name, long quantity, long totalValue)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.totalValue = totalValue;
		}
	}

	static final class RaidCompletion
	{
		String id;
		String date;
		String occurredAt;
		String source;
		Integer killCount;
		Integer personalPoints;
		Integer lootPoints;
		Integer teamPoints;
		Integer raidLevel;
		double uniqueChance;
		long expectedUniqueValue;
		String estimateBasis;
	}
}
