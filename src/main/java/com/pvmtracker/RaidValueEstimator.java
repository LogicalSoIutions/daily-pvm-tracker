package com.pvmtracker;

final class RaidValueEstimator
{
	private static final long[] COX_PRICES = {
		16_221_897L, 5_203_296L, 17_458_492L, 36_623_130L, 13_887_019L, 50_781_669L,
		119_452_472L, 79_231_172L, 39_342_629L, 98_300_118L, 62_461_234L, 1_487_048_509L
	};
	private static final int[] COX_WEIGHTS = {20, 20, 4, 4, 3, 3, 3, 3, 3, 2, 2, 2};
	private static final long[] TOB_PRICES = {
		30_598_999L, 25_376_978L, 22_319_722L, 10_386_274L, 9_702_590L, 9_629_648L,
		1_301_174_876L
	};
	private static final int[] TOB_NORMAL_WEIGHTS = {8, 2, 2, 2, 2, 2, 1};
	private static final int[] TOB_HARD_WEIGHTS = {7, 2, 2, 2, 2, 2, 1};

	private RaidValueEstimator()
	{
	}

	static Estimate estimate(String source, int lootPoints, int raidLevel)
	{
		if (source.startsWith("Chambers of Xeric"))
		{
			double chance = Math.max(0, lootPoints) / 867_600d;
			return new Estimate(chance, Math.round(chance * weightedAverage(COX_PRICES, COX_WEIGHTS)),
				"Point-based unique EV");
		}
		if (source.startsWith("Tombs of Amascut"))
		{
			int level = Math.max(0, raidLevel);
			double scaledLevel = level <= 310 ? level
				: level <= 430 ? 310d + (level - 310d) / 3d : 350d + (level - 430d) / 6d;
			double pointsPerPercent = Math.max(1d, 10_500d - 20d * scaledLevel);
			double chance = Math.min(.55d, Math.max(0, lootPoints) / (100d * pointsPerPercent));
			return new Estimate(chance, Math.round(chance * toaPurpleValue(level)),
				"Uncapped point and raid-level unique EV");
		}
		if (source.equals("Theatre of Blood: Hard Mode"))
		{
			double chance = 1d / 7.7d / 4d;
			return new Estimate(chance, Math.round(chance * weightedAverage(TOB_PRICES, TOB_HARD_WEIGHTS)),
				"Deathless 4-player unique baseline");
		}
		if (source.equals("Theatre of Blood"))
		{
			double chance = 1d / 9.1d / 4d;
			return new Estimate(chance, Math.round(chance * weightedAverage(TOB_PRICES, TOB_NORMAL_WEIGHTS)),
				"Deathless 4-player unique baseline");
		}
		return new Estimate(0d, 0L, "Unavailable");
	}

	private static double toaPurpleValue(int level)
	{
		int[] levels = {300, 350, 400, 450, 500};
		double[] values = {47_855_344.75d, 51_222_815.76d, 57_246_804d, 60_214_605.83d, 64_497_551.40d};
		if (level <= levels[0])
		{
			return values[0];
		}
		for (int i = 1; i < levels.length; i++)
		{
			if (level <= levels[i])
			{
				double fraction = (level - levels[i - 1]) / (double) (levels[i] - levels[i - 1]);
				return values[i - 1] + fraction * (values[i] - values[i - 1]);
			}
		}
		return values[values.length - 1];
	}

	private static double weightedAverage(long[] prices, int[] weights)
	{
		long totalWeight = 0;
		double totalValue = 0d;
		for (int i = 0; i < prices.length; i++)
		{
			totalWeight += weights[i];
			totalValue += (double) prices[i] * weights[i];
		}
		return totalValue / totalWeight;
	}

	static final class Estimate
	{
		final double uniqueChance;
		final long expectedUniqueValue;
		final String basis;

		private Estimate(double uniqueChance, long expectedUniqueValue, String basis)
		{
			this.uniqueChance = uniqueChance;
			this.expectedUniqueValue = expectedUniqueValue;
			this.basis = basis;
		}
	}
}
