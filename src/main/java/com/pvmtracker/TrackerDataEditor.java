package com.pvmtracker;

import java.time.LocalDate;

final class TrackerDataEditor
{
	private TrackerDataEditor()
	{
	}

	static boolean setSplit(TrackerData data, LocalDate date, String boss, long value)
	{
		String key = date.toString();
		TrackerData.LootDay day = data.lootDays.get(key);
		if (value == 0L)
		{
			if (day == null || day.manualAdjustments.remove(boss) == null)
			{
				return false;
			}
			removeEmptyDay(data, key, day);
			return true;
		}
		if (day == null)
		{
			day = new TrackerData.LootDay();
			data.lootDays.put(key, day);
		}
		Long previous = day.manualAdjustments.put(boss, value);
		return previous == null || previous != value;
	}

	static boolean deleteBossLoot(TrackerData data, LocalDate date, String boss)
	{
		String key = date.toString();
		TrackerData.LootDay day = data.lootDays.get(key);
		boolean changed = day != null && day.sources.remove(boss) != null;
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			if (key.equals(kill.date) && boss.equals(kill.source)
				&& (!kill.items.isEmpty() || kill.totalValue != 0))
			{
				kill.items.clear();
				kill.totalValue = 0;
				changed = true;
			}
		}
		if (day != null)
		{
			removeEmptyDay(data, key, day);
		}
		return changed;
	}

	static boolean deleteDayGp(TrackerData data, LocalDate date)
	{
		String key = date.toString();
		boolean changed = data.lootDays.remove(key) != null;
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			if (key.equals(kill.date) && (!kill.items.isEmpty() || kill.totalValue != 0))
			{
				kill.items.clear();
				kill.totalValue = 0;
				changed = true;
			}
		}
		return changed;
	}

	static boolean setLootHidden(TrackerData data, String boss, int itemId, boolean hidden)
	{
		boolean changed;
		if (hidden)
		{
			changed = data.hiddenLootFor(boss).add(itemId);
		}
		else
		{
			java.util.Set<Integer> hiddenItems = data.hiddenLootItems.get(boss);
			changed = hiddenItems != null && hiddenItems.remove(itemId);
			if (hiddenItems != null && hiddenItems.isEmpty())
			{
				data.hiddenLootItems.remove(boss);
			}
		}
		if (changed)
		{
			recalculateBossTotals(data, boss);
		}
		return changed;
	}

	static boolean setLootKept(TrackerData data, LocalDate date, String boss, int itemId, boolean kept)
	{
		TrackerData.LootDay day = data.lootDays.get(date.toString());
		TrackerData.LootSource source = day == null ? null : day.sources.get(boss);
		TrackerData.LootItem item = source == null ? null : source.items.get(itemId);
		if (item == null || item.kept == kept)
		{
			return false;
		}
		item.kept = kept;
		return true;
	}

	static boolean setConfirmedValueOverride(TrackerData data, LocalDate date, String boss, int itemId, Long value)
	{
		TrackerData.LootDay day = data.lootDays.get(date.toString());
		TrackerData.LootSource source = day == null ? null : day.sources.get(boss);
		TrackerData.LootItem item = source == null ? null : source.items.get(itemId);
		if (item == null || java.util.Objects.equals(item.confirmedValueOverride, value))
		{
			return false;
		}
		item.confirmedValueOverride = value;
		return true;
	}

	static void recalculateAllSourceTotals(TrackerData data)
	{
		for (TrackerData.LootDay day : data.lootDays.values())
		{
			for (java.util.Map.Entry<String, TrackerData.LootSource> entry : day.sources.entrySet())
			{
				recalculateSourceTotal(data, entry.getKey(), entry.getValue());
			}
		}
		recalculateKillLogTotals(data);
	}

	private static void recalculateBossTotals(TrackerData data, String boss)
	{
		for (TrackerData.LootDay day : data.lootDays.values())
		{
			TrackerData.LootSource source = day.sources.get(boss);
			if (source != null)
			{
				recalculateSourceTotal(data, boss, source);
			}
		}
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			if (boss.equals(kill.source))
			{
				kill.totalValue = kill.items.stream()
					.filter(item -> !data.isLootHidden(boss, item.itemId))
					.mapToLong(item -> item.totalValue).sum();
			}
		}
	}

	private static void recalculateKillLogTotals(TrackerData data)
	{
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			kill.totalValue = kill.items.stream()
				.filter(item -> !data.isLootHidden(kill.source, item.itemId))
				.mapToLong(item -> item.totalValue).sum();
		}
	}

	private static void recalculateSourceTotal(TrackerData data, String boss, TrackerData.LootSource source)
	{
		source.totalValue = source.items.values().stream()
			.filter(item -> !data.isLootHidden(boss, item.itemId))
			.mapToLong(item -> item.totalValue).sum();
	}

	private static void removeEmptyDay(TrackerData data, String key, TrackerData.LootDay day)
	{
		if (day.sources.isEmpty() && day.manualAdjustments.isEmpty())
		{
			data.lootDays.remove(key);
		}
	}
}
