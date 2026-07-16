package com.pvmtracker;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

final class BossRegistry
{
	private static final Map<String, String> SOURCES = new HashMap<>();

	static
	{
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() == HiscoreSkillType.BOSS)
			{
				SOURCES.put(normalize(skill.getName()), skill.getName());
			}
		}

		alias("Dusk", "Grotesque Guardians");
		alias("Dawn", "Grotesque Guardians");
		alias("Barrows", "Barrows Chests");
		alias("Barrows chest", "Barrows Chests");
		alias("Lunar Chest", "Lunar Chests");
		alias("Fortis Colosseum", "Sol Heredit");
		alias("Reward pool (Tempoross)", "Tempoross");
		alias("Casket (Tempoross)", "Tempoross");
		alias("Reward cart (Wintertodt)", "Wintertodt");
		alias("Supply crate (Wintertodt)", "Wintertodt");
		alias("Crystalline Hunllef", "The Gauntlet");
		alias("Corrupted Hunllef", "The Corrupted Gauntlet");
		alias("Gauntlet", "The Gauntlet");
		alias("Corrupted Gauntlet", "The Corrupted Gauntlet");
		alias("The Mimic", "Mimic");
		alias("The Nightmare", "Nightmare");
		alias("Phosani's Nightmare", "Phosani's Nightmare");
		alias("Tumeken's Warden", "Tombs of Amascut");
		alias("The Wardens", "Tombs of Amascut");
		alias("Verzik Vitur", "Theatre of Blood");
		alias("Theatre of Blood: Entry Mode", "Theatre of Blood: Entry Mode");
		alias("Tombs of Amascut: Entry Mode", "Tombs of Amascut: Entry Mode");
		alias("The Great Olm", "Chambers of Xeric");
		alias("Branda the Fire Queen", "The Royal Titans");
		alias("Eldric the Ice King", "The Royal Titans");
		alias("Branda and Eldric", "The Royal Titans");
	}

	private BossRegistry()
	{
	}

	static String canonicalize(String source)
	{
		return source == null ? null : SOURCES.get(normalize(source));
	}

	static List<String> bosses()
	{
		List<String> bosses = new ArrayList<>();
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() == HiscoreSkillType.BOSS)
			{
				bosses.add(skill.getName());
			}
		}
		Collections.sort(bosses);
		return Collections.unmodifiableList(bosses);
	}

	private static void alias(String source, String boss)
	{
		SOURCES.put(normalize(source), boss);
	}

	private static String normalize(String value)
	{
		return value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
	}
}
