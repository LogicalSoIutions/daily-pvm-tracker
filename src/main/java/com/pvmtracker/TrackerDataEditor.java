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
		if (day == null || day.sources.remove(boss) == null)
		{
			return false;
		}
		removeEmptyDay(data, key, day);
		return true;
	}

	static boolean deleteDayGp(TrackerData data, LocalDate date)
	{
		return data.lootDays.remove(date.toString()) != null;
	}

	private static void removeEmptyDay(TrackerData data, String key, TrackerData.LootDay day)
	{
		if (day.sources.isEmpty() && day.manualAdjustments.isEmpty())
		{
			data.lootDays.remove(key);
		}
	}
}
