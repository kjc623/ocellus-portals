package com.ocellusportals;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(OcellusPortalsConfig.GROUP)
public interface OcellusPortalsConfig extends Config
{
	String GROUP = "ocellusportals";

	@ConfigSection(name = "Safe tile", description = "Where the two orange portals cross", position = 0)
	String SAFE = "safe";

	@ConfigSection(name = "Next tile", description = "Prediction from the known sequences", position = 1)
	String NEXT = "next";

	@ConfigSection(name = "Other markers", description = "Floor portal, pools and info", position = 2)
	String OTHER = "other";

	@ConfigItem(keyName = "highlightSafe", name = "Highlight safe tiles", description = "Fill the tiles where the orange row and column lanes cross", section = SAFE, position = 0)
	default boolean highlightSafe()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "safeColor", name = "Safe colour", description = "Colour of the safe tile highlight", section = SAFE, position = 1)
	default Color safeColor()
	{
		return new Color(255, 154, 46, 255);
	}

	@Range(min = 0, max = 255)
	@ConfigItem(keyName = "safeFill", name = "Safe fill opacity", description = "0 = outline only, 255 = solid", section = SAFE, position = 2)
	default int safeFill()
	{
		return 90;
	}

	@ConfigItem(keyName = "lineToSafe", name = "Line to safe tile", description = "Draw a line from you to the safe tile", section = SAFE, position = 3)
	default boolean lineToSafe()
	{
		return false;
	}

	@ConfigItem(keyName = "showNext", name = "Show next tile", description = "Once the sequence is recognised, outline the tile for the following wave", section = NEXT, position = 0)
	default boolean showNext()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "nextColor", name = "Next colour", description = "Colour of the next-tile outline", section = NEXT, position = 1)
	default Color nextColor()
	{
		return new Color(255, 255, 255, 200);
	}

	@ConfigItem(keyName = "showRemaining", name = "Show remaining waves", description = "Also mark every later wave of the recognised sequence, with its wave number", section = NEXT, position = 2)
	default boolean showRemaining()
	{
		return false;
	}

	@ConfigItem(keyName = "highlightFloorPortal", name = "Highlight floor portal", description = "Mark the orange floor portal that moves you to the next quadrant", section = OTHER, position = 0)
	default boolean highlightFloorPortal()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "floorPortalColor", name = "Floor portal colour", description = "Colour of the floor portal marker", section = OTHER, position = 1)
	default Color floorPortalColor()
	{
		return new Color(60, 220, 120, 255);
	}

	@ConfigItem(keyName = "highlightPools", name = "Highlight purple pools", description = "Tint the purple pools that appear before the floor portal", section = OTHER, position = 2)
	default boolean highlightPools()
	{
		return true;
	}

	@ConfigItem(keyName = "showInfo", name = "Show info panel", description = "Small panel with the recognised sequence and wave", section = OTHER, position = 3)
	default boolean showInfo()
	{
		return true;
	}

	@ConfigItem(keyName = "debug", name = "Debug", description = "Draw tracked object ids on the floor (for reporting problems)", section = OTHER, position = 4)
	default boolean debug()
	{
		return false;
	}
}
