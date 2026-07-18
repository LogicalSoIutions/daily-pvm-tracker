package com.pvmtracker;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import net.runelite.api.gameval.ItemID;

final class RaidLootMatcher
{
	private static final Duration COMPLETION_ATTRIBUTION_WINDOW = Duration.ofMinutes(2);

	private RaidLootMatcher()
	{
	}

	static boolean isRaid(String source)
	{
		return source != null && (source.startsWith("Chambers of Xeric")
			|| source.startsWith("Theatre of Blood")
			|| source.startsWith("Tombs of Amascut"));
	}

	static boolean isGenericRaidSource(String source)
	{
		return "Chambers of Xeric".equals(source)
			|| "Theatre of Blood".equals(source)
			|| "Tombs of Amascut".equals(source);
	}

	static boolean isNonRewardLoot(String source, int itemCount, int onlyItemId)
	{
		return source != null && source.startsWith("Theatre of Blood")
			&& itemCount == 1 && onlyItemId == ItemID.TOB_BOOK_VERZIK;
	}

	static boolean matchesCompletion(String lootSource, String completionSource)
	{
		if (lootSource == null || completionSource == null)
		{
			return false;
		}
		if (lootSource.equals(completionSource))
		{
			return true;
		}
		return sameRaid(lootSource, completionSource, "Chambers of Xeric")
			|| sameRaid(lootSource, completionSource, "Theatre of Blood")
			|| sameRaid(lootSource, completionSource, "Tombs of Amascut");
	}

	static String resolveSource(String lootSource, String lootOccurredAt,
		List<TrackerData.RaidCompletion> completions)
	{
		if (!isGenericRaidSource(lootSource) || lootOccurredAt == null)
		{
			return lootSource;
		}
		try
		{
			Instant lootTime = Instant.parse(lootOccurredAt);
			TrackerData.RaidCompletion closest = null;
			Duration closestDistance = null;
			for (TrackerData.RaidCompletion completion : completions)
			{
				if (completion == null || completion.source == null || completion.occurredAt == null
					|| lootSource.equals(completion.source)
					|| !matchesCompletion(lootSource, completion.source))
				{
					continue;
				}
				Duration distance = Duration.between(Instant.parse(completion.occurredAt), lootTime).abs();
				if (distance.compareTo(COMPLETION_ATTRIBUTION_WINDOW) <= 0
					&& (closestDistance == null || distance.compareTo(closestDistance) < 0))
				{
					closest = completion;
					closestDistance = distance;
				}
			}
			return closest == null ? lootSource : closest.source;
		}
		catch (DateTimeParseException ignored)
		{
			return lootSource;
		}
	}

	static boolean repairGenericRaidLootAttribution(TrackerData data)
	{
		boolean changed = false;
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			String resolvedSource = resolveSource(kill.source, kill.occurredAt, data.raidCompletions);
			if (resolvedSource.equals(kill.source) || !canMoveUnmodifiedLoot(data, kill))
			{
				continue;
			}
			String originalSource = kill.source;
			moveLootAggregate(data, kill, resolvedSource);
			repairInferredKillCount(data, kill, originalSource);
			kill.source = resolvedSource;
			changed = true;
		}
		return changed;
	}

	private static boolean canMoveUnmodifiedLoot(TrackerData data, TrackerData.KillLogEntry kill)
	{
		TrackerData.LootDay day = data.lootDays.get(kill.date);
		TrackerData.LootSource source = day == null ? null : day.sources.get(kill.source);
		if (source == null || source.drops < kill.kills)
		{
			return false;
		}
		for (TrackerData.KillLootItem killItem : kill.items)
		{
			TrackerData.LootItem item = source.items.get(killItem.itemId);
			if (item == null || item.quantity < killItem.quantity || item.totalValue < killItem.totalValue
				|| item.confirmedQuantity != 0 || item.confirmedValue != 0 || item.geConfirmedQuantity != 0
				|| item.geConfirmedValue != 0 || item.alchConfirmedQuantity != 0 || item.alchConfirmedValue != 0
				|| item.kept || item.confirmedValueOverride != null)
			{
				return false;
			}
		}
		return true;
	}

	private static void moveLootAggregate(TrackerData data, TrackerData.KillLogEntry kill, String resolvedSource)
	{
		TrackerData.LootDay day = data.lootDays.get(kill.date);
		TrackerData.LootSource original = day.sources.get(kill.source);
		TrackerData.LootSource target = day.sources.computeIfAbsent(resolvedSource,
			ignored -> new TrackerData.LootSource());
		original.drops -= kill.kills;
		target.drops += kill.kills;
		for (TrackerData.KillLootItem killItem : kill.items)
		{
			TrackerData.LootItem originalItem = original.items.get(killItem.itemId);
			originalItem.quantity -= killItem.quantity;
			originalItem.totalValue -= killItem.totalValue;
			if (originalItem.quantity == 0 && originalItem.totalValue == 0)
			{
				original.items.remove(killItem.itemId);
			}
			TrackerData.LootItem targetItem = target.items.computeIfAbsent(killItem.itemId,
				ignored -> new TrackerData.LootItem(killItem.itemId, killItem.name));
			targetItem.quantity += killItem.quantity;
			targetItem.totalValue += killItem.totalValue;
		}
		if (original.drops == 0 && original.items.isEmpty())
		{
			day.sources.remove(kill.source);
		}
	}

	private static void repairInferredKillCount(TrackerData data, TrackerData.KillLogEntry kill,
		String originalSource)
	{
		if (kill.killCount != null)
		{
			return;
		}
		TrackerData.KcDay day = data.kcDays.get(kill.date);
		if (day == null)
		{
			return;
		}
		int recorded = day.kills.getOrDefault(originalSource, 0);
		int recovered = day.recoveredKills.getOrDefault(originalSource, 0);
		if (recorded - recovered < kill.kills)
		{
			return;
		}
		int correctedKills = recorded - kill.kills;
		if (correctedKills == 0)
		{
			day.kills.remove(originalSource);
		}
		else
		{
			day.kills.put(originalSource, correctedKills);
		}
		Integer ending = day.endingKillCounts.get(originalSource);
		if (ending != null)
		{
			int correctedEnding = Math.max(0, ending - kill.kills);
			day.endingKillCounts.put(originalSource, correctedEnding);
			if (ending.equals(data.lastKnownKillCounts.get(originalSource)))
			{
				data.lastKnownKillCounts.put(originalSource, correctedEnding);
			}
		}
	}

	private static boolean sameRaid(String first, String second, String raid)
	{
		return first.startsWith(raid) && second.startsWith(raid);
	}
}
