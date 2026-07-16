package com.pvmtracker;

final class ItemValuation
{
	private ItemValuation()
	{
	}

	static long unitValue(int gePrice, int highAlchPrice, boolean highAlchOnly)
	{
		long safeGePrice = Math.max(0, gePrice);
		long safeHighAlchPrice = Math.max(0, highAlchPrice);
		return highAlchOnly || safeGePrice == 0 ? safeHighAlchPrice : safeGePrice;
	}
}
