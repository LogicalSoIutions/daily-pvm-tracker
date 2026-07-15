package com.pvmtracker;

import java.util.Arrays;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BossRegistryTest
{
	@Test
	public void mapsRuneLiteLootNamesToHiscoreBosses()
	{
		assertEquals("Barrows Chests", BossRegistry.canonicalize("Barrows"));
		assertEquals("Tombs of Amascut", BossRegistry.canonicalize("Tombs of Amascut"));
		assertEquals("The Corrupted Gauntlet", BossRegistry.canonicalize("Corrupted Hunllef"));
		assertEquals("Mimic", BossRegistry.canonicalize("The Mimic"));
		assertEquals("The Royal Titans", BossRegistry.canonicalize("Branda the Fire Queen"));
		assertEquals("The Royal Titans", BossRegistry.canonicalize("Eldric the Ice King"));
		assertNull(BossRegistry.canonicalize("Goblin"));
	}

	@Test
	public void registersEveryRuneLiteHiscoreBossAutomatically()
	{
		Arrays.stream(HiscoreSkill.values())
			.filter(skill -> skill.getType() == HiscoreSkillType.BOSS)
			.forEach(skill -> assertEquals(skill.getName(), BossRegistry.canonicalize(skill.getName())));
	}

	@Test
	public void includesEveryRaidHiscoreCategory()
	{
		String[] raids = {
			"Chambers of Xeric",
			"Chambers of Xeric: Challenge Mode",
			"Theatre of Blood",
			"Theatre of Blood: Hard Mode",
			"Tombs of Amascut",
			"Tombs of Amascut: Expert Mode"
		};
		for (String raid : raids)
		{
			assertEquals(raid, BossRegistry.canonicalize(raid));
		}
	}

	@Test
	public void exposesTheCompleteBossCatalogForManualSplits()
	{
		long expected = Arrays.stream(HiscoreSkill.values())
			.filter(skill -> skill.getType() == HiscoreSkillType.BOSS).count();
		assertEquals(expected, BossRegistry.bosses().size());
		assertTrue(BossRegistry.bosses().contains("Nex"));
		assertTrue(BossRegistry.bosses().contains("Tombs of Amascut: Expert Mode"));
	}
}
