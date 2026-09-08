package dev.reece.nta;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NextTargetPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NextTargetPlugin.class);
		RuneLite.main(args);
	}
}
