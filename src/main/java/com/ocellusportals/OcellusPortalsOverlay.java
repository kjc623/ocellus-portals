package com.ocellusportals;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Area;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class OcellusPortalsOverlay extends Overlay
{
	private static final Stroke THICK = new BasicStroke(2.5f);
	private static final Stroke THIN = new BasicStroke(1.5f);
	private static final Stroke DASHED = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f);
	private static final Color POOL_FILL = new Color(150, 40, 220, 70);
	private static final Color POOL_EDGE = new Color(190, 90, 255, 180);
	private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

	private final Client client;
	private final OcellusPortalsPlugin plugin;
	private final OcellusPortalsConfig config;

	@Inject
	OcellusPortalsOverlay(Client client, OcellusPortalsPlugin plugin, OcellusPortalsConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.isInArena())
		{
			return null;
		}
		WorldView wv = client.getTopLevelWorldView();
		g.setFont(FontManager.getRunescapeBoldFont());

		if (config.highlightPools())
		{
			for (GraphicsObject pool : plugin.getPools())
			{
				Polygon poly = Perspective.getCanvasTilePoly(client, pool.getLocation());
				if (poly != null)
				{
					OverlayUtil.renderPolygon(g, poly, POOL_EDGE, POOL_FILL, THIN);
				}
			}
		}

		WorldPoint next = plugin.getNextTile();
		if (config.showRemaining())
		{
			Color c = withAlpha(config.nextColor(), 120);
			for (OcellusPortalsPlugin.LabelledTile t : plugin.getRemainingTiles())
			{
				if (t.tile.equals(next))
				{
					continue;
				}
				Polygon poly = tilePoly(wv, t.tile);
				if (poly != null)
				{
					OverlayUtil.renderPolygon(g, poly, c, TRANSPARENT, THIN);
					drawLabel(g, poly, t.label, c);
				}
			}
		}

		if (config.showNext() && next != null)
		{
			Polygon poly = tilePoly(wv, next);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(g, poly, config.nextColor(), withAlpha(config.nextColor(), 35), DASHED);
				String label = plugin.getSafeTiles().isEmpty() ? "GO" : "NEXT";
				drawLabel(g, poly, label, config.nextColor());
			}
		}

		if (config.highlightSafe() && !plugin.getSafeTiles().isEmpty())
		{
			Color edge = config.safeColor();
			Color fill = withAlpha(edge, config.safeFill());
			Area block = new Area();
			for (WorldPoint wp : plugin.getSafeTiles())
			{
				Polygon poly = tilePoly(wv, wp);
				if (poly != null)
				{
					g.setColor(fill);
					g.fill(poly);
					block.add(new Area(poly));
				}
			}
			if (!block.isEmpty())
			{
				g.setColor(edge);
				g.setStroke(THICK);
				g.draw(block);
				PortalSequences.Match m = plugin.getCurrent();
				String label = m == null ? "SAFE" : m.colour.name().charAt(0) + Integer.toString(m.wave);
				Rectangle b = block.getBounds();
				drawText(g, b.x + b.width / 2, b.y + b.height / 2, label, edge);

				if (config.lineToSafe())
				{
					Player me = client.getLocalPlayer();
					if (me != null)
					{
						Point p = Perspective.localToCanvas(client, me.getLocalLocation(), wv.getPlane());
						if (p != null)
						{
							g.setStroke(THIN);
							g.setColor(withAlpha(edge, 160));
							g.drawLine(p.getX(), p.getY(), b.x + b.width / 2, b.y + b.height / 2);
						}
					}
				}
			}
		}

		if (config.highlightFloorPortal())
		{
			for (GraphicsObject fp : plugin.getFloorPortals())
			{
				Polygon poly = Perspective.getCanvasTilePoly(client, fp.getLocation());
				if (poly != null)
				{
					Color c = config.floorPortalColor();
					OverlayUtil.renderPolygon(g, poly, c, withAlpha(c, 70), THICK);
					drawLabel(g, poly, "TP", c);
				}
			}
		}

		if (config.debug())
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			for (OcellusPortalsPlugin.Portal p : plugin.getOrangePortals())
			{
				for (int x = p.minX; x <= p.maxX; x++)
				{
					for (int y = p.minY; y <= p.maxY; y++)
					{
						Polygon poly = tilePoly(wv, new WorldPoint(x, y, p.plane));
						if (poly != null)
						{
							OverlayUtil.renderPolygon(g, poly, Color.CYAN, TRANSPARENT, THIN);
							drawLabel(g, poly, p.id + (p.spansX ? " H" : " V"), Color.CYAN);
						}
					}
				}
			}
		}
		return null;
	}

	private Polygon tilePoly(WorldView wv, WorldPoint wp)
	{
		LocalPoint lp = LocalPoint.fromWorld(wv, wp);
		return lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
	}

	private static void drawLabel(Graphics2D g, Polygon poly, String text, Color color)
	{
		Rectangle b = poly.getBounds();
		drawText(g, b.x + b.width / 2, b.y + b.height / 2, text, color);
	}

	private static void drawText(Graphics2D g, int cx, int cy, String text, Color color)
	{
		Font f = g.getFont();
		int w = g.getFontMetrics(f).stringWidth(text);
		int x = cx - w / 2, y = cy + g.getFontMetrics(f).getAscent() / 2 - 1;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue()));
		g.drawString(text, x, y);
	}

	private static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
	}
}
