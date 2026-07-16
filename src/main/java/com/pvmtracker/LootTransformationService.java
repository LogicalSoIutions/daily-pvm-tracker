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
					.forEach(item -> lots.add(new Lot(day.getKey(), source.getKey(), source.getValue(), item)));
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
				replaceKillLogItems(data, lot.date, lot.sourceName, intermediateName,
					replacement, take, unitValue);
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
		if (changed)
		{
			TrackerDataEditor.recalculateAllSourceTotals(data);
		}
		return changed;
	}

	private static void replaceKillLogItems(TrackerData data, String date, String sourceName,
		String intermediateName, ReplacementItem replacement, long quantity, long replacementUnitValue)
	{
		long remaining = quantity;
		for (TrackerData.KillLogEntry kill : data.killLog)
		{
			if (remaining == 0 || !date.equals(kill.date) || !sourceName.equals(kill.source))
			{
				continue;
			}
			for (int i = 0; i < kill.items.size() && remaining > 0; i++)
			{
				TrackerData.KillLootItem intermediate = kill.items.get(i);
				if (!intermediateName.equalsIgnoreCase(intermediate.name) || intermediate.quantity <= 0)
				{
					continue;
				}
				long take = Math.min(remaining, intermediate.quantity);
				long oldUnitValue = intermediate.totalValue / intermediate.quantity;
				intermediate.quantity -= take;
				intermediate.totalValue -= oldUnitValue * take;
				if (intermediate.quantity == 0)
				{
					kill.items.remove(i--);
				}
				TrackerData.KillLootItem revealed = kill.items.stream()
					.filter(item -> item.itemId == replacement.itemId)
					.findFirst()
					.orElseGet(() ->
					{
						TrackerData.KillLootItem item = new TrackerData.KillLootItem(
							replacement.itemId, replacement.name, 0, 0);
						kill.items.add(item);
						return item;
					});
				revealed.quantity += take;
				revealed.totalValue += replacementUnitValue * take;
				remaining -= take;
			}
		}
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
		private final String date;
		private final String sourceName;
		private final TrackerData.LootSource source;
		private final TrackerData.LootItem intermediate;

		private Lot(String date, String sourceName, TrackerData.LootSource source,
			TrackerData.LootItem intermediate)
		{
			this.date = date;
			this.sourceName = sourceName;
			this.source = source;
			this.intermediate = intermediate;
		}
	}
}
