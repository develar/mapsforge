/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright © 2014 Ludwig M Brinckmann
 * Copyright © 2014 devemux86
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.layer.renderer;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Position;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.mapelements.SymbolContainer;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.labels.TileBasedLabelStore;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.MapReadResult;
import org.mapsforge.map.reader.PointOfInterest;
import org.mapsforge.map.reader.Way;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.mapsforge.map.rendertheme.RenderCallback;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.rule.RenderTheme;
import org.mapsforge.map.rendertheme.rule.RenderThemeHandler;
import org.mapsforge.map.util.LayerUtil;

/**
 * The DatabaseRenderer renders map tiles by reading from a {@link MapDatabase}.
 *
 * Up to version 0.4.x the DatabaseRenderer was responsible for rendering ways, areas as
 * well as labels. However, the label placement algorithm suffered from multiple problems,
 * such as clipped labels at tile bounds.
 *
 *
 */
public class DatabaseRenderer implements RenderCallback {

	private static final Byte DEFAULT_START_ZOOM_LEVEL = Byte.valueOf((byte) 12);
	private static final byte LAYERS = 11;
	private static final Logger LOGGER = Logger.getLogger(DatabaseRenderer.class.getName());
	private static final double STROKE_INCREASE = 1.5;
	private static final byte STROKE_MIN_ZOOM_LEVEL = 12;
	private static final Tag TAG_NATURAL_WATER = new Tag("natural", "water");
	private static final byte ZOOM_MAX = 22;

	private static Point[] getTilePixelCoordinates(int tileSize) {
		Point[] result = new Point[5];
		result[0] = new Point(0, 0);
		result[1] = new Point(tileSize, 0);
		result[2] = new Point(tileSize, tileSize);
		result[3] = new Point(0, tileSize);
		result[4] = result[0];
		return result;
	}

	private static byte getValidLayer(byte layer) {
		if (layer < 0) {
			return 0;
		} else if (layer >= LAYERS) {
			return LAYERS - 1;
		} else {
			return layer;
		}
	}

	private final CanvasRasterer canvasRasterer;
	private List<MapElementContainer> currentLabels;
	private Set<MapElementContainer> currentWayLabels;
	private List<List<ShapePaintContainer>> drawingLayers;
	private final GraphicFactory graphicFactory;
	private final TileBasedLabelStore labelStore;
	private final MapDatabase mapDatabase;
	private XmlRenderTheme previousJobTheme;
	private final boolean renderLabels;
	private RenderTheme renderTheme;
	private List<List<List<ShapePaintContainer>>> ways;
	private final TileCacheInfoProvider tileCacheInfoProvider;
	private final TileDependencies tileDependencies;

	/**
	 * Constructs a new DatabaseRenderer that will not draw labels, instead it stores the label
	 * information in the labelStore for drawing by a LabelLayer.
	 * 
	 * @param mapDatabase
	 *            the MapDatabase from which the map data will be read.
	 */
	public DatabaseRenderer(MapDatabase mapDatabase, GraphicFactory graphicFactory,
	                        TileBasedLabelStore labelStore) {
		this.mapDatabase = mapDatabase;
		this.graphicFactory = graphicFactory;

		this.canvasRasterer = new CanvasRasterer(graphicFactory);
		this.labelStore = labelStore;
		this.renderLabels = false;
		this.tileCacheInfoProvider = null;
		this.tileDependencies = null;
	}

	/**
	 * Constructs a new DatabaseRenderer that will draw labels onto the tiles.
	 *
     * @param mapDatabase
     *            the MapDatabase from which the map data will be read.
     */
    public DatabaseRenderer(MapDatabase mapDatabase, GraphicFactory graphicFactory,
                            final TileCache tileCache) {
        this(mapDatabase, graphicFactory, new TileCacheInfoProvider() {
            @Override
            public boolean contains(Tile tile, RendererJob rendererJob) {
                return tileCache.containsKey(rendererJob.otherTile(tile));
            }
        });
    }

    public DatabaseRenderer(MapDatabase mapDatabase, GraphicFactory graphicFactory,
                            final TileCacheInfoProvider tileCacheInfoProvider) {
        this.mapDatabase = mapDatabase;
        this.graphicFactory = graphicFactory;

        this.canvasRasterer = new CanvasRasterer(graphicFactory);
        this.labelStore = null;
        this.renderLabels = true;
        this.tileCacheInfoProvider = tileCacheInfoProvider;
        this.tileDependencies = new TileDependencies();
    }

    public interface TileCacheInfoProvider {
        public boolean contains(Tile tile, RendererJob rendererJob);
    }


