package com.pvmtracker;

import com.google.gson.Gson;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
final class PvmHubUploader
{
	static final String UPLOAD_URL = "https://osrs.pvm-hub.com/api/profiles";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private PvmHubUploader()
	{
	}

	interface UploadCallback
	{
		void onComplete(Call call, boolean successful, int responseCode);
	}

	static Call uploadSnapshot(TrackerData snapshot, Gson gson, OkHttpClient httpClient,
		UploadCallback callback)
	{
		String json = gson.toJson(snapshot);
		Request request = new Request.Builder()
			.url(UPLOAD_URL)
			.post(RequestBody.create(JSON, json))
			.build();
		Call call = httpClient.newCall(request);
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failedCall, IOException ex)
			{
				log.debug("PvM Hub upload failed", ex);
				callback.onComplete(failedCall, false, -1);
			}

			@Override
			public void onResponse(Call completedCall, Response response)
			{
				try
				{
					if (!response.isSuccessful())
					{
						log.debug("PvM Hub upload returned HTTP {}", response.code());
					}
					callback.onComplete(completedCall, response.isSuccessful(), response.code());
				}
				finally
				{
					response.close();
				}
			}
		});
		return call;
	}
}
