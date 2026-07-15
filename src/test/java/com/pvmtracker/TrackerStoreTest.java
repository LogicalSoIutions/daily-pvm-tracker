package com.pvmtracker;

import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.nio.file.Path;
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

		store.save(11L, first);
		store.save(22L, second);

		TrackerData loadedFirst = store.load(11L);
		TrackerData loadedSecond = store.load(22L);
		assertEquals(1, loadedFirst.schemaVersion);
		assertEquals("One", loadedFirst.lastKnownName);
		assertEquals(Integer.valueOf(42), loadedFirst.lastKnownKillCounts.get("Vorkath"));
		assertEquals(Long.valueOf(12_500_000L), loadedFirst.lootDays.get("2026-07-15").manualAdjustments.get("Nex"));
		assertEquals("Two", loadedSecond.lastKnownName);
		assertNotSame(loadedFirst, loadedSecond);
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
}
