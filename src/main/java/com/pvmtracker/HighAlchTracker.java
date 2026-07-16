package com.pvmtracker;

final class HighAlchTracker
{
	private Pending pending;

	void begin(int itemId, long itemQuantity, long coinQuantity, int highAlchPrice, int tick)
	{
		pending = itemId < 0 || itemQuantity <= 0 || highAlchPrice <= 0
			? null : new Pending(itemId, itemQuantity, coinQuantity, highAlchPrice, tick);
	}

	Confirmation observe(int itemId, long itemQuantity, long coinQuantity, int tick)
	{
		Pending current = pending;
		if (current == null)
		{
			return null;
		}
		if (tick - current.tick > 2)
		{
			pending = null;
			return null;
		}
		if (itemId != current.itemId || itemQuantity >= current.itemQuantity
			|| coinQuantity - current.coinQuantity != current.highAlchPrice)
		{
			return null;
		}
		pending = null;
		return new Confirmation(current.itemId, current.highAlchPrice);
	}

	int pendingItemId()
	{
		return pending == null ? -1 : pending.itemId;
	}

	void clear()
	{
		pending = null;
	}

	static final class Confirmation
	{
		final int itemId;
		final long value;

		private Confirmation(int itemId, long value)
		{
			this.itemId = itemId;
			this.value = value;
		}
	}

	private static final class Pending
	{
		private final int itemId;
		private final long itemQuantity;
		private final long coinQuantity;
		private final int highAlchPrice;
		private final int tick;

		private Pending(int itemId, long itemQuantity, long coinQuantity, int highAlchPrice, int tick)
		{
			this.itemId = itemId;
			this.itemQuantity = itemQuantity;
			this.coinQuantity = coinQuantity;
			this.highAlchPrice = highAlchPrice;
			this.tick = tick;
		}
	}
}
