/*
 * The Exomiser - A tool to annotate and prioritize genomic variants
 *
 * Copyright (c) 2016-2017 Queen Mary University of London.
 * Copyright (c) 2012-2016 Charité Universitätsmedizin Berlin and Genome Research Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jax.svanna.core.filter;

/**
 * A FilterResult object gets attached to each Variant object as a result of the
 * filtering of the variants according to various criteria.
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public interface FilterResult {

    static FilterResult pass(FilterType filterType) {
        return new PassFilterResult(filterType);
    }

    static FilterResult fail(FilterType filterType) {
        return new FailFilterResult(filterType);
    }

    static FilterResult notRun(FilterType filterType) {
        return new NotRunFilterResult(filterType);
    }

    FilterType getFilterType();

    boolean passed();

    boolean failed();

    boolean wasRun();

    enum Status {
        PASS, FAIL, NOT_RUN
    }
}