	public void destroy() {
		this.canvasRasterer.destroy();
		// there is a chance that the renderer is being destroyed from the
		// DestroyThread before the rendertheme has been completely created
		// and assigned. If that happens bitmap memory held by the
		// RenderThemeHandler
		// will be leaked
		if (this.renderTheme != null) {
			this.renderTheme.destroy();
		} else {
			LOGGER.log(Level.SEVERE, "RENDERTHEME Could not destroy RenderTheme");
		}
	}

	public TileBitmap executeJob(RendererJob rendererJob) {
		XmlRenderTheme jobTheme = rendererJob.xmlRenderTheme;
		if (!jobTheme.equals(this.previousJobTheme)) {
			this.renderTheme = getRenderTheme(jobTheme, rendererJob.displayModel);
			if (this.renderTheme == null) {
				this.previousJobTheme = null;
				return null;
			}
			this.ways = createWayLists();
			this.previousJobTheme = jobTheme;
		}

		return renderTile(rendererJob.tile, rendererJob.textScale, rendererJob.labelsOnly, rendererJob.hasAlpha, rendererJob.displayModel, rendererJob);
	}

	public void setRenderTheme(RenderTheme renderTheme) {
		if (this.renderTheme != renderTheme) {
			this.renderTheme = renderTheme;
			this.ways = createWayLists();
		}
	}

	public TileBitmap renderTile(Tile tile, float textScale, boolean labelsOnly, boolean hasAlpha, DisplayModel displayModel) {
		return renderTile(tile, textScale, labelsOnly, hasAlpha, displayModel, null);
	}

	private TileBitmap renderTile(Tile tile, float textScale, boolean labelsOnly, boolean hasAlpha, DisplayModel displayModel, RendererJob rendererJob) {
		final int tileSize = tile.tileSize;
		final byte zoomLevel = tile.zoomLevel;

		this.currentLabels = new LinkedList<MapElementContainer>();
		this.currentWayLabels = new HashSet<MapElementContainer>();

		setScaleStrokeWidth(zoomLevel);
		this.renderTheme.scaleTextSize(textScale);

		if (this.mapDatabase != null) {
			MapReadResult mapReadResult = this.mapDatabase.readMapData(tile);
			processReadMapData(ways, mapReadResult, tile);
		}

		TileBitmap bitmap = null;
		if (!labelsOnly) {
			bitmap = this.graphicFactory.createTileBitmap(tileSize, hasAlpha);
			this.canvasRasterer.setCanvasBitmap(bitmap);
			if (displayModel.getBackgroundColor() != this.renderTheme.getMapBackground()) {
				this.canvasRasterer.fill(hasAlpha ? 0 : this.renderTheme.getMapBackground());
			}
			this.canvasRasterer.drawWays(ways, tile);
		}

		if (renderLabels) {
			// if we are drawing the labels per tile, we need to establish which tile-overlapping
			// elements need to be drawn.

			Set<MapElementContainer> labelsToDraw = new HashSet<MapElementContainer>();
			// first we need to get the labels from the adjacent tiles if they have already been drawn
			// as those overlapping items must also be drawn on the current tile. They must be drawn regardless
			// of priority clashes as a part of them has alread been drawn.
			Set<Tile> neighbours = tile.getNeighbours();
			Iterator<Tile> tileIterator = neighbours.iterator();
			Set<MapElementContainer> undrawableElements = new HashSet<MapElementContainer>();
			while (tileIterator.hasNext()) {
				Tile neighbour = tileIterator.next();
				if (tileCacheInfoProvider.contains(neighbour, rendererJob)) {
					// if a tile has already been drawn, the elements drawn that overlap onto the
					// current tile should be in the tile dependencies, we add them to the labels that
					// need to be drawn onto this tile.
					labelsToDraw.addAll(tileDependencies.getOverlappingElements(neighbour, tile));

					// but we need to remove the labels for this tile that overlap onto a tile that has been drawn
					for (MapElementContainer current : currentLabels) {
						if (current.intersects(neighbour.getBoundaryAbsolute())) {
							undrawableElements.add(current);
						}
					}
					// since we already have the data from that tile, we do not need to get the data for
					// it, so remove it from the neighbours list.
					tileIterator.remove();
				} else {
					tileDependencies.removeTileData(neighbour);
				}
			}

			// now we remove the elements that overlap onto a drawn tile from the list of labels
			// for this tile
			currentLabels.removeAll(undrawableElements);

			// at this point we have two lists: one is the list of labels that must be drawn because
			// they already overlap from other tiles. The second one is currentLabels that contains
			// the elements on this tile that do not overlap onto a drawn tile. Now we sort this list and
			// remove those elements that clash in this list already.
			List<MapElementContainer> currentElementsOrdered = LayerUtil.collisionFreeOrdered(currentLabels);

			// now we go through this list, ordered by priority, to see which can be drawn without clashing.
			Iterator<MapElementContainer> currentMapElementsIterator = currentElementsOrdered.iterator();
			while (currentMapElementsIterator.hasNext()) {
				MapElementContainer current = currentMapElementsIterator.next();
				for (MapElementContainer label : labelsToDraw) {
					if (label.clashesWith(current)) {
						currentMapElementsIterator.remove();
						break;
					}
				}
			}

			labelsToDraw.addAll(currentElementsOrdered);

			// update dependencies, add to the dependencies list all the elements that overlap to the
			// neighbouring tiles, first clearing out the cache for this relation.
			for (Tile neighbour : neighbours) {
				tileDependencies.removeTileData(neighbour, neighbour);
				for (MapElementContainer element : labelsToDraw) {
					if (element.intersects(neighbour.getBoundaryAbsolute())) {
						tileDependencies.addOverlappingElement(neighbour, neighbour, element);
					}
				}
			}
			// now draw the ways and the labels
			this.canvasRasterer.drawMapElements(currentWayLabels, tile);
			this.canvasRasterer.drawMapElements(labelsToDraw, tile);
		} else {
			// store elements for this tile in the label cache
			this.labelStore.storeMapItems(tile, this.currentLabels);
		}

		// clear way list
		for (int i = this.ways.size() - 1; i >= 0; --i) {
			List<List<ShapePaintContainer>> innerWayList = this.ways.get(i);
			for (int j = innerWayList.size() - 1; j >= 0; --j) {
				innerWayList.get(j).clear();
			}
		}

		return bitmap;
	}

