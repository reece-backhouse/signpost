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
}
