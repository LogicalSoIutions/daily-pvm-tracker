package com.pvmtracker;

import java.util.Map;

final class TrackerDataMigration
{
	private static final int RAID_KC_REPAIR_SCHEMA_VERSION = 4;
	private static final int OBSERVED_KC_ONLY_SCHEMA_VERSION = 5;

	private TrackerDataMigration()
	{
	}

	static boolean migrateToCurrentVersion(TrackerData data, int currentSchemaVersion)
	{
		if (data.schemaVersion >= currentSchemaVersion)
		{
			return false;
		}
		if (data.schemaVersion < RAID_KC_REPAIR_SCHEMA_VERSION)
		{
			repairInferredRaidKills(data);
		}
		if (data.schemaVersion < OBSERVED_KC_ONLY_SCHEMA_VERSION)
		{
			removeRecoveredKills(data);
		}
		data.schemaVersion = currentSchemaVersion;
		return true;
	}

	private static void removeRecoveredKills(TrackerData data)
	{
		for (TrackerData.KcDay day : data.kcDays.values())
		{
			for (Map.Entry<String, Integer> entry : new java.util.ArrayList<>(day.recoveredKills.entrySet()))
			{
				String source = entry.getKey();
				int observed = Math.max(0, day.kills.getOrDefault(source, 0) - entry.getValue());
				if (observed == 0)
				{
					day.kills.remove(source);
					day.startingKillCounts.remove(source);
					day.endingKillCounts.remove(source);
				}
				else
				{
					day.kills.put(source, observed);
					Integer ending = day.endingKillCounts.get(source);
					if (ending != null)
					{
						day.startingKillCounts.put(source, Math.max(0, ending - observed));
					}
				}
			}
			day.recoveredKills.clear();
			day.intervalEnd = null;
		}
	}

	private static void repairInferredRaidKills(TrackerData data)
	{
		for (Map.Entry<String, TrackerData.KcDay> dayEntry : data.kcDays.entrySet())
		{
			String date = dayEntry.getKey();
			TrackerData.KcDay day = dayEntry.getValue();
			for (Map.Entry<String, Integer> killEntry : new java.util.ArrayList<>(day.kills.entrySet()))
			{
				String source = killEntry.getKey();
				int recovered = day.recoveredKills.getOrDefault(source, 0);
				int observed = killEntry.getValue() - recovered;
				if (!RaidLootMatcher.isRaid(source) || recovered <= 0 || observed <= 0
					|| observed != exactLootCompletions(data, date, source))
				{
					continue;
				}
				day.kills.put(source, observed);
				day.recoveredKills.remove(source);
				Integer ending = day.endingKillCounts.get(source);
				if (ending != null)
				{
					day.startingKillCounts.put(source, Math.max(0, ending - observed));
				}
			}
		}
	}

	private static int exactLootCompletions(TrackerData data, String date, String source)
	{
		int count = 0;
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			if (date.equals(kill.date) && source.equals(kill.source) && kill.killCount != null)
			{
				count += kill.kills;
			}
		}
		return count;
	}
}
