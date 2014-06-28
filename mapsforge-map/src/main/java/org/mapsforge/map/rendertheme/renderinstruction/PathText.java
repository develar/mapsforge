/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
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
package org.mapsforge.map.rendertheme.renderinstruction;

import java.util.Locale;

import org.mapsforge.core.graphics.Align;
import org.mapsforge.core.graphics.Color;
import org.mapsforge.core.graphics.FontFamily;
import org.mapsforge.core.graphics.FontStyle;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.layer.renderer.PolylineContainer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.PointOfInterest;
import org.mapsforge.map.rendertheme.RenderCallback;
import org.mapsforge.map.rendertheme.XmlUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Represents a text along a polyline on the map.
 */
public class PathText extends RenderInstruction {
	private float dy;
	private final Paint fill;
	private float fontSize;
	private int priority;
	private final Paint stroke;
	private TextKey textKey;

	public PathText(GraphicFactory graphicFactory, DisplayModel displayModel, String elementName,
	                       XmlPullParser pullParser) throws XmlPullParserException {
		super(graphicFactory, displayModel);
		this.fill = graphicFactory.createPaint();
		this.fill.setColor(Color.BLACK);
		this.fill.setStyle(Style.FILL);
		this.fill.setTextAlign(Align.CENTER);

		this.stroke = graphicFactory.createPaint();
		this.stroke.setColor(Color.BLACK);
		this.stroke.setStyle(Style.STROKE);
		this.stroke.setTextAlign(Align.CENTER);

		extractValues(graphicFactory, displayModel, elementName, pullParser);
	}

	@Override
	public void destroy() {
		// no-op
	}

	@Override
	public void renderNode(RenderCallback renderCallback, PointOfInterest poi, Tile tile) {
		// do nothing
	}

	@Override
	public void renderWay(RenderCallback renderCallback, PolylineContainer way) {
		String caption = this.textKey.getValue(way.getTags());
		if (caption == null) {
			return;
		}
		renderCallback.renderWayText(way, priority, caption, this.dy, this.fill, this.stroke);
	}

	@Override
	public void scaleStrokeWidth(float scaleFactor) {
		// do nothing
	}

	@Override
	public void scaleTextSize(float scaleFactor) {
		this.fill.setTextSize(this.fontSize * scaleFactor);
		this.stroke.setTextSize(this.fontSize * scaleFactor);
	}

	private void extractValues(GraphicFactory graphicFactory, DisplayModel displayModel, String elementName,
	                           XmlPullParser pullParser) throws XmlPullParserException {
		FontFamily fontFamily = FontFamily.DEFAULT;
		FontStyle fontStyle = FontStyle.NORMAL;

		for (int i = 0; i < pullParser.getAttributeCount(); ++i) {
			String name = pullParser.getAttributeName(i);
			String value = pullParser.getAttributeValue(i);

			switch (name) {
				case K:
					this.textKey = TextKey.getInstance(value);
					break;
				case CAT:
					this.category = value;
					break;
				case DY:
					this.dy = Float.parseFloat(value) * displayModel.getScaleFactor();
					break;
				case FONT_FAMILY:
					fontFamily = FontFamily.valueOf(value.toUpperCase(Locale.ENGLISH));
					break;
				case FONT_STYLE:
					fontStyle = FontStyle.valueOf(value.toUpperCase(Locale.ENGLISH));
					break;
				case FONT_SIZE:
					this.fontSize = XmlUtils.parseNonNegativeFloat(name, value) * displayModel.getScaleFactor();
					break;
				case FILL:
					this.fill.setColor(XmlUtils.getColor(graphicFactory, value));
					break;
				case PRIORITY:
					this.priority = Integer.parseInt(value);
					break;
				case STROKE:
					this.stroke.setColor(XmlUtils.getColor(graphicFactory, value));
					break;
				case STROKE_WIDTH:
					this.stroke.setStrokeWidth(XmlUtils.parseNonNegativeFloat(name, value) * displayModel.getScaleFactor());
					break;
				default:
					throw XmlUtils.createXmlPullParserException(elementName, name, value, i);
			}
		}

		this.fill.setTypeface(fontFamily, fontStyle);
		this.stroke.setTypeface(fontFamily, fontStyle);

		XmlUtils.checkMandatoryAttribute(elementName, K, this.textKey);
	}

}
