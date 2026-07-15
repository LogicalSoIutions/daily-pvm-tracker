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
			List<DailySummary.LootSummary> loot = summarizeLoot(lootDay);
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
			if (showEmptyDays || completed || totalKills > 0 || totalValue > 0)
			{
				result.add(new DailySummary(date, intervalEnd, completed,
					kills, recoveredKills, startingCounts, endingCounts, loot, totalKills, totalRecoveredKills,
					trackedLootValue, confirmedValue, adjustments, totalAdjustment, totalValue));
			}
		}
		return result;
	}

	private List<DailySummary.LootSummary> summarizeLoot(TrackerData.LootDay day)
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
					.sorted(Comparator.comparingLong((TrackerData.LootItem item) -> item.totalValue).reversed())
					.map(item -> new DailySummary.ItemSummary(item.itemId, item.name, item.quantity, item.totalValue,
						item.confirmedQuantity, item.confirmedValue))
					.collect(Collectors.toList());
				long confirmedValue = source.items.values().stream().mapToLong(item -> item.confirmedValue).sum();
				return new DailySummary.LootSummary(entry.getKey(), source.drops, source.totalValue, confirmedValue, items);
			})
			.sorted(Comparator.comparingLong((DailySummary.LootSummary source) -> source.value).reversed())
			.collect(Collectors.toList());
	}

}
