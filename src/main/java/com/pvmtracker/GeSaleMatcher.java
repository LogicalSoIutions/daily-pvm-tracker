package com.pvmtracker;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GeSaleMatcher
{
	enum ConfirmationMethod
	{
		GRAND_EXCHANGE,
		HIGH_ALCHEMY
	}

	private GeSaleMatcher()
	{
	}

	static MatchResult match(TrackerData data, int itemId, int soldQuantity, long proceeds)
	{
		return match(data, itemId, soldQuantity, proceeds, ConfirmationMethod.GRAND_EXCHANGE);
	}

	static MatchResult match(TrackerData data, int itemId, int confirmedQuantity, long proceeds,
		ConfirmationMethod method)
	{
		if (confirmedQuantity <= 0 || proceeds <= 0)
		{
			return new MatchResult(0, 0);
		}

		if (method == ConfirmationMethod.GRAND_EXCHANGE)
		{
			proceeds = GrandExchangeTax.netProceeds(confirmedQuantity, proceeds);
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
						lots.add(new Lot(day.getKey(), source.getKey(), item));
					}
				}));

		long available = lots.stream().mapToLong(lot -> lot.item.quantity - lot.item.confirmedQuantity).sum();
		long matchedQuantity = Math.min(available, confirmedQuantity);
		if (matchedQuantity <= 0)
		{
			return new MatchResult(0, 0);
		}
		long matchedProceeds = prorate(proceeds, matchedQuantity, confirmedQuantity);
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
			matchKillLog(data, lot.date, lot.source, itemId, take, allocation);
			if (method == ConfirmationMethod.HIGH_ALCHEMY)
			{
				lot.item.alchConfirmedQuantity += take;
				lot.item.alchConfirmedValue += allocation;
			}
			else
			{
				lot.item.geConfirmedQuantity += take;
				lot.item.geConfirmedValue += allocation;
			}
			remainingQuantity -= take;
			remainingProceeds -= allocation;
			if (remainingQuantity == 0)
			{
				break;
			}
		}
		return new MatchResult(matchedQuantity, matchedProceeds);
	}

	private static void matchKillLog(TrackerData data, String date, String source, int itemId,
		long quantity, long proceeds)
	{
		long remainingQuantity = quantity;
		long remainingProceeds = proceeds;
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			if (remainingQuantity == 0 || !date.equals(kill.date) || !source.equals(kill.source))
			{
				continue;
			}
			for (TrackerData.KillLootItem item : kill.items)
			{
				if (item.itemId != itemId || item.quantity <= item.confirmedQuantity)
				{
					continue;
				}
				long take = Math.min(remainingQuantity, item.quantity - item.confirmedQuantity);
				long allocation = take == remainingQuantity
					? remainingProceeds : prorate(proceeds, take, quantity);
				item.confirmedQuantity += take;
				item.confirmedValue += allocation;
				remainingQuantity -= take;
				remainingProceeds -= allocation;
				if (remainingQuantity == 0)
				{
					return;
				}
			}
		}
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
		private final String date;
		private final String source;
		private final TrackerData.LootItem item;

		private Lot(String date, String source, TrackerData.LootItem item)
		{
			this.date = date;
			this.source = source;
			this.item = item;
		}
	}
}
