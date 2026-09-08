package dev.reece.nta;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("nexttarget")
public interface NextTargetConfig extends Config
{
	@Range(min = 1, max = 90)
	@ConfigItem(
		keyName = "snoozeDays",
		name = "Snooze duration (days)",
		description = "How many days a goal you clicked 'Not now' on stays hidden by default."
	)
	default int snoozeDays()
	{
		return 7;
	}

	@ConfigItem(
		keyName = "countGroupStorage",
		name = "Count group storage",
		description = "Group ironman: treat items in the group's shared storage as owned for readiness, routes and shortfalls. Off stops reading it; the last seen copy is kept.",
		position = 1
	)
	default boolean countGroupStorage()
	{
		return true;
	}
}
