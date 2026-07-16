package com.pvmtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ItemValuationTest
{
	@Test
	public void usesGeWithHighAlchFallback()
	{
		assertEquals(1_000L, ItemValuation.unitValue(1_000, 600, false));
		assertEquals(600L, ItemValuation.unitValue(0, 600, false));
	}

	@Test
	public void supportsHighAlchOnlyValuation()
	{
		assertEquals(600L, ItemValuation.unitValue(1_000, 600, true));
	}
}
