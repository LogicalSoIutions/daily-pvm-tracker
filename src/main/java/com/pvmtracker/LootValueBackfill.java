package com.pvmtracker;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class LootValueBackfill
{
	private LootValueBackfill()
	{
	}

	static Set<Integer> missingItemIds(TrackerData data)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (TrackerData.LootDay day : data.lootDays.values())
		{
			for (TrackerData.LootSource source : day.sources.values())
			{
				for (TrackerData.LootItem item : source.items.values())
				{
					if (item.quantity > 0 && item.totalValue == 0)
					{
						ids.add(item.itemId);
					}
				}
			}
		}
		return ids;
	}

	static boolean apply(TrackerData data, Map<Integer, Long> unitValues)
	{
		boolean changed = false;
		for (TrackerData.LootDay day : data.lootDays.values())
		{
			for (TrackerData.LootSource source : day.sources.values())
			{
				for (TrackerData.LootItem item : source.items.values())
				{
					long unitValue = unitValues.getOrDefault(item.itemId, 0L);
					if (item.quantity > 0 && item.totalValue == 0 && unitValue > 0)
					{
						item.totalValue = unitValue * item.quantity;
						changed = true;
					}
				}
			}
		}
		if (changed)
		{
			TrackerDataEditor.recalculateAllSourceTotals(data);
		}
		return changed;
	}
}
