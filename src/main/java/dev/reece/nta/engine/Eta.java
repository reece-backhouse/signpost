package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Route;

/**
 * RL-012 (spec ruling 34): the time left to a skill target at the player's observed xp rate.
 * Pure arithmetic over data the plugin passes in ({@link dev.reece.nta.engine.model.Advice#getXpPerHour()}).
 */
public final class Eta
{
	private Eta()
	{
	}

	/** The xp still to train: after the route's bank steps when a route exists, else the raw gap. */
	public static long remainingXp(Route route, long gapXp)
	{
		return route != null ? route.getUncoveredXp() : gapXp;
	}

	/** {@code "about 45 min at 38k/h"} (hours split out past 60 min), or {@code null} without a rate or with nothing left. */
	public static String text(long remainingXp, Long xpPerHour)
	{
		if (xpPerHour == null || xpPerHour <= 0 || remainingXp <= 0)
		{
			return null;
		}
		long minutes = (remainingXp * 60 + xpPerHour - 1) / xpPerHour;
		String time = minutes < 60 ? minutes + " min"
			: minutes % 60 == 0 ? (minutes / 60) + " h"
			: (minutes / 60) + " h " + (minutes % 60) + " min";
		String rate = xpPerHour < 1000 ? xpPerHour + "/h" : ((xpPerHour + 500) / 1000) + "k/h";
		return "about " + time + " at " + rate;
	}
}
