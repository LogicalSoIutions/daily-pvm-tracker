package com.pvmtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

// Keep the original group so upgrades retain users' saved settings.
@ConfigGroup("pvm-raid-daily-tracker")
public interface DailyPvmTrackerConfig extends Config
{
	@ConfigItem(
		keyName = "showEmptyDays",
		name = "Show quiet days",
		description = "Show tracked days that had no boss kills or loot"
	)
	default boolean showEmptyDays()
	{
		return true;
	}

	@ConfigItem(
		keyName = "uploadToPvmHub",
		name = "Upload to PvM Hub (optional)",
		description = "Disabled by default. When enabled, uploads this character's complete Daily PvM Tracker snapshot, including RSN, KC, loot, GP, and Grand Exchange matching state, to PvM Hub at most once every 30 minutes",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean uploadToPvmHub()
	{
		return false;
	}
}