	public MapDatabase getMapDatabase() {
		return this.mapDatabase;
	}

	/**
	 * @return the start point (may be null).
	 */
	public LatLong getStartPoint() {
		if (this.mapDatabase != null && this.mapDatabase.hasOpenFile()) {
			MapFileInfo mapFileInfo = this.mapDatabase.getMapFileInfo();
			if (mapFileInfo.startPosition != null) {
				return mapFileInfo.startPosition;
			}
			return mapFileInfo.boundingBox.getCenterPoint();
		}

		return null;
	}

	/**
	 * @return the start zoom level (may be null).
	 */
	public Byte getStartZoomLevel() {
		if (this.mapDatabase != null && this.mapDatabase.hasOpenFile()) {
			MapFileInfo mapFileInfo = this.mapDatabase.getMapFileInfo();
			if (mapFileInfo.startZoomLevel != null) {
				return mapFileInfo.startZoomLevel;
			}
		}

		return DEFAULT_START_ZOOM_LEVEL;
	}

	/**
	 * @return the maximum zoom level.
	 */
	public byte getZoomLevelMax() {
		return ZOOM_MAX;
	}

	@Override
	public void renderArea(PolylineContainer way, Paint fill, Paint stroke, int level) {
		List<ShapePaintContainer> list = this.drawingLayers.get(level);
		list.add(new ShapePaintContainer(way, stroke));
		list.add(new ShapePaintContainer(way, fill));
	}

	@Override
	public void renderAreaCaption(PolylineContainer way, int priority, String caption, float horizontalOffset, float verticalOffset,
	                              Paint fill, Paint stroke, Position position, int maxTextWidth) {
		Point centerPoint = way.getCenterAbsolute().offset(horizontalOffset, verticalOffset);
		this.currentLabels.add(this.graphicFactory.createPointTextContainer(centerPoint, priority, caption, fill, stroke, null, position, maxTextWidth));
	}

	@Override
	public void renderAreaSymbol(PolylineContainer way, int priority, Bitmap symbol) {
		Point centerPosition = way.getCenterAbsolute();

		this.currentLabels.add(new SymbolContainer(centerPosition, priority, symbol));
	}

	@Override
	public void renderPointOfInterestCaption(PointOfInterest poi, int priority, String caption, float horizontalOffset, float verticalOffset,
	                                         Paint fill, Paint stroke, Position position, int maxTextWidth, Tile tile) {
		Point poiPosition = MercatorProjection.getPixelAbsolute(poi.position, tile.zoomLevel, tile.tileSize);

		this.currentLabels.add(this.graphicFactory.createPointTextContainer(poiPosition.offset(horizontalOffset, verticalOffset), priority, caption, fill,
				stroke, null, position, maxTextWidth));
	}

