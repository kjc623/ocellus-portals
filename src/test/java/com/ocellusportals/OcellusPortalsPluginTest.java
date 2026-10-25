package com.ocellusportals;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches RuneLite with this plugin loaded (run from an IDE with --developer-mode). */
public class OcellusPortalsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OcellusPortalsPlugin.class);
		RuneLite.main(args);
	}
}
