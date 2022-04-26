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
package org.monarchinitiative.svanna.core.filter;

import java.util.Locale;
import java.util.Objects;

/**
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
abstract class AbstractFilterResult implements FilterResult {

    private final FilterType filterType;
    private final Status status;

    AbstractFilterResult(FilterType filterType, Status status) {
        this.filterType = filterType;
        this.status = status;
    }

    @Override
    public FilterType getFilterType() {
        return filterType;
    }

    @Override
    public boolean passed() {
        return status == Status.PASS;
    }

    @Override
    public boolean failed() {
        return status == Status.FAIL;
    }

    @Override
    public boolean wasRun() {
        return status != Status.NOT_RUN;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.filterType);
        hash = 59 * hash + Objects.hashCode(this.status);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AbstractFilterResult other = (AbstractFilterResult) obj;
        if (this.filterType != other.filterType) {
            return false;
        }
        return this.status == other.status;
    }

    @Override
    public String toString() {
        return String.format(Locale.UK, "Filter=%s status=%s", filterType, status);
    }

}
