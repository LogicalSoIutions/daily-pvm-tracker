package com.pvmtracker;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
			if (previous != null && exactKillCount < previous)
			{
				correctAheadCount(data, date, boss, previous - exactKillCount, exactKillCount);
				return true;
			}
			if (previous != null && exactKillCount.equals(previous))
			{
				return false;
			}
			// An exact KC message proves that this completion happened, but a jump in the
			// absolute count does not prove that the plugin observed the intervening kills.
			delta = 1;
			ending = exactKillCount;
		}

		TrackerData.KcDay day = data.kcDays.computeIfAbsent(date.toString(), ignored -> new TrackerData.KcDay());
		day.kills.merge(boss, delta, Integer::sum);
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

	private void correctAheadCount(TrackerData data, LocalDate date, String boss, int correction, int exactKillCount)
	{
		TrackerData.KcDay day = data.kcDays.get(date.toString());
		if (day != null)
		{
			Integer starting = day.startingKillCounts.get(boss);
			Integer ending = day.endingKillCounts.get(boss);
			if (starting != null)
			{
				day.startingKillCounts.put(boss, Math.max(0, starting - correction));
			}
			if (ending != null)
			{
				day.endingKillCounts.put(boss, Math.max(0, ending - correction));
			}
		}
		data.lastKnownKillCounts.put(boss, exactKillCount);
		data.lastKnownKillCountsAt = date.toString();
	}

	boolean recordLootCompletionIfMissing(TrackerData data, LocalDate date, String boss, int lootDrops)
	{
		TrackerData.KcDay day = data.kcDays.get(date.toString());
		int recordedKills = day == null ? 0 : day.kills.getOrDefault(boss, 0);
		if (recordedKills >= lootDrops)
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

		LocalDate baselineDate = data.lastKnownKillCountsAt == null
			? date : LocalDate.parse(data.lastKnownKillCountsAt);
		int synchronizedKills = 0;
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
			if (remote < local)
			{
				if (canCorrectSingleCountDrift(data, baselineDate, boss, local, remote))
				{
					correctAheadCount(data, baselineDate, boss, 1, remote);
				}
				continue;
			}
			if (remote == local)
			{
				continue;
			}

			int delta = remote - local;
			data.lastKnownKillCounts.put(boss, remote);
			synchronizedKills += delta;
		}
		data.lastKnownKillCountsAt = date.toString();
		return synchronizedKills;
	}

	boolean repairLegacyLootKillCounts(TrackerData data)
	{
		Set<String> sources = new LinkedHashSet<>(data.lastKnownKillCounts.keySet());
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			if (kill.source != null)
			{
				sources.add(kill.source);
			}
		}

		boolean changed = false;
		for (String source : sources)
		{
			changed |= repairLegacyLootKillCounts(data, source);
		}
		return changed;
	}

	private boolean repairLegacyLootKillCounts(TrackerData data, String source)
	{
		Integer anchorCount = null;
		int anchorIndex = -1;
		boolean changed = false;

		for (int i = 0; i < data.killLog.size(); i++)
		{
			TrackerData.KillLogEntry kill = data.killLog.get(i);
			if (!source.equals(kill.source) || kill.lootCaptured != null || kill.killCount == null)
			{
				continue;
			}
			if (anchorCount != null)
			{
				changed |= fillProvenSequence(data, source, anchorIndex + 1, i + 1,
					anchorCount, kill.killCount);
			}
			anchorCount = kill.killCount;
			anchorIndex = i;
		}

		Integer latestKnown = data.lastKnownKillCounts.get(source);
		if (anchorCount != null && latestKnown != null && latestKnown >= anchorCount)
		{
			changed |= fillProvenSequence(data, source, anchorIndex + 1, data.killLog.size(),
				anchorCount, latestKnown);
		}
		return changed;
	}

	private boolean fillProvenSequence(TrackerData data, String source, int fromIndex, int toIndex,
		int startingCount, int endingCount)
	{
		int observedKills = 0;
		for (int i = fromIndex; i < toIndex; i++)
		{
			TrackerData.KillLogEntry kill = data.killLog.get(i);
			if (source.equals(kill.source) && kill.lootCaptured == null)
			{
				observedKills += Math.max(1, kill.kills);
			}
		}
		if (endingCount - startingCount != observedKills)
		{
			return false;
		}

		boolean changed = false;
		int count = startingCount;
		for (int i = fromIndex; i < toIndex; i++)
		{
			TrackerData.KillLogEntry kill = data.killLog.get(i);
			if (!source.equals(kill.source) || kill.lootCaptured != null)
			{
				continue;
			}
			count += Math.max(1, kill.kills);
			if (kill.killCount == null)
			{
				kill.killCount = count;
				changed = true;
			}
		}
		return changed;
	}

	private boolean canCorrectSingleCountDrift(TrackerData data, LocalDate date, String boss, int local, int remote)
	{
		if (local - remote != 1)
		{
			return false;
		}
		TrackerData.KcDay day = data.kcDays.get(date.toString());
		if (day == null)
		{
			return false;
		}
		Integer start = day.startingKillCounts.get(boss);
		Integer end = day.endingKillCounts.get(boss);
		int recorded = day.kills.getOrDefault(boss, 0);
		return start != null && end != null && end == local && recorded == end - start;
	}
}
