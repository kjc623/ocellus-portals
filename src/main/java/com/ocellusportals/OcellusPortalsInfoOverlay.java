package com.ocellusportals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.EnumSet;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Small panel: which sequence is running and which wave is up. */
class OcellusPortalsInfoOverlay extends OverlayPanel
{
	private static final Color ORANGE = new Color(255, 154, 46);
	private static final Color RED = new Color(255, 92, 99);
	private static final Color GREEN = new Color(63, 211, 127);

	private final OcellusPortalsPlugin plugin;
	private final OcellusPortalsConfig config;

	@Inject
	OcellusPortalsInfoOverlay(OcellusPortalsPlugin plugin, OcellusPortalsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showInfo() || !plugin.isInArena())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Ocellus portals").build());

		PortalSequences.Match m = plugin.getCurrent();
		EnumSet<PortalSequences.Colour> candidates = plugin.getCandidates();
		if (m == null)
		{
			String hint = candidates.size() == 1 ? "expect " + candidates.iterator().next().name().toLowerCase() : "reading wave 1";
			panelComponent.getChildren().add(LineComponent.builder().left("Sequence").right(hint).build());
		}
		else
		{
			String name = m.colour.name().charAt(0) + m.colour.name().substring(1).toLowerCase();
			if (candidates.size() > 1)
			{
				name += "?";
			}
			panelComponent.getChildren().add(LineComponent.builder().left("Sequence").right(name).rightColor(colourOf(m.colour)).build());
			panelComponent.getChildren().add(LineComponent.builder().left("Wave").right(m.wave + " / " + PortalSequences.WAVES).build());
			panelComponent.getChildren().add(LineComponent.builder().left("Then").right(m.colour.next().name().toLowerCase()).rightColor(colourOf(m.colour.next())).build());
		}
		return super.render(g);
	}

	private static Color colourOf(PortalSequences.Colour c)
	{
		switch (c)
		{
			case ORANGE:
				return ORANGE;
			case RED:
				return RED;
			default:
				return GREEN;
		}
	}
}
