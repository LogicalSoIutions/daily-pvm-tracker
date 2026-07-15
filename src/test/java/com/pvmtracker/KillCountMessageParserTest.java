package com.pvmtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KillCountMessageParserTest
{
	@Test
	public void parsesBossAndRaidKillCounts()
	{
		KillCountMessageParser.Observation vorkath = KillCountMessageParser.parse(
			"Your Vorkath kill count is: <col=ff0000>1,234</col>");
		KillCountMessageParser.Observation raid = KillCountMessageParser.parse(
			"Your completion count for Tombs of Amascut: Expert Mode is: <col=ff0000>42</col>");

		assertEquals("Vorkath", vorkath.source);
		assertEquals(1234, vorkath.killCount);
		assertEquals("Tombs of Amascut: Expert Mode", raid.source);
		assertEquals(42, raid.killCount);
	}

	@Test
	public void ignoresNonKillCountMessages()
	{
		assertNull(KillCountMessageParser.parse("Your Strength level is: <col=ff0000>99</col>"));
	}
}
