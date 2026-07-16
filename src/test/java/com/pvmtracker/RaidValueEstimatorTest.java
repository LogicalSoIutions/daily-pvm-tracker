package com.pvmtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RaidValueEstimatorTest
{
	@Test
	public void chambersChanceScalesDirectlyWithPersonalPoints()
	{
		RaidValueEstimator.Estimate estimate = RaidValueEstimator.estimate("Chambers of Xeric", 26_028, 0);

		assertEquals(.03d, estimate.uniqueChance, .0000001d);
		assertTrue(estimate.expectedUniqueValue > 0);
		assertEquals("Point-based unique EV", estimate.basis);
	}

	@Test
	public void tombsUsesRaidLevelScaling()
	{
		RaidValueEstimator.Estimate estimate = RaidValueEstimator.estimate("Tombs of Amascut: Expert Mode",
			37_000, 400);

		assertEquals(.10d, estimate.uniqueChance, .0000001d);
		assertEquals(5_724_680L, estimate.expectedUniqueValue);
	}

	@Test
	public void theatreIsExplicitlyABaselineRatherThanFabricatedPoints()
	{
		RaidValueEstimator.Estimate estimate = RaidValueEstimator.estimate("Theatre of Blood", 0, 0);

		assertEquals(1d / 9.1d / 4d, estimate.uniqueChance, .0000001d);
		assertTrue(estimate.expectedUniqueValue > 0);
		assertTrue(estimate.basis.contains("baseline"));
	}
}
