package com.pvmtracker;

import net.runelite.api.gameval.ItemID;

final class RaidLootMatcher
{
	private RaidLootMatcher()
	{
	}

	static boolean isRaid(String source)
	{
		return source != null && (source.startsWith("Chambers of Xeric")
			|| source.startsWith("Theatre of Blood")
			|| source.startsWith("Tombs of Amascut"));
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

	private static boolean sameRaid(String first, String second, String raid)
	{
		return first.startsWith(raid) && second.startsWith(raid);
	}
}
