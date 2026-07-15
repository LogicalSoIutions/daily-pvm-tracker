package com.pvmtracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LootTransformationService
{
	private LootTransformationService()
	{
	}

	static boolean isSupported(String source)
	{
		return source != null && source.toLowerCase(java.util.Locale.ENGLISH).startsWith("tarnished ");
	}

	static boolean replace(TrackerData data, String intermediateName, List<ReplacementItem> replacements)
	{
		List<Lot> lots = new ArrayList<>();
		data.lootDays.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(day ->
		{
			day.getValue().sources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(source ->
			{
				source.getValue().items.values().stream()
					.filter(item -> intermediateName.equalsIgnoreCase(item.name) && item.quantity > item.confirmedQuantity)
					.forEach(item -> lots.add(new Lot(source.getValue(), item)));
			});
		});

		boolean changed = false;
		int lotIndex = 0;
		for (ReplacementItem replacement : replacements)
		{
			long remaining = replacement.quantity;
			long unitValue = replacement.quantity == 0 ? 0 : replacement.totalValue / replacement.quantity;
			while (remaining > 0 && lotIndex < lots.size())
			{
				Lot lot = lots.get(lotIndex);
				long available = lot.intermediate.quantity - lot.intermediate.confirmedQuantity;
				if (available <= 0)
				{
					lotIndex++;
					continue;
				}
				long take = Math.min(remaining, available);
				long oldUnitValue = lot.intermediate.quantity == 0 ? 0
					: lot.intermediate.totalValue / lot.intermediate.quantity;
				long oldValue = oldUnitValue * take;
				long newValue = unitValue * take;
				lot.intermediate.quantity -= take;
				lot.intermediate.totalValue -= oldValue;
				TrackerData.LootItem revealed = lot.source.items.computeIfAbsent(replacement.itemId,
					ignored -> new TrackerData.LootItem(replacement.itemId, replacement.name));
				revealed.quantity += take;
				revealed.totalValue += newValue;
				lot.source.totalValue += newValue - oldValue;
				if (lot.intermediate.quantity == 0 && lot.intermediate.confirmedQuantity == 0)
				{
					lot.source.items.remove(lot.intermediate.itemId);
					lotIndex++;
				}
				remaining -= take;
				changed = true;
			}
		}
		return changed;
	}

	static final class ReplacementItem
	{
		final int itemId;
		final String name;
		final long quantity;
		final long totalValue;

		ReplacementItem(int itemId, String name, long quantity, long totalValue)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.totalValue = totalValue;
		}
	}

	private static final class Lot
	{
		private final TrackerData.LootSource source;
		private final TrackerData.LootItem intermediate;

		private Lot(TrackerData.LootSource source, TrackerData.LootItem intermediate)
		{
			this.source = source;
			this.intermediate = intermediate;
		}
	}
}
