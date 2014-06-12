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
package org.mapsforge.core.model;

import org.mapsforge.core.util.MercatorProjection;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * A tile represents a rectangular part of the world map. All tiles can be identified by their X and Y number together
 * with their zoom level. The actual area that a tile covers on a map depends on the underlying map projection.
 */
public class Tile implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * @return the maximum valid tile number for the given zoom level, 2<sup>zoomLevel</sup> -1.
	 */
	public static int getMaxTileNumber(byte zoomLevel) {
		if (zoomLevel < 0) {
			throw new IllegalArgumentException("zoomLevel must not be negative: " + zoomLevel);
		} else if (zoomLevel == 0) {
			return 0;
		}
		return (2 << zoomLevel - 1) - 1;
	}

	public final int tileSize;

	/**
	 * The X number of this tile.
	 */
	public final int tileX;

	/**
	 * The Y number of this tile.
	 */
	public final int tileY;

	/**
	 * The zoom level of this tile.
	 */
	public final byte zoomLevel;


	private Point origin;

	/**
	 * @param tileX
	 *            the X number of the tile.
	 * @param tileY
	 *            the Y number of the tile.
	 * @param zoomLevel
	 *            the zoom level of the tile.
	 * @throws IllegalArgumentException
	 *             if any of the parameters is invalid.
	 */
	public Tile(int tileX, int tileY, byte zoomLevel, int tileSize) {
		if (tileX < 0) {
			throw new IllegalArgumentException("tileX must not be negative: " + tileX);
		} else if (tileY < 0) {
			throw new IllegalArgumentException("tileY must not be negative: " + tileY);
		} else if (zoomLevel < 0) {
			throw new IllegalArgumentException("zoomLevel must not be negative: " + zoomLevel);
		}

		long maxTileNumber = getMaxTileNumber(zoomLevel);
		if (tileX > maxTileNumber) {
			throw new IllegalArgumentException("invalid tileX number on zoom level " + zoomLevel + ": " + tileX);
		} else if (tileY > maxTileNumber) {
			throw new IllegalArgumentException("invalid tileY number on zoom level " + zoomLevel + ": " + tileY);
		}

		this.tileSize = tileSize;
		this.tileX = tileX;
		this.tileY = tileY;
		this.zoomLevel = zoomLevel;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (!(obj instanceof Tile)) {
			return false;
		}
		Tile other = (Tile) obj;
		if (this.tileX != other.tileX) {
			return false;
		} else if (this.tileY != other.tileY) {
			return false;
		} else if (this.zoomLevel != other.zoomLevel) {
			return false;
		} else if (this.tileSize != other.tileSize) {
			return false;
		}
		return true;
	}

	/**
	 * Returns a set of the eight neighbours of this tile.
	 * @return neighbour tiles as a set
	 */
	public Set<Tile> getNeighbours() {
		Set<Tile> neighbours = new HashSet<Tile>(8);
		neighbours.add(getLeft());
		neighbours.add(getAboveLeft());
		neighbours.add(getAbove());
		neighbours.add(getAboveRight());
		neighbours.add(getRight());
		neighbours.add(getBelowRight());
		neighbours.add(getBelow());
		neighbours.add(getBelowLeft());
		return neighbours;
	}

	/**
	 * Extend of this tile in absolute coordinates.
	 * @return rectangle with the absolute coordinates.
	 */
	public Rectangle getBoundaryAbsolute() {
		return new Rectangle(getOrigin().x, getOrigin().y, getOrigin().x + tileSize, getOrigin().y + tileSize);
	}

	/**
	 * Returns the top-left point of this tile in absolute coordinates.
	 * @return the top-left point
	 */
	public Point getOrigin() {
		if (this.origin == null) {
			double x = MercatorProjection.tileToPixel(this.tileX, this.tileSize);
			double y = MercatorProjection.tileToPixel(this.tileY, this.tileSize);
			this.origin = new Point(x, y);
		}
		return this.origin;
	}

	/**
	 * Returns the tile to the left of this tile.
	 * @return tile to the left.
	 */
	public Tile getLeft() {
		int x = tileX - 1;
		if (x < 0) {
			x = getMaxTileNumber(this.zoomLevel);
		}
		return createNeighbourTile(this.tileY, x);
	}

	/**
	 * Returns the tile to the right of this tile.
	 * @return
	 */
	public Tile getRight() {
		int x = tileX + 1;
		if (x > getMaxTileNumber(this.zoomLevel)) {
			x = 0;
		}
		return createNeighbourTile(this.tileY, x);
	}

	public Tile getAbove() {
		int y = tileY - 1;
		if (y < 0) {
			y = getMaxTileNumber(this.zoomLevel);
		}
		return createNeighbourTile(y, this.tileX);
	}

	public Tile getBelow() {
		int y = tileY + 1;
		if (y > getMaxTileNumber(this.zoomLevel)) {
			y = 0;
		}
		return createNeighbourTile(y, this.tileX);
	}

	public Tile getAboveLeft() {
		int y = tileY - 1;
		int x = tileX - 1;
		if (y < 0) {
			y = getMaxTileNumber(this.zoomLevel);
		}
		if (x < 0) {
			x = getMaxTileNumber(this.zoomLevel);
		}
		return createNeighbourTile(y, x);
	}

	public Tile getAboveRight() {
		int y = tileY - 1;
		int x = tileX + 1;
		if (y < 0) {
			y = getMaxTileNumber(this.zoomLevel);
		}
		if (x > getMaxTileNumber(this.zoomLevel)) {
			x = 0;
		}
		return createNeighbourTile(y, x);
	}

	public Tile getBelowLeft() {
		int y = tileY + 1;
		int x = tileX - 1;
		if (y > getMaxTileNumber(this.zoomLevel)) {
			y = 0;
		}
		if (x < 0) {
			x = getMaxTileNumber(this.zoomLevel);
		}
		return createNeighbourTile(y, x);
	}
 
	protected Tile createNeighbourTile(int y, int x) {
        return new Tile(x, y, this.zoomLevel, this.tileSize);
    }

    public Tile getBelowRight() {
		int y = tileY + 1;
	    int x = tileX + 1;
		if (y > getMaxTileNumber(this.zoomLevel)) {
			y = 0;
		}
		if (x > getMaxTileNumber(this.zoomLevel)) {
			x = 0;
		}
		return createNeighbourTile(y, x);
	}

	/**
	 * @return the parent tile of this tile or null, if the zoom level of this tile is 0.
	 */
	public Tile getParent() {
		if (this.zoomLevel == 0) {
			return null;
		}

		return new Tile(this.tileX / 2, this.tileY / 2, (byte) (this.zoomLevel - 1), this.tileSize);
	}

	public int getShiftX(Tile otherTile) {
		if (this.equals(otherTile)) {
			return 0;
		}

		return this.tileX % 2 + 2 * getParent().getShiftX(otherTile);
	}

	public int getShiftY(Tile otherTile) {
		if (this.equals(otherTile)) {
			return 0;
		}

		return this.tileY % 2 + 2 * getParent().getShiftY(otherTile);
	}

	@Override
	public int hashCode() {
		int result = 7;
		result = 31 * result + (int) (this.tileX ^ (this.tileX >>> 16));
		result = 31 * result + (int) (this.tileY ^ (this.tileY >>> 16));
		result = 31 * result + this.zoomLevel;
		result = 31 * result + this.tileSize;
		return result;
	}

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("x=");
		stringBuilder.append(this.tileX);
		stringBuilder.append(", y=");
		stringBuilder.append(this.tileY);
		stringBuilder.append(", z=");
		stringBuilder.append(this.zoomLevel);
		return stringBuilder.toString();
	}
}
