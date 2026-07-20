package com.pvmtracker;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

final class KillLogService
{
	private static final Duration LOOT_BEFORE_COMPLETION_WINDOW = Duration.ofSeconds(90);

	boolean recordCompletion(TrackerData data, String date, String occurredAt, String source, int killCount)
	{
		for (int i = data.killLog.size() - 1; i >= 0; i--)
		{
			TrackerData.KillLogEntry kill = data.killLog.get(i);
			if (RaidLootMatcher.matchesCompletion(kill.source, source)
				&& Integer.valueOf(killCount).equals(kill.killCount))
			{
				return false;
			}
		}

		for (int i = data.killLog.size() - 1; i >= 0; i--)
		{
			TrackerData.KillLogEntry kill = data.killLog.get(i);
			if (kill.killCount == null && kill.hasCapturedLoot()
				&& RaidLootMatcher.matchesCompletion(kill.source, source)
				&& withinReverseOrderWindow(kill.occurredAt, occurredAt))
			{
				kill.killCount = killCount;
				return true;
			}
		}

		TrackerData.KillLogEntry kill = new TrackerData.KillLogEntry();
		kill.id = UUID.randomUUID().toString();
		kill.date = date;
		kill.occurredAt = occurredAt;
		kill.source = source;
		kill.killCount = killCount;
		kill.lootCaptured = false;
		data.killLog.add(kill);
		return true;
	}

	TrackerData.KillLogEntry claimOldestUnlootedCompletion(TrackerData data, String lootSource)
	{
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			if (!kill.hasCapturedLoot() && kill.killCount != null
				&& RaidLootMatcher.matchesCompletion(lootSource, kill.source))
			{
				kill.lootCaptured = true;
				return kill;
			}
		}
		return null;
	}

	TrackerData.KillLogEntry findCompletion(TrackerData data, String source, int killCount)
	{
		for (int i = data.killLog.size() - 1; i >= 0; i--)
		{
			TrackerData.KillLogEntry kill = data.killLog.get(i);
			if (RaidLootMatcher.matchesCompletion(kill.source, source)
				&& Integer.valueOf(killCount).equals(kill.killCount))
			{
				return kill;
			}
		}
		return null;
	}

	boolean linkRaidCompletions(TrackerData data)
	{
		boolean changed = false;
		for (TrackerData.RaidCompletion raid : data.raidCompletions)
		{
			if (raid.killId != null || raid.source == null || raid.killCount == null)
			{
				continue;
			}
			TrackerData.KillLogEntry kill = findCompletion(data, raid.source, raid.killCount);
			if (kill != null)
			{
				raid.killId = kill.id;
				changed = true;
			}
		}
		return changed;
	}

	TrackerData.KillLogEntry createLootOnly(String date, String occurredAt, String source,
		Integer killCount, int kills)
	{
		TrackerData.KillLogEntry kill = new TrackerData.KillLogEntry();
		kill.id = UUID.randomUUID().toString();
		kill.date = date;
		kill.occurredAt = occurredAt;
		kill.source = source;
		kill.killCount = killCount;
		kill.kills = kills;
		kill.lootCaptured = true;
		return kill;
	}

	private boolean withinReverseOrderWindow(String lootOccurredAt, String completionOccurredAt)
	{
		if (lootOccurredAt == null || completionOccurredAt == null)
		{
			return false;
		}
		try
		{
			return Duration.between(Instant.parse(lootOccurredAt), Instant.parse(completionOccurredAt)).abs()
				.compareTo(LOOT_BEFORE_COMPLETION_WINDOW) <= 0;
		}
		catch (DateTimeParseException ignored)
		{
			return false;
		}
	}
}
