package com.pvmtracker;

import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class TrackerStoreTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void roundTripsSeparateCharacterFiles() throws Exception
	{
		Path directory = temporaryFolder.newFolder("daily-pvm-tracker").toPath();
		TrackerStore store = new TrackerStore(new GsonBuilder().create(), directory);
		TrackerData first = new TrackerData();
		first.lastKnownName = "One";
		first.lastKnownKillCounts.put("Vorkath", 42);
		TrackerData second = new TrackerData();
		second.lastKnownName = "Two";
		TrackerData.LootDay lootDay = new TrackerData.LootDay();
		lootDay.manualAdjustments.put("Nex", 12_500_000L);
		first.lootDays.put("2026-07-15", lootDay);
		TrackerData.RaidCompletion raid = new TrackerData.RaidCompletion();
		raid.id = "raid-1";
		raid.date = "2026-07-15";
		raid.source = "Chambers of Xeric";
		raid.personalPoints = 26_028;
		raid.expectedUniqueValue = 1_000_000L;
		first.raidCompletions.add(raid);

		store.save(11L, first);
		store.save(22L, second);

		TrackerData loadedFirst = store.load(11L);
		TrackerData loadedSecond = store.load(22L);
		assertEquals(1, loadedFirst.schemaVersion);
		assertEquals("One", loadedFirst.lastKnownName);
		assertEquals(Integer.valueOf(42), loadedFirst.lastKnownKillCounts.get("Vorkath"));
		assertEquals(Long.valueOf(12_500_000L), loadedFirst.lootDays.get("2026-07-15").manualAdjustments.get("Nex"));
		assertEquals(Integer.valueOf(26_028), loadedFirst.raidCompletions.get(0).personalPoints);
		assertEquals("Two", loadedSecond.lastKnownName);
		assertNotSame(loadedFirst, loadedSecond);
	}

	@Test
	public void appendsVersionToGeneratedProfileFileId() throws Exception
	{
		Path directory = temporaryFolder.newFolder("daily-pvm-tracker-versioned").toPath();
		TrackerStore store = new TrackerStore(new GsonBuilder().create(), directory);

		store.save(11L, new TrackerData());

		assertTrue(Files.exists(directory.resolve("11-v1.json")));
	}

	@Test
	public void ignoresUnversionedProfileFiles() throws Exception
	{
		Path directory = temporaryFolder.newFolder("daily-pvm-tracker-unversioned").toPath();
		TrackerStore store = new TrackerStore(new GsonBuilder().create(), directory);
		TrackerData oldData = new TrackerData();
		oldData.lastKnownName = "Old profile";
		try (java.io.Writer writer = Files.newBufferedWriter(directory.resolve("11.json")))
		{
			new GsonBuilder().create().toJson(oldData, writer);
		}

		assertEquals("", store.load(11L).lastKnownName);
	}

	@Test
	public void exportsWebsiteCompatibleSnapshot() throws Exception
	{
		Path directory = temporaryFolder.newFolder("daily-pvm-tracker-export").toPath();
		TrackerStore store = new TrackerStore(new GsonBuilder().create(), directory);
		TrackerData data = new TrackerData();
		data.lastKnownName = "Logical";
		data.lastKnownKillCounts.put("Vorkath", 42);
		Path destination = temporaryFolder.getRoot().toPath().resolve("daily-pvm-tracker-Logical.json");

		store.export(destination, data);

		assertTrue(java.nio.file.Files.exists(destination));
		TrackerData exported;
		try (Reader reader = java.nio.file.Files.newBufferedReader(destination))
		{
			exported = new GsonBuilder().create().fromJson(reader, TrackerData.class);
		}
		assertEquals(1, exported.schemaVersion);
		assertEquals("Logical", exported.lastKnownName);
		assertEquals(Integer.valueOf(42), exported.lastKnownKillCounts.get("Vorkath"));
	}

	@Test
	public void persistsHiddenLootRulesAndRecalculatesTotals() throws Exception
	{
		Path directory = temporaryFolder.newFolder("daily-pvm-tracker-hidden").toPath();
		TrackerStore store = new TrackerStore(new GsonBuilder().create(), directory);
		TrackerData data = new TrackerData();
		TrackerData.LootDay day = new TrackerData.LootDay();
		TrackerData.LootSource source = new TrackerData.LootSource();
		TrackerData.LootItem visible = new TrackerData.LootItem(1, "Visible");
		visible.quantity = 1;
		visible.totalValue = 1_000L;
		TrackerData.LootItem hidden = new TrackerData.LootItem(2, "Hidden");
		hidden.quantity = 1;
		hidden.totalValue = 500L;
		source.items.put(1, visible);
		source.items.put(2, hidden);
		source.totalValue = 9_999L;
		day.sources.put("Vorkath", source);
		data.lootDays.put("2026-07-15", day);
		data.hiddenLootFor("Vorkath").add(2);
		store.save(11L, data);

		TrackerData loaded = store.load(11L);
		assertTrue(loaded.isLootHidden("Vorkath", 2));
		assertEquals(1_000L, loaded.lootDays.get("2026-07-15").sources.get("Vorkath").totalValue);
	}
}
