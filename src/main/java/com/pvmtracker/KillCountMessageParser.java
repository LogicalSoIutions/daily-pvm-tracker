package com.pvmtracker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KillCountMessageParser
{
	private static final Pattern KILLCOUNT_PATTERN = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? "
			+ "(?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?"
			+ "<col=[0-9a-f]{6}>(?<kc>[0-9,]+)</col>");

	private KillCountMessageParser()
	{
	}

	static Observation parse(String message)
	{
		Matcher matcher = KILLCOUNT_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return null;
		}
		String pre = matcher.group("pre");
		String post = matcher.group("post");
		if ((pre == null || pre.isEmpty()) && (post == null || post.isEmpty()))
		{
			return null;
		}
		String source = BossRegistry.canonicalize(matcher.group("boss"));
		if (source == null)
		{
			return null;
		}
		int killCount = Integer.parseInt(matcher.group("kc").replace(",", ""));
		return new Observation(source, killCount);
	}

	static final class Observation
	{
		final String source;
		final int killCount;

		private Observation(String source, int killCount)
		{
			this.source = source;
			this.killCount = killCount;
		}
	}
}
