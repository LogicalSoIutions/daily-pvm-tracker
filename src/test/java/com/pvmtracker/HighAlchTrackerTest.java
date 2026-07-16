package com.pvmtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HighAlchTrackerTest
{
	@Test
	public void confirmsOnlyWhenItemIsConsumedAndExactCoinsArrive()
	{
		HighAlchTracker tracker = new HighAlchTracker();
		tracker.begin(2, 4, 100, 600, 10);
		assertNull(tracker.observe(2, 4, 700, 10));
		HighAlchTracker.Confirmation confirmation = tracker.observe(2, 3, 700, 11);
		assertEquals(2, confirmation.itemId);
		assertEquals(600, confirmation.value);
	}

	@Test
	public void ignoresUnsuccessfulOrStaleCasts()
	{
		HighAlchTracker tracker = new HighAlchTracker();
		tracker.begin(2, 4, 100, 600, 10);
		assertNull(tracker.observe(2, 3, 699, 11));
		assertNull(tracker.observe(2, 3, 700, 13));
	}
}
