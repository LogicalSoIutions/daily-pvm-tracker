package com.pvmtracker;

import com.google.gson.GsonBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PvmHubUploaderTest
{
	@Test
	public void uploadsCompleteSnapshotAsJson() throws Exception
	{
		AtomicReference<Request> capturedRequest = new AtomicReference<>();
		AtomicReference<String> capturedBody = new AtomicReference<>();
		OkHttpClient client = new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				Request request = chain.request();
				capturedRequest.set(request);
				Buffer buffer = new Buffer();
				request.body().writeTo(buffer);
				capturedBody.set(buffer.readUtf8());
				return new Response.Builder()
					.request(request)
					.protocol(Protocol.HTTP_1_1)
					.code(201)
					.message("Created")
					.body(ResponseBody.create(null, ""))
					.build();
			})
			.build();
		TrackerData snapshot = new TrackerData();
		snapshot.lastKnownName = "Logical";
		snapshot.lastKnownKillCounts.put("Vorkath", 42);
		CompletableFuture<Integer> result = new CompletableFuture<>();

		PvmHubUploader.uploadSnapshot(snapshot, new GsonBuilder().create(), client,
			(call, successful, responseCode) ->
			{
				if (successful)
				{
					result.complete(responseCode);
				}
				else
				{
					result.completeExceptionally(new AssertionError("Upload failed"));
				}
			});

		assertEquals(Integer.valueOf(201), result.get(5, TimeUnit.SECONDS));
		assertEquals(PvmHubUploader.UPLOAD_URL, capturedRequest.get().url().toString());
		assertEquals("POST", capturedRequest.get().method());
		assertEquals("application/json; charset=utf-8", capturedRequest.get().body().contentType().toString());
		TrackerData uploaded = new GsonBuilder().create().fromJson(capturedBody.get(), TrackerData.class);
		assertEquals("Logical", uploaded.lastKnownName);
		assertEquals(Integer.valueOf(42), uploaded.lastKnownKillCounts.get("Vorkath"));
	}

	@Test
	public void uploadOptionIsOptInAndCarriesRequiredWarning() throws Exception
	{
		DailyPvmTrackerConfig config = new DailyPvmTrackerConfig()
		{
		};
		assertFalse(config.uploadToPvmHub());
		assertTrue(config.showEmptyDays());
		ConfigItem item = DailyPvmTrackerConfig.class.getMethod("uploadToPvmHub")
			.getAnnotation(ConfigItem.class);
		assertEquals("This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
			item.warning());
	}
}
