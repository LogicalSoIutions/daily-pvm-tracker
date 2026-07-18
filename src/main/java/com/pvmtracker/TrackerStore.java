package com.pvmtracker;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.runelite.client.RuneLite;

final class TrackerStore
{
	private static final int PROFILE_ID_VERSION = 1;
	private final Gson gson;
	private final Path directory;

	TrackerStore(Gson gson)
	{
		this(gson, RuneLite.RUNELITE_DIR.toPath().resolve("pvm-raid-daily-tracker"));
	}

	TrackerStore(Gson gson, Path directory)
	{
		this.gson = gson.newBuilder().setPrettyPrinting().create();
		this.directory = directory;
	}

	TrackerData load(long accountHash) throws IOException
	{
		Path file = fileFor(accountHash);
		if (!Files.exists(file))
		{
			return new TrackerData();
		}
		try (Reader reader = Files.newBufferedReader(file))
		{
			TrackerData data = gson.fromJson(reader, TrackerData.class);
			if (data == null)
			{
				data = new TrackerData();
			}
			data.schemaVersion = 3;
			TrackerDataEditor.recalculateAllSourceTotals(data);
			return data;
		}
	}

	void save(long accountHash, TrackerData data) throws IOException
	{
		Files.createDirectories(directory);
		Path destination = fileFor(accountHash);
		writeAtomically(destination, data);
	}

	void export(Path destination, TrackerData data) throws IOException
	{
		writeAtomically(destination, data);
	}

	private void writeAtomically(Path destination, TrackerData data) throws IOException
	{
		Path parent = destination.toAbsolutePath().getParent();
		if (parent == null)
		{
			throw new IOException("Export destination has no parent directory");
		}
		Files.createDirectories(parent);
		Path staging = Files.createTempFile(parent, destination.getFileName().toString(), ".new");
		try
		{
			try (Writer writer = Files.newBufferedWriter(staging))
			{
				gson.toJson(data, writer);
			}
			try
			{
				Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ex)
			{
				Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			Files.deleteIfExists(staging);
		}
	}

	private Path fileFor(long accountHash)
	{
		return directory.resolve(Long.toUnsignedString(accountHash) + "-v" + PROFILE_ID_VERSION + ".json");
	}
}
