package com.pvmtracker;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DailySummaryServiceTest
{
	private final DailySummaryService summaryService = new DailySummaryService();
	private final KillCountService killCountService = new KillCountService();

	@Test
	public void buildsLocalKillTotalsAndMergesLootByDay()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Vorkath", 100);
		data.lastKnownKillCounts.put("Tombs of Amascut", 20);
		LocalDate date = LocalDate.parse("2026-07-14");
		killCountService.recordCompletion(data, date, "Vorkath", 106);
		killCountService.recordCompletion(data, date, "Tombs of Amascut", 22);

		TrackerData.LootDay day = new TrackerData.LootDay();
		TrackerData.LootSource source = new TrackerData.LootSource();
		source.drops = 6;
		source.totalValue = 1_250_000;
		TrackerData.LootItem item = new TrackerData.LootItem(11286, "Draconic visage");
		item.quantity = 1;
		item.totalValue = 1_250_000;
		item.confirmedQuantity = 1;
		item.confirmedValue = 1_100_000;
		source.items.put(item.itemId, item);
		day.sources.put("Vorkath", source);
		day.manualAdjustments.put("Vorkath", 500_000L);
		data.lootDays.put(date.toString(), day);

		List<DailySummary> summaries = summaryService.build(data, LocalDate.parse("2026-07-15"), true);
		DailySummary completed = summaries.stream().filter(summary -> summary.date.equals(date))
			.findFirst().orElseThrow(AssertionError::new);

		assertEquals(8, completed.totalKills);
		assertEquals(Integer.valueOf(6), completed.kills.get("Vorkath"));
		assertEquals(Integer.valueOf(2), completed.kills.get("Tombs of Amascut"));
		assertEquals(6, completed.totalRecoveredKills);
		assertEquals(1_250_000, completed.trackedLootValue);
		assertEquals(1_600_000, completed.confirmedValue);
		assertEquals(Integer.valueOf(100), completed.startingKillCounts.get("Vorkath"));
		assertEquals(Integer.valueOf(106), completed.endingKillCounts.get("Vorkath"));
	}

	@Test
	public void labelsHiscoreRecoveryAcrossAnOfflineInterval()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Zulrah", 10);
		data.lastKnownKillCountsAt = "2026-07-10";

		int recovered = killCountService.reconcile(data, LocalDate.parse("2026-07-13"), counts("Zulrah", 15));
		DailySummary summary = summaryService.build(data, LocalDate.parse("2026-07-13"), true).stream()
			.filter(day -> day.date.equals(LocalDate.parse("2026-07-10")))
			.findFirst().orElseThrow(AssertionError::new);

		assertEquals(5, recovered);
		assertEquals(3, summary.intervalDays());
		assertEquals(5, summary.totalKills);
		assertEquals(5, summary.totalRecoveredKills);
	}

	@Test
	public void laggingHiscoresNeverReduceLocalState()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Vorkath", 100);
		data.lastKnownKillCountsAt = "2026-07-15";

		assertEquals(0, killCountService.reconcile(data, LocalDate.parse("2026-07-15"), counts("Vorkath", 99)));
		assertEquals(Integer.valueOf(100), data.lastKnownKillCounts.get("Vorkath"));
		assertTrue(data.kcDays.isEmpty());
	}

	@Test
	public void lootFallbackAdvancesAKnownAbsoluteCount()
	{
		TrackerData data = new TrackerData();
		data.lastKnownKillCounts.put("Vorkath", 100);
		LocalDate today = LocalDate.parse("2026-07-15");

		killCountService.recordCompletion(data, today, "Vorkath", null);
		DailySummary live = summaryService.build(data, today, true).get(0);

		assertEquals(1, live.totalKills);
		assertEquals(Integer.valueOf(100), live.startingKillCounts.get("Vorkath"));
		assertEquals(Integer.valueOf(101), live.endingKillCounts.get("Vorkath"));
	}

	private static Map<String, Integer> counts(Object... values)
	{
		Map<String, Integer> result = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2)
		{
			result.put((String) values[i], (Integer) values[i + 1]);
		}
		return result;
	}
}
