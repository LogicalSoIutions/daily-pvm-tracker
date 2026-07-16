package com.pvmtracker;

import java.time.LocalDate;
import java.util.Map;

final class KillCountService
{
	boolean recordCompletion(TrackerData data, LocalDate date, String boss, Integer exactKillCount)
	{
		Integer previous = data.lastKnownKillCounts.get(boss);
		int delta;
		int ending;
		if (exactKillCount == null)
		{
			delta = 1;
			ending = previous == null ? -1 : previous + 1;
		}
		else
		{
			if (previous != null && exactKillCount <= previous)
			{
				return false;
			}
			delta = previous == null ? 1 : exactKillCount - previous;
			ending = exactKillCount;
		}

		TrackerData.KcDay day = data.kcDays.computeIfAbsent(date.toString(), ignored -> new TrackerData.KcDay());
		day.kills.merge(boss, delta, Integer::sum);
		if (exactKillCount != null && delta > 1)
		{
			day.recoveredKills.merge(boss, delta - 1, Integer::sum);
		}
		if (previous != null)
		{
			day.startingKillCounts.putIfAbsent(boss, previous);
		}
		else if (exactKillCount != null)
		{
			day.startingKillCounts.putIfAbsent(boss, Math.max(0, exactKillCount - delta));
		}
		if (ending >= 0)
		{
			day.endingKillCounts.put(boss, ending);
			data.lastKnownKillCounts.put(boss, ending);
		}
		data.lastKnownKillCountsAt = date.toString();
		return true;
	}

	boolean recordLootCompletionIfMissing(TrackerData data, LocalDate date, String boss, int lootDrops)
	{
		TrackerData.KcDay day = data.kcDays.get(date.toString());
		int recordedKills = day == null ? 0 : day.kills.getOrDefault(boss, 0);
		int recoveredKills = day == null ? 0 : day.recoveredKills.getOrDefault(boss, 0);
		int observedKills = Math.max(0, recordedKills - recoveredKills);
		if (observedKills >= lootDrops)
		{
			return false;
		}
		return recordCompletion(data, date, boss, null);
	}

	int reconcile(TrackerData data, LocalDate date, Map<String, Integer> hiscoreCounts)
	{
		if (data.lastKnownKillCounts.isEmpty())
		{
			data.lastKnownKillCounts.putAll(hiscoreCounts);
			data.lastKnownKillCountsAt = date.toString();
			return 0;
		}

		LocalDate intervalStart = data.lastKnownKillCountsAt == null
			? date : LocalDate.parse(data.lastKnownKillCountsAt);
		TrackerData.KcDay recoveredDay = null;
		int recovered = 0;
		for (Map.Entry<String, Integer> entry : hiscoreCounts.entrySet())
		{
			String boss = entry.getKey();
			int remote = entry.getValue();
			Integer local = data.lastKnownKillCounts.get(boss);
			if (local == null)
			{
				data.lastKnownKillCounts.put(boss, remote);
				continue;
			}
			if (remote <= local)
			{
				continue;
			}

			int delta = remote - local;
			if (recoveredDay == null)
			{
				recoveredDay = data.kcDays.computeIfAbsent(intervalStart.toString(), ignored -> new TrackerData.KcDay());
				if (intervalStart.isBefore(date))
				{
					recoveredDay.intervalEnd = date.toString();
				}
			}
			recoveredDay.kills.merge(boss, delta, Integer::sum);
			recoveredDay.recoveredKills.merge(boss, delta, Integer::sum);
			recoveredDay.startingKillCounts.putIfAbsent(boss, local);
			recoveredDay.endingKillCounts.put(boss, remote);
			data.lastKnownKillCounts.put(boss, remote);
			recovered += delta;
		}
		data.lastKnownKillCountsAt = date.toString();
		return recovered;
	}
}
