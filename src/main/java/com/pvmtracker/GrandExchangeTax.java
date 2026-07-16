package com.pvmtracker;

final class GrandExchangeTax
{
	private static final long TAX_DIVISOR = 50L;
	private static final long MAX_TAX_PER_ITEM = 5_000_000L;

	private GrandExchangeTax()
	{
	}

	static long netProceeds(long quantity, long grossProceeds)
	{
		if (quantity <= 0 || grossProceeds <= 0)
		{
			return grossProceeds;
		}

		long unitPrice = grossProceeds / quantity;
		long higherPriceQuantity = grossProceeds % quantity;
		long lowerPriceQuantity = quantity - higherPriceQuantity;
		long tax = lowerPriceQuantity * taxPerItem(unitPrice)
			+ higherPriceQuantity * taxPerItem(unitPrice + 1L);
		return grossProceeds - tax;
	}

	private static long taxPerItem(long price)
	{
		return Math.min(price / TAX_DIVISOR, MAX_TAX_PER_ITEM);
	}
}
