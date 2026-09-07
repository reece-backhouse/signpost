package dev.reece.nta;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Next Target Advisor",
	description = "Suggests what to focus on next",
	tags = {"quest", "diary", "skilling"}
)
public class NextTargetPlugin extends Plugin
{
	@Override
	protected void startUp() throws Exception
	{
		log.info("Next Target Advisor started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Next Target Advisor stopped!");
	}
}
