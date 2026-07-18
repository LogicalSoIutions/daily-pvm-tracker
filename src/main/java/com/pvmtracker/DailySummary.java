package com.pvmtracker;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class DailySummary
{
	final LocalDate date;
	final LocalDate intervalEnd;
	final boolean completed;
	final Map<String, Integer> kills;
	final Map<String, Integer> recoveredKills;
	final Map<String, Integer> startingKillCounts;
	final Map<String, Integer> endingKillCounts;
	final List<LootSummary> loot;
	final List<RaidSummary> raids;
	final int totalKills;
	final int totalRecoveredKills;
	final long trackedLootValue;
	final long confirmedValue;
	final Map<String, Long> manualAdjustments;
	final long totalAdjustment;
	final long totalValue;

	DailySummary(LocalDate date, LocalDate intervalEnd, boolean completed, Map<String, Integer> kills,
		Map<String, Integer> recoveredKills,
		Map<String, Integer> startingKillCounts, Map<String, Integer> endingKillCounts,
		List<LootSummary> loot, List<RaidSummary> raids, int totalKills, int totalRecoveredKills,
		long trackedLootValue, long confirmedValue,
		Map<String, Long> manualAdjustments, long totalAdjustment, long totalValue)
	{
		this.date = date;
		this.intervalEnd = intervalEnd;
		this.completed = completed;
		this.kills = Collections.unmodifiableMap(kills);
		this.recoveredKills = Collections.unmodifiableMap(recoveredKills);
		this.startingKillCounts = Collections.unmodifiableMap(startingKillCounts);
		this.endingKillCounts = Collections.unmodifiableMap(endingKillCounts);
		this.loot = Collections.unmodifiableList(loot);
		this.raids = Collections.unmodifiableList(raids);
		this.totalKills = totalKills;
		this.totalRecoveredKills = totalRecoveredKills;
		this.trackedLootValue = trackedLootValue;
		this.confirmedValue = confirmedValue;
		this.manualAdjustments = Collections.unmodifiableMap(manualAdjustments);
		this.totalAdjustment = totalAdjustment;
		this.totalValue = totalValue;
	}

	LootSummary findLoot(String source)
	{
		return loot.stream().filter(entry -> entry.source.equals(source)).findFirst().orElse(null);
	}

	RaidSummary findRaid(String source)
	{
		return raids.stream().filter(entry -> entry.source.equals(source)).findFirst().orElse(null);
	}

	long bossTrackedValue(String source)
	{
		LootSummary summary = findLoot(source);
		return summary == null ? 0L : summary.value;
	}

	long bossAdjustment(String source)
	{
		return manualAdjustments.getOrDefault(source, 0L);
	}

	long bossConfirmedValue(String source)
	{
		return bossConfirmedSaleValue(source) + bossAdjustment(source);
	}

	long bossConfirmedSaleValue(String source)
	{
		LootSummary summary = findLoot(source);
		return summary == null ? 0L : summary.confirmedValue;
	}

	int intervalDays()
	{
		return intervalEnd == null ? 1 : Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(date, intervalEnd));
	}

	static final class LootSummary
	{
		final String source;
		final int drops;
		final long value;
		final long confirmedValue;
		final List<ItemSummary> items;
		final List<ItemSummary> hiddenItems;

		LootSummary(String source, int drops, long value, long confirmedValue, List<ItemSummary> items,
			List<ItemSummary> hiddenItems)
		{
			this.source = source;
			this.drops = drops;
			this.value = value;
			this.confirmedValue = confirmedValue;
			this.items = Collections.unmodifiableList(items);
			this.hiddenItems = Collections.unmodifiableList(hiddenItems);
		}
	}

	static final class RaidSummary
	{
		final String source;
		final int completions;
		final long personalPoints;
		final int pointRecords;
		final long lootPoints;
		final long teamPoints;
		final int teamPointRecords;
		final Integer minimumRaidLevel;
		final Integer maximumRaidLevel;

		RaidSummary(String source, int completions, long personalPoints, int pointRecords, long lootPoints,
			long teamPoints, int teamPointRecords, Integer minimumRaidLevel, Integer maximumRaidLevel)
		{
			this.source = source;
			this.completions = completions;
			this.personalPoints = personalPoints;
			this.pointRecords = pointRecords;
			this.lootPoints = lootPoints;
			this.teamPoints = teamPoints;
			this.teamPointRecords = teamPointRecords;
			this.minimumRaidLevel = minimumRaidLevel;
			this.maximumRaidLevel = maximumRaidLevel;
		}
	}

	static final class ItemSummary
	{
		final int itemId;
		final String name;
		final long quantity;
		final long value;
		final long confirmedQuantity;
		final long confirmedValue;
		final long geConfirmedQuantity;
		final long geConfirmedValue;
		final long alchConfirmedQuantity;
		final long alchConfirmedValue;
		final boolean kept;
		final Long confirmedValueOverride;

		ItemSummary(int itemId, String name, long quantity, long value, long confirmedQuantity, long confirmedValue,
			long geConfirmedQuantity, long geConfirmedValue, long alchConfirmedQuantity, long alchConfirmedValue,
			boolean kept, Long confirmedValueOverride)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.value = value;
			this.confirmedQuantity = confirmedQuantity;
			this.confirmedValue = confirmedValue;
			this.geConfirmedQuantity = geConfirmedQuantity;
			this.geConfirmedValue = geConfirmedValue;
			this.alchConfirmedQuantity = alchConfirmedQuantity;
			this.alchConfirmedValue = alchConfirmedValue;
			this.kept = kept;
			this.confirmedValueOverride = confirmedValueOverride;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