	@Override
	public void renderPointOfInterestCircle(PointOfInterest poi, float radius, Paint fill, Paint stroke, int level, Tile tile) {
		List<ShapePaintContainer> list = this.drawingLayers.get(level);
		Point poiPosition = MercatorProjection.getPixelRelativeToTile(poi.position, tile);
		list.add(new ShapePaintContainer(new CircleContainer(poiPosition, radius), stroke));
		list.add(new ShapePaintContainer(new CircleContainer(poiPosition, radius), fill));
	}

	@Override
	public void renderPointOfInterestSymbol(PointOfInterest poi, int priority, Bitmap symbol, Tile tile) {
		Point poiPosition = MercatorProjection.getPixelAbsolute(poi.position, tile.zoomLevel, tile.tileSize);
		this.currentLabels.add(new SymbolContainer(poiPosition, priority, symbol));
	}

	@Override
	public void renderWay(PolylineContainer way, Paint stroke, float dy, int level) {
		this.drawingLayers.get(level).add(new ShapePaintContainer(way, stroke, dy));
	}

	@Override
	public void renderWaySymbol(PolylineContainer way, int priority, Bitmap symbol, float dy, boolean alignCenter, boolean repeat,
	                     float repeatGap, float repeatStart, boolean rotate) {
		WayDecorator.renderSymbol(symbol, priority, dy, alignCenter, repeat, repeatGap,
				repeatStart, rotate, way.getCoordinatesAbsolute(), this.currentLabels);
	}

	@Override
	public void renderWayText(PolylineContainer way, int priority, String textKey, float dy, Paint fill, Paint stroke) {
		WayDecorator.renderText(textKey, priority, dy, fill, stroke, way.getCoordinatesAbsolute(), this.currentWayLabels);
	}

	private List<List<List<ShapePaintContainer>>> createWayLists() {
		List<List<List<ShapePaintContainer>>> result = new ArrayList<List<List<ShapePaintContainer>>>(LAYERS);
		int levels = this.renderTheme.getLevels();

		for (byte i = LAYERS - 1; i >= 0; --i) {
			List<List<ShapePaintContainer>> innerWayList = new ArrayList<List<ShapePaintContainer>>(levels);
			for (int j = levels - 1; j >= 0; --j) {
				innerWayList.add(new ArrayList<ShapePaintContainer>(0));
			}
			result.add(innerWayList);
		}
		return result;
	}

	private RenderTheme getRenderTheme(XmlRenderTheme jobTheme, DisplayModel displayModel) {
		try {
			return RenderThemeHandler.getRenderTheme(this.graphicFactory, displayModel, jobTheme);
		} catch (XmlPullParserException e) {
			LOGGER.log(Level.SEVERE, null, e);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, null, e);
		}
		return null;
	}

	private void processReadMapData(final List<List<List<ShapePaintContainer>>> ways, MapReadResult mapReadResult, Tile tile) {
		if (mapReadResult == null) {
			return;
		}

		for (PointOfInterest pointOfInterest : mapReadResult.pointOfInterests) {
			renderPointOfInterest(ways, pointOfInterest, tile);
		}

		for (Way way : mapReadResult.ways) {
			renderWay(ways, new PolylineContainer(way, tile));
		}

		if (mapReadResult.isWater) {
			renderWaterBackground(ways, tile);
		}
	}

	private void renderPointOfInterest(final List<List<List<ShapePaintContainer>>> ways, PointOfInterest pointOfInterest, Tile tile) {
		this.drawingLayers = ways.get(getValidLayer(pointOfInterest.layer));
		this.renderTheme.matchNode(this, pointOfInterest, tile);
	}

	private void renderWaterBackground(final List<List<List<ShapePaintContainer>>> ways, Tile tile) {
		this.drawingLayers = ways.get(0);
		Point[] coordinates = getTilePixelCoordinates(tile.tileSize);
		PolylineContainer way = new PolylineContainer(coordinates, tile, Arrays.asList(TAG_NATURAL_WATER));
		this.renderTheme.matchClosedWay(this, way);
	}

	private void renderWay(final List<List<List<ShapePaintContainer>>> ways, PolylineContainer way) {
		this.drawingLayers = ways.get(getValidLayer(way.getLayer()));

		if (way.isClosedWay()) {
			this.renderTheme.matchClosedWay(this, way);
		} else {
			this.renderTheme.matchLinearWay(this, way);
		}
	}

	/**
	 * Sets the scale stroke factor for the given zoom level.
	 * 
	 * @param zoomLevel
	 *            the zoom level for which the scale stroke factor should be set.
	 */
	private void setScaleStrokeWidth(byte zoomLevel) {
		int zoomLevelDiff = Math.max(zoomLevel - STROKE_MIN_ZOOM_LEVEL, 0);
		this.renderTheme.scaleStrokeWidth((float) Math.pow(STROKE_INCREASE, zoomLevelDiff));
	}

}
