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
package org.mapsforge.map.rendertheme.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.layer.renderer.PolylineContainer;
import org.mapsforge.map.reader.PointOfInterest;
import org.mapsforge.map.rendertheme.RenderCallback;
import org.mapsforge.map.rendertheme.renderinstruction.RenderInstruction;

abstract class Rule {
	private static final ArrayList<Rule> EMPTY_LIST = new ArrayList<Rule>() {
		@Override
		public Iterator<Rule> iterator() {
			return Collections.emptyIterator();
		}
	};

	static final Map<List<String>, AttributeMatcher> MATCHERS_CACHE_KEY = new HashMap<>();
	static final Map<List<String>, AttributeMatcher> MATCHERS_CACHE_VALUE = new HashMap<>();

	String cat;
	final ClosedMatcher closedMatcher;
	final ElementMatcher elementMatcher;
	final byte zoomMax;
	final byte zoomMin;
	private final ArrayList<RenderInstruction> renderInstructions; // NOPMD we need specific interface
	private ArrayList<Rule> subRules; // NOPMD we need specific interface

	Rule(RuleBuilder ruleBuilder) {
		this.cat = ruleBuilder.cat;
		this.closedMatcher = ruleBuilder.closedMatcher;
		this.elementMatcher = ruleBuilder.elementMatcher;
		this.zoomMax = ruleBuilder.zoomMax;
		this.zoomMin = ruleBuilder.zoomMin;

		this.renderInstructions = new ArrayList<>(4);
		this.subRules = new ArrayList<>(4);
	}

	void addRenderingInstruction(RenderInstruction renderInstruction) {
		this.renderInstructions.add(renderInstruction);
	}

	void addSubRule(Rule rule) {
		this.subRules.add(rule);
	}

	void destroy() {
		for (RenderInstruction ri : this.renderInstructions) {
			ri.destroy();
		}
		for (Rule sr : this.subRules) {
			sr.destroy();
		}
	}

	abstract boolean matchesNode(List<Tag> tags, byte zoomLevel);

	abstract boolean matchesWay(List<Tag> tags, byte zoomLevel, Closed closed);

	void matchNode(RenderCallback renderCallback, PointOfInterest pointOfInterest, Tile tile,
	               List<RenderInstruction> matchingList ) {
		if (matchesNode(pointOfInterest.tags, tile.zoomLevel)) {
			for (RenderInstruction renderInstruction : this.renderInstructions) {
				renderInstruction.renderNode(renderCallback, pointOfInterest, tile);
				matchingList.add(renderInstruction);
			}
			for (Rule subRule : this.subRules) {
				subRule.matchNode(renderCallback, pointOfInterest, tile, matchingList);
			}
		}
	}

	void matchWay(RenderCallback renderCallback, PolylineContainer way, Tile tile, Closed closed,
			List<RenderInstruction> matchingList) {
		if (matchesWay(way.getTags(), tile.zoomLevel, closed)) {
			for (RenderInstruction renderInstruction : this.renderInstructions) {
				renderInstruction.renderWay(renderCallback, way);
				matchingList.add(renderInstruction);
			}
			for (Rule subRule : this.subRules) {
				subRule.matchWay(renderCallback, way, tile, closed, matchingList);
			}
		}
	}

	void onComplete() {
		MATCHERS_CACHE_KEY.clear();
		MATCHERS_CACHE_VALUE.clear();

		this.renderInstructions.trimToSize();

		if (subRules.isEmpty()) {
			subRules = EMPTY_LIST;
		}
		else {
			this.subRules.trimToSize();
			for (Rule subRule : this.subRules) {
				subRule.onComplete();
			}
		}
	}

	void scaleStrokeWidth(float scaleFactor) {
		for (RenderInstruction renderInstruction : this.renderInstructions) {
			renderInstruction.scaleStrokeWidth(scaleFactor);
		}
		for (Rule subRule : this.subRules) {
			subRule.scaleStrokeWidth(scaleFactor);
		}
	}

	void scaleTextSize(float scaleFactor) {
		for (RenderInstruction renderInstruction : this.renderInstructions) {
			renderInstruction.scaleTextSize(scaleFactor);
		}
		for (Rule subRule : this.subRules) {
			subRule.scaleTextSize(scaleFactor);
		}
	}
}
