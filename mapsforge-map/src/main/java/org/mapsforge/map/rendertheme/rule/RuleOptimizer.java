/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
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

import java.util.Deque;
import java.util.logging.Logger;

final class RuleOptimizer {
	private static final Logger LOGGER = Logger.getLogger(RuleOptimizer.class.getName());

	static AttributeMatcher optimize(AttributeMatcher attributeMatcher, Deque<Rule> ruleStack) {
		if (attributeMatcher instanceof AnyMatcher || attributeMatcher instanceof NegativeMatcher) {
			return attributeMatcher;
		} else if (attributeMatcher instanceof KeyMatcher) {
			return optimizeKeyMatcher(attributeMatcher, ruleStack);
		} else if (attributeMatcher instanceof ValueMatcher) {
			return optimizeValueMatcher(attributeMatcher, ruleStack);
		}

		throw new IllegalArgumentException("unknown AttributeMatcher: " + attributeMatcher);
	}

	static ClosedMatcher optimize(ClosedMatcher closedMatcher, Deque<Rule> ruleStack) {
		if (closedMatcher instanceof AnyMatcher) {
			return closedMatcher;
		}

		for (Rule aRuleStack : ruleStack) {
			if (aRuleStack.closedMatcher.isCoveredBy(closedMatcher)) {
				return AnyMatcher.INSTANCE;
			} else if (!closedMatcher.isCoveredBy(aRuleStack.closedMatcher)) {
				LOGGER.warning("unreachable rule (closed)");
			}
		}

		return closedMatcher;
	}

	static ElementMatcher optimize(ElementMatcher elementMatcher, Deque<Rule> ruleStack) {
		if (elementMatcher instanceof AnyMatcher) {
			return elementMatcher;
		}

		for (Rule rule : ruleStack) {
			if (rule.elementMatcher.isCoveredBy(elementMatcher)) {
				return AnyMatcher.INSTANCE;
			} else if (!elementMatcher.isCoveredBy(rule.elementMatcher)) {
				LOGGER.warning("unreachable rule (e)");
			}
		}

		return elementMatcher;
	}

	private static AttributeMatcher optimizeKeyMatcher(AttributeMatcher attributeMatcher, Deque<Rule> ruleStack) {
		for (Rule rule : ruleStack) {
			if (rule instanceof PositiveRule) {
				PositiveRule positiveRule = (PositiveRule) rule;
				if (positiveRule.keyMatcher.isCoveredBy(attributeMatcher)) {
					return AnyMatcher.INSTANCE;
				}
			}
		}

		return attributeMatcher;
	}

	private static AttributeMatcher optimizeValueMatcher(AttributeMatcher attributeMatcher, Deque<Rule> ruleStack) {
		for (Rule rule : ruleStack) {
			if (rule instanceof PositiveRule) {
				PositiveRule positiveRule = (PositiveRule) rule;
				if (positiveRule.valueMatcher.isCoveredBy(attributeMatcher)) {
					return AnyMatcher.INSTANCE;
				}
			}
		}

		return attributeMatcher;
	}

	private RuleOptimizer() {
		throw new IllegalStateException();
	}
}
