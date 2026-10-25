package com.ocellusportals;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Ocellus Portals",
	description = "Ocellus phase 2: highlights the tile where the two orange portals cross, the next tile in the sequence, and the floor portal",
	tags = {"ocellus", "fractured", "archive", "tfa", "raid", "portal", "safe", "tile"}
)
public class OcellusPortalsPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(OcellusPortalsPlugin.class);

	/** Loc "Portal", 2 tiles wide, orange model 62758: TFA_OCELLUS_QUADRANT_WALL_INACTIVE */
	static final int ORANGE_PORTAL = 62684;
	/** Loc "Portal", 2 tiles wide, purple model 62757: TFA_OCELLUS_QUADRANT_WALL_ACTIVE */
	static final int PURPLE_PORTAL = 62685;
	/** Spotanims VFX_OCELLUS_P2_SAFE_PORTAL_SPAWN / IDLE / DESPAWN (orange floor portal, model 62756) */
	static final int FLOOR_PORTAL_SPAWN = 4271;
	static final int FLOOR_PORTAL_IDLE = 4272;
	static final int FLOOR_PORTAL_DESPAWN = 4273;
	/** Spotanim VFX_OCELLUS_P2_AOE_DODGE (purple pool, model 62755) */
	static final int POOL = 4270;

	/** How far (tiles) an orange crossing may be from both of its portals to count as their crossing. */
	private static final int MAX_LANE_LENGTH = 16;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private OcellusPortalsOverlay overlay;

	@Inject
	private OcellusPortalsInfoOverlay infoOverlay;

	@Inject
	private OcellusPortalsConfig config;

	/** One orange wall portal: the tiles it occupies, in instance-local world coordinates. */
	static final class Portal
	{
		final int id;
		final int plane;
		final int minX, maxX, minY, maxY;
		final boolean spansX; // lies on a horizontal portal line, so its lane is a set of columns

		Portal(int id, int plane, int minX, int maxX, int minY, int maxY, int orientation)
		{
			this.id = id;
			this.plane = plane;
			this.minX = minX;
			this.maxX = maxX;
			this.minY = minY;
			this.maxY = maxY;
			// A 2-wide portal occupies two tiles along the line it sits on; fall back to orientation for 1x1.
			this.spansX = maxX > minX || (maxY == minY && (orientation & 1) == 0);
		}

		int sizeX()
		{
			return maxX - minX + 1;
		}

		int sizeY()
		{
			return maxY - minY + 1;
		}
	}

	// tracked world state
	private final Map<WorldPoint, Portal> orangePortals = new HashMap<>();
	private final List<GraphicsObject> floorPortals = new ArrayList<>();
	private final List<GraphicsObject> pools = new ArrayList<>();

	// derived state read by the overlays (client thread only)
	private final List<WorldPoint> safeTiles = new ArrayList<>();
	private final List<WorldPoint> safeTemplateTiles = new ArrayList<>();
	private WorldPoint nextTile;
	private final List<LabelledTile> remainingTiles = new ArrayList<>();
	private PortalSequences.Match current;
	private EnumSet<PortalSequences.Colour> candidates = EnumSet.allOf(PortalSequences.Colour.class);
	private PortalSequences.Quadrant currentQuadrant;
	private PortalSequences.Colour lastQuadrantColour;
	private boolean inArena;
	private Set<WorldPoint> lastSafeKey = Collections.emptySet();

	static final class LabelledTile
	{
		final WorldPoint tile;
		final String label;

		LabelledTile(WorldPoint tile, String label)
		{
			this.tile = tile;
			this.label = label;
		}
	}

	@Provides
	OcellusPortalsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OcellusPortalsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(infoOverlay);
		reset();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(infoOverlay);
		reset();
	}

	private void reset()
	{
		orangePortals.clear();
		floorPortals.clear();
		pools.clear();
		safeTiles.clear();
		safeTemplateTiles.clear();
		remainingTiles.clear();
		nextTile = null;
		current = null;
		candidates = EnumSet.allOf(PortalSequences.Colour.class);
		currentQuadrant = null;
		lastQuadrantColour = null;
		lastSafeKey = Collections.emptySet();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOADING || e.getGameState() == GameState.LOGIN_SCREEN
			|| e.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		GameObject obj = e.getGameObject();
		if (obj.getId() != ORANGE_PORTAL)
		{
			return;
		}
		WorldView wv = obj.getWorldView();
		Point min = obj.getSceneMinLocation();
		Point max = obj.getSceneMaxLocation();
		WorldPoint sw = WorldPoint.fromScene(wv, min.getX(), min.getY(), obj.getPlane());
		WorldPoint ne = WorldPoint.fromScene(wv, max.getX(), max.getY(), obj.getPlane());
		Portal p = new Portal(obj.getId(), obj.getPlane(), sw.getX(), ne.getX(), sw.getY(), ne.getY(), obj.getOrientation());
		orangePortals.put(sw, p);
		if (config.debug())
		{
			log.info("orange portal spawned at {} span {}x{} orientation {}", sw, p.sizeX(), p.sizeY(), obj.getOrientation());
		}
		recompute();
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		GameObject obj = e.getGameObject();
		if (obj.getId() != ORANGE_PORTAL)
		{
			return;
		}
		Point min = obj.getSceneMinLocation();
		WorldPoint sw = WorldPoint.fromScene(obj.getWorldView(), min.getX(), min.getY(), obj.getPlane());
		if (orangePortals.remove(sw) != null)
		{
			recompute();
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated e)
	{
		GraphicsObject g = e.getGraphicsObject();
		int id = g.getId();
		if (id == FLOOR_PORTAL_SPAWN || id == FLOOR_PORTAL_IDLE || id == FLOOR_PORTAL_DESPAWN)
		{
			floorPortals.add(g);
		}
		else if (id == POOL)
		{
			pools.add(g);
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		prune(floorPortals);
		prune(pools);
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			inArena = false;
			return;
		}
		WorldPoint template = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		inArena = template != null && template.getRegionID() == PortalSequences.REGION_ID;
	}

	private static void prune(List<GraphicsObject> list)
	{
		for (Iterator<GraphicsObject> it = list.iterator(); it.hasNext(); )
		{
			if (it.next().finished())
			{
				it.remove();
			}
		}
	}

	/**
	 * Pair each orange portal on a horizontal line with the orange portal on a vertical line that it
	 * actually crosses, and publish the crossing tiles. Then recognise which sequence is running.
	 */
	private void recompute()
	{
		safeTiles.clear();
		safeTemplateTiles.clear();

		List<Portal> horizontal = new ArrayList<>();
		List<Portal> vertical = new ArrayList<>();
		for (Portal p : orangePortals.values())
		{
			(p.spansX ? horizontal : vertical).add(p);
		}

		// Candidate crossings: the H portal's columns x the V portal's rows, provided the crossing is
		// within lane reach of both portals (so portals of different quadrants are never paired).
		List<List<WorldPoint>> crossings = new ArrayList<>();
		for (Portal h : horizontal)
		{
			for (Portal v : vertical)
			{
				if (h.plane != v.plane)
				{
					continue;
				}
				int dx = Math.min(Math.abs(h.minX - v.minX), Math.abs(h.maxX - v.maxX));
				int dy = Math.min(Math.abs(h.minY - v.minY), Math.abs(h.maxY - v.maxY));
				if (dx > MAX_LANE_LENGTH || dy > MAX_LANE_LENGTH)
				{
					continue;
				}
				List<WorldPoint> tiles = new ArrayList<>();
				for (int x = h.minX; x <= h.maxX; x++)
				{
					for (int y = v.minY; y <= v.maxY; y++)
					{
						tiles.add(new WorldPoint(x, y, h.plane));
					}
				}
				crossings.add(tiles);
			}
		}

		if (crossings.isEmpty())
		{
			publishSequenceState(Collections.emptyList());
			return;
		}

		// If several crossings survive, keep the one nearest the player.
		List<WorldPoint> best = crossings.get(0);
		Player me = client.getLocalPlayer();
		if (crossings.size() > 1 && me != null)
		{
			WorldPoint mine = me.getWorldLocation();
			int bestDist = Integer.MAX_VALUE;
			for (List<WorldPoint> c : crossings)
			{
				int d = c.get(0).distanceTo(mine);
				if (d < bestDist)
				{
					bestDist = d;
					best = c;
				}
			}
		}
		safeTiles.addAll(best);

		// Template coordinates, for matching against the known sequences.
		WorldView wv = client.getTopLevelWorldView();
		for (WorldPoint wp : best)
		{
			LocalPoint lp = LocalPoint.fromWorld(wv, wp);
			if (lp != null)
			{
				safeTemplateTiles.add(WorldPoint.fromLocalInstance(client, lp, wp.getPlane()));
			}
		}
		publishSequenceState(safeTemplateTiles);
	}

	/** Recognise (colour, wave) from the crossing and derive the next tile(s). */
	private void publishSequenceState(List<WorldPoint> templateTiles)
	{
		Set<WorldPoint> key = new LinkedHashSet<>(templateTiles);
		boolean changed = !key.equals(lastSafeKey);
		lastSafeKey = key;
		if (templateTiles.isEmpty() || !changed)
		{
			// Between waves the portals are gone: keep what we know and keep pointing at the next tile.
			deriveTargets();
			return;
		}

		WorldPoint any = templateTiles.get(0);
		PortalSequences.Quadrant q = PortalSequences.Quadrant.of(any.getRegionX(), any.getRegionY());
		if (q == null)
		{
			return;
		}
		if (q != currentQuadrant)
		{
			// New quadrant: the cycle says the colour advances; keep that as the leading candidate.
			currentQuadrant = q;
			PortalSequences.Colour expected = lastQuadrantColour == null ? null : lastQuadrantColour.next();
			candidates = expected == null ? EnumSet.allOf(PortalSequences.Colour.class) : EnumSet.of(expected);
			current = null;
		}

		List<PortalSequences.Match> matches = PortalSequences.matches(templateTiles);
		PortalSequences.Match picked = null;

		// 1. continuation of the sequence we are already following
		if (current != null && current.wave < PortalSequences.WAVES)
		{
			for (PortalSequences.Match m : matches)
			{
				if (m.colour == current.colour && m.wave == current.wave + 1)
				{
					picked = m;
				}
			}
		}
		boolean ambiguous = false;
		// 2. otherwise a match among the candidate colours (lowest wave wins)
		if (picked == null)
		{
			List<PortalSequences.Match> inCandidates = new ArrayList<>();
			for (PortalSequences.Match m : matches)
			{
				if (candidates.contains(m.colour))
				{
					inCandidates.add(m);
				}
			}
			if (inCandidates.isEmpty())
			{
				inCandidates = matches; // cycle assumption was wrong; fall back to anything that fits
			}
			EnumSet<PortalSequences.Colour> colours = EnumSet.noneOf(PortalSequences.Colour.class);
			for (PortalSequences.Match m : inCandidates)
			{
				colours.add(m.colour);
				if (picked == null || m.wave < picked.wave)
				{
					picked = m;
				}
			}
			if (colours.size() > 1)
			{
				// e.g. orange 1 and green 1 are neighbouring tiles: show the best guess, decide next wave
				ambiguous = true;
				candidates = colours;
			}
		}

		current = picked;
		if (picked != null)
		{
			if (!ambiguous)
			{
				candidates = EnumSet.of(picked.colour);
			}
			lastQuadrantColour = picked.colour;
		}
		deriveTargets();
	}

	private void deriveTargets()
	{
		nextTile = null;
		remainingTiles.clear();
		if (current == null || currentQuadrant == null)
		{
			return;
		}
		for (int w = current.wave + 1; w <= PortalSequences.WAVES; w++)
		{
			WorldPoint template = PortalSequences.safeTile(current.colour, w, currentQuadrant);
			WorldPoint local = toLocal(template);
			if (local == null)
			{
				continue;
			}
			if (w == current.wave + 1)
			{
				nextTile = local;
			}
			remainingTiles.add(new LabelledTile(local, Integer.toString(w)));
		}
	}

	/** Template world point -> the instance-local copy nearest the player. */
	private WorldPoint toLocal(WorldPoint template)
	{
		Collection<WorldPoint> copies = WorldPoint.toLocalInstance(client.getTopLevelWorldView(), template);
		if (copies == null || copies.isEmpty())
		{
			return null;
		}
		Player me = client.getLocalPlayer();
		WorldPoint best = null;
		int bestDist = Integer.MAX_VALUE;
		for (WorldPoint c : copies)
		{
			int d = me == null ? 0 : c.distanceTo(me.getWorldLocation());
			if (best == null || d < bestDist)
			{
				best = c;
				bestDist = d;
			}
		}
		return best;
	}

	// ---- read by overlays ----

	boolean isInArena()
	{
		return inArena;
	}

	List<WorldPoint> getSafeTiles()
	{
		return safeTiles;
	}

	WorldPoint getNextTile()
	{
		return nextTile;
	}

	List<LabelledTile> getRemainingTiles()
	{
		return remainingTiles;
	}

	PortalSequences.Match getCurrent()
	{
		return current;
	}

	EnumSet<PortalSequences.Colour> getCandidates()
	{
		return candidates;
	}

	List<GraphicsObject> getFloorPortals()
	{
		return floorPortals;
	}

	List<GraphicsObject> getPools()
	{
		return pools;
	}

	Collection<Portal> getOrangePortals()
	{
		return orangePortals.values();
	}
}
