/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
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
package org.mapsforge.map.layer.debug;

import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.Color;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.model.DisplayModel;

public class TileGridLayer extends Layer {
	private static Paint createPaint(GraphicFactory graphicFactory, DisplayModel displayModel) {
		Paint paint = graphicFactory.createPaint();
		paint.setColor(Color.BLACK);
		paint.setStrokeWidth(2 * displayModel.getScaleFactor());
		paint.setStyle(Style.STROKE);
		return paint;
	}

	private final DisplayModel displayModel;
	private final Paint paint;

	public TileGridLayer(GraphicFactory graphicFactory, DisplayModel displayModel) {
		super();

		this.displayModel = displayModel;
		this.paint = createPaint(graphicFactory, displayModel);
	}

	@Override
	public void draw(BoundingBox boundingBox, byte zoomLevel, Canvas canvas, Point topLeftPoint) {
		int tileLeft = MercatorProjection.longitudeToTileX(boundingBox.minLongitude, zoomLevel);
		int tileTop = MercatorProjection.latitudeToTileY(boundingBox.maxLatitude, zoomLevel);
		int tileRight = MercatorProjection.longitudeToTileX(boundingBox.maxLongitude, zoomLevel);
		int tileBottom = MercatorProjection.latitudeToTileY(boundingBox.minLatitude, zoomLevel);

		int tileSize = this.displayModel.getTileSize();
		int pixelX1 = (int) (MercatorProjection.tileToPixel(tileLeft, tileSize) - topLeftPoint.x);
		int pixelY1 = (int) (MercatorProjection.tileToPixel(tileTop, tileSize) - topLeftPoint.y);
		int pixelX2 = (int) (MercatorProjection.tileToPixel(tileRight, tileSize) - topLeftPoint.x + tileSize);
		int pixelY2 = (int) (MercatorProjection.tileToPixel(tileBottom, tileSize) - topLeftPoint.y + tileSize);

		for (int lineX = pixelX1; lineX <= pixelX2 + 1; lineX += tileSize) {
			canvas.drawLine(lineX, pixelY1, lineX, pixelY2, this.paint);
		}

		for (int lineY = pixelY1; lineY <= pixelY2 + 1; lineY += tileSize) {
			canvas.drawLine(pixelX1, lineY, pixelX2, lineY, this.paint);
		}
	}
}
