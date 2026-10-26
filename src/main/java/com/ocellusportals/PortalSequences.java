package com.ocellusportals;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * The three fixed phase-2 portal sequences, expressed in template (region-local) coordinates.
 *
 * <p>Each quadrant has an "inner corner": the tile nearest Ocellus. A safe tile is recorded as
 * (columns out, rows out) from that corner, 0 = the column/row touching the centre. All four
 * quadrants are exact mirror images, so one table serves every quadrant.
 *
 * <p>Source: Rs Suspect, "OSRS Ocellus Portal Phase Fully Solved" (22 Oct 2026) and the tile
 * export published with it; verified frame-by-frame against the footage.
 */
final class PortalSequences
{
	static final int REGION_ID = 16472;
	static final int PLANE = 1;

	static final int WAVES = 6;

	/** A sequence: its safe tiles as [wave-1] -> {columnsOut, rowsOut}, and the tint the panel shows it in. */
	enum Colour
	{
		ORANGE(new int[][]{{1, 0}, {1, 4}, {6, 0}, {2, 0}, {4, 2}, {2, 6}}, new Color(255, 154, 46)),
		RED(new int[][]{{4, 3}, {6, 2}, {8, 0}, {3, 3}, {7, 3}, {6, 4}}, new Color(255, 92, 99)),
		GREEN(new int[][]{{0, 0}, {5, 0}, {9, 0}, {5, 4}, {3, 2}, {0, 4}}, new Color(63, 211, 127));

		final int[][] offsets;
		final Color tint;

		Colour(int[][] offsets, Color tint)
		{
			this.offsets = offsets;
			this.tint = tint;
		}

		Colour next()
		{
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/** A quadrant: inner corner (region-local) and the outward direction on each axis. */
	enum Quadrant
	{
		NW(29, 44, -1, +1),
		NE(33, 44, +1, +1),
		SW(29, 38, -1, -1),
		SE(33, 38, +1, -1);

		final int cornerX, cornerY, dirX, dirY;

		Quadrant(int cornerX, int cornerY, int dirX, int dirY)
		{
			this.cornerX = cornerX;
			this.cornerY = cornerY;
			this.dirX = dirX;
			this.dirY = dirY;
		}

		/** Quadrant containing a region-local tile. The centre strip (x 30-32, y 39-43) belongs to none. */
		static Quadrant of(int regionX, int regionY)
		{
			if (regionX >= 30 && regionX <= 32)
			{
				return null;
			}
			if (regionY >= 39 && regionY <= 43)
			{
				return null;
			}
			boolean west = regionX < 30, north = regionY > 43;
			return west ? (north ? NW : SW) : (north ? NE : SE);
		}

		/** Region-local tile for a (columnsOut, rowsOut) offset. */
		WorldPoint tile(int columnsOut, int rowsOut)
		{
			return WorldPoint.fromRegion(REGION_ID, cornerX + columnsOut * dirX, cornerY + rowsOut * dirY, PLANE);
		}
	}

	private PortalSequences()
	{
	}

	/** The marked safe tile of a wave (1-6) in a quadrant, as a template world point. */
	static WorldPoint safeTile(Colour colour, int wave, Quadrant q)
	{
		int[] off = colour.offsets[wave - 1];
		return q.tile(off[0], off[1]);
	}

	/** One (colour, wave) pairing. */
	static final class Match
	{
		final Colour colour;
		final int wave;

		Match(Colour colour, int wave)
		{
			this.colour = colour;
			this.wave = wave;
		}

		@Override
		public String toString()
		{
			return colour + " " + wave;
		}
	}

	/**
	 * Every (colour, wave) whose marked tile lies inside the given set of template tiles.
	 * The live safe zone is the 2x2 crossing of two 2-wide portals, so a wave matches when
	 * its single marked tile falls anywhere inside that zone.
	 */
	static List<Match> matches(Collection<WorldPoint> templateTiles)
	{
		List<Match> out = new ArrayList<>();
		if (templateTiles.isEmpty())
		{
			return out;
		}
		WorldPoint any = templateTiles.iterator().next();
		Quadrant q = Quadrant.of(any.getRegionX(), any.getRegionY());
		if (q == null || any.getRegionID() != REGION_ID)
		{
			return out;
		}
		for (Colour c : Colour.values())
		{
			for (int w = 1; w <= WAVES; w++)
			{
				if (templateTiles.contains(safeTile(c, w, q)))
				{
					out.add(new Match(c, w));
				}
			}
		}
		return out;
	}
}
