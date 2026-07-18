package com.pvmtracker;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class DailySummaryService
{
	List<DailySummary> build(TrackerData data, LocalDate today, boolean showEmptyDays)
	{
		TreeSet<LocalDate> dates = new TreeSet<>(Comparator.reverseOrder());
		data.kcDays.keySet().stream().map(LocalDate::parse).forEach(dates::add);
		data.lootDays.keySet().stream().map(LocalDate::parse).forEach(dates::add);
		data.raidCompletions.stream().map(raid -> LocalDate.parse(raid.date)).forEach(dates::add);
		dates.add(today);

		List<DailySummary> result = new ArrayList<>();
		for (LocalDate date : dates)
		{
			TrackerData.KcDay kcDay = data.kcDays.get(date.toString());
			Map<String, Integer> kills = kcDay == null
				? new LinkedHashMap<>() : new LinkedHashMap<>(kcDay.kills);
			Map<String, Integer> recoveredKills = kcDay == null
				? new LinkedHashMap<>() : new LinkedHashMap<>(kcDay.recoveredKills);
			Map<String, Integer> startingCounts = kcDay == null
				? new LinkedHashMap<>() : new LinkedHashMap<>(kcDay.startingKillCounts);
			Map<String, Integer> endingCounts = kcDay == null
				? new LinkedHashMap<>() : new LinkedHashMap<>(kcDay.endingKillCounts);
			LocalDate intervalEnd = kcDay != null && kcDay.intervalEnd != null
				? LocalDate.parse(kcDay.intervalEnd) : date.isBefore(today) ? date.plusDays(1) : null;
			TrackerData.LootDay lootDay = data.lootDays.get(date.toString());
			List<DailySummary.LootSummary> loot = summarizeLoot(data, lootDay);
			List<DailySummary.RaidSummary> raids = summarizeRaids(data, date);
			int totalKills = kills.values().stream().mapToInt(Integer::intValue).sum();
			int totalRecoveredKills = recoveredKills.values().stream().mapToInt(Integer::intValue).sum();
			long trackedLootValue = loot.stream().mapToLong(source -> source.value).sum();
			long confirmedSalesValue = loot.stream().mapToLong(source -> source.confirmedValue).sum();
			Map<String, Long> adjustments = lootDay == null
				? new LinkedHashMap<>() : new LinkedHashMap<>(lootDay.manualAdjustments);
			long totalAdjustment = adjustments.values().stream().mapToLong(Long::longValue).sum();
			long totalValue = trackedLootValue + totalAdjustment;
			long confirmedValue = confirmedSalesValue + totalAdjustment;
			boolean completed = date.isBefore(today);
			if (showEmptyDays || completed || totalKills > 0 || totalValue > 0 || !raids.isEmpty())
			{
				result.add(new DailySummary(date, intervalEnd, completed,
					kills, recoveredKills, startingCounts, endingCounts, loot, raids, totalKills, totalRecoveredKills,
					trackedLootValue, confirmedValue, adjustments, totalAdjustment, totalValue));
			}
		}
		return result;
	}

	private List<DailySummary.RaidSummary> summarizeRaids(TrackerData data, LocalDate date)
	{
		Map<String, RaidAccumulator> raids = new LinkedHashMap<>();
		for (TrackerData.RaidCompletion completion : data.raidCompletions)
		{
			if (!date.toString().equals(completion.date))
			{
				continue;
			}
			RaidAccumulator raid = raids.computeIfAbsent(completion.source, ignored -> new RaidAccumulator());
			raid.completions++;
			if (completion.personalPoints != null)
			{
				raid.personalPoints += completion.personalPoints;
				raid.pointRecords++;
			}
			if (completion.lootPoints != null)
			{
				raid.lootPoints += completion.lootPoints;
			}
			if (completion.teamPoints != null)
			{
				raid.teamPoints += completion.teamPoints;
				raid.teamPointRecords++;
			}
			if (completion.raidLevel != null)
			{
				raid.minimumRaidLevel = raid.minimumRaidLevel == null ? completion.raidLevel
					: Math.min(raid.minimumRaidLevel, completion.raidLevel);
				raid.maximumRaidLevel = raid.maximumRaidLevel == null ? completion.raidLevel
					: Math.max(raid.maximumRaidLevel, completion.raidLevel);
			}
		}
		return raids.entrySet().stream().map(entry ->
		{
			RaidAccumulator raid = entry.getValue();
			return new DailySummary.RaidSummary(entry.getKey(), raid.completions, raid.personalPoints,
				raid.pointRecords, raid.lootPoints, raid.teamPoints, raid.teamPointRecords,
				raid.minimumRaidLevel, raid.maximumRaidLevel);
		}).collect(Collectors.toList());
	}

	private List<DailySummary.LootSummary> summarizeLoot(TrackerData data, TrackerData.LootDay day)
	{
		if (day == null)
		{
			return new ArrayList<>();
		}
		return day.sources.entrySet().stream()
			.map(entry ->
			{
				TrackerData.LootSource source = entry.getValue();
				List<DailySummary.ItemSummary> items = source.items.values().stream()
					.filter(item -> !data.isLootHidden(entry.getKey(), item.itemId))
					.sorted(Comparator.comparingLong((TrackerData.LootItem item) -> item.totalValue).reversed())
					.map(item -> itemSummary(item))
					.collect(Collectors.toList());
				List<DailySummary.ItemSummary> hiddenItems = source.items.values().stream()
					.filter(item -> data.isLootHidden(entry.getKey(), item.itemId))
					.sorted(Comparator.comparing((TrackerData.LootItem item) -> item.name))
					.map(item -> itemSummary(item))
					.collect(Collectors.toList());
				long confirmedValue = items.stream().mapToLong(item -> item.confirmedValue).sum();
				return new DailySummary.LootSummary(entry.getKey(), source.drops, source.totalValue,
					confirmedValue, items, hiddenItems);
			})
			.sorted(Comparator.comparingLong((DailySummary.LootSummary source) -> source.value).reversed())
			.collect(Collectors.toList());
	}

	private DailySummary.ItemSummary itemSummary(TrackerData.LootItem item)
	{
		long confirmedValue = item.confirmedValueOverride == null ? item.confirmedValue : item.confirmedValueOverride;
		return new DailySummary.ItemSummary(item.itemId, item.name, item.quantity, item.totalValue,
			item.confirmedQuantity, confirmedValue, item.geConfirmedQuantity, item.geConfirmedValue,
			item.alchConfirmedQuantity, item.alchConfirmedValue, item.kept, item.confirmedValueOverride);
	}

	private static final class RaidAccumulator
	{
		int completions;
		long personalPoints;
		int pointRecords;
		long lootPoints;
		long teamPoints;
		int teamPointRecords;
		Integer minimumRaidLevel;
		Integer maximumRaidLevel;
	}

}
