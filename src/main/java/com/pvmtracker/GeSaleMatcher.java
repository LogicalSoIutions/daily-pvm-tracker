package com.pvmtracker;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GeSaleMatcher
{
	private GeSaleMatcher()
	{
	}

	static MatchResult match(TrackerData data, int itemId, int soldQuantity, long proceeds)
	{
		if (soldQuantity <= 0 || proceeds <= 0)
		{
			return new MatchResult(0, 0);
		}

		List<Lot> lots = new ArrayList<>();
		data.lootDays.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(day -> day.getValue().sources.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(source ->
				{
					TrackerData.LootItem item = source.getValue().items.get(itemId);
					if (item != null && item.quantity > item.confirmedQuantity)
					{
						lots.add(new Lot(item));
					}
				}));

		long available = lots.stream().mapToLong(lot -> lot.item.quantity - lot.item.confirmedQuantity).sum();
		long matchedQuantity = Math.min(available, soldQuantity);
		if (matchedQuantity <= 0)
		{
			return new MatchResult(0, 0);
		}
		long matchedProceeds = prorate(proceeds, matchedQuantity, soldQuantity);
		long remainingQuantity = matchedQuantity;
		long remainingProceeds = matchedProceeds;
		for (Lot lot : lots)
		{
			long take = Math.min(remainingQuantity, lot.item.quantity - lot.item.confirmedQuantity);
			if (take <= 0)
			{
				continue;
			}
			long allocation = take == remainingQuantity
				? remainingProceeds : prorate(matchedProceeds, take, matchedQuantity);
			lot.item.confirmedQuantity += take;
			lot.item.confirmedValue += allocation;
			remainingQuantity -= take;
			remainingProceeds -= allocation;
			if (remainingQuantity == 0)
			{
				break;
			}
		}
		return new MatchResult(matchedQuantity, matchedProceeds);
	}

	private static long prorate(long value, long numerator, long denominator)
	{
		return BigInteger.valueOf(value).multiply(BigInteger.valueOf(numerator))
			.divide(BigInteger.valueOf(denominator)).longValueExact();
	}

	static final class MatchResult
	{
		final long quantity;
		final long value;

		private MatchResult(long quantity, long value)
		{
			this.quantity = quantity;
			this.value = value;
		}
	}

	private static final class Lot
	{
		private final TrackerData.LootItem item;

		private Lot(TrackerData.LootItem item)
		{
			this.item = item;
		}
	}
}
