//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 and
 lat/lon GmbH

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/

package org.deegree.rendering.r2d.styling;

import static org.deegree.rendering.r2d.styling.RasterStyling.Overlap.RANDOM;

import java.util.HashMap;

import org.deegree.rendering.r2d.se.unevaluated.Symbolizer;

/**
 * <code>RasterStyling</code>
 * 
 * @author <a href="mailto:schmitz@lat-lon.de">Andreas Schmitz</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class RasterStyling implements Copyable<RasterStyling>, Styling {

    /** Default is 1. */
    public double opacity = 1;

    /***/
    public String redChannel;

    /***/
    public String greenChannel;

    /***/
    public String blueChannel;

    /***/
    public String grayChannel;

    /***/
    public HashMap<String, ContrastEnhancement> channelContrastEnhancements;

    /** Default is RANDOM. */
    public Overlap overlap = RANDOM;

    // TODO colormap, function stuff

    /** Default is no contrast enhancement. */
    public ContrastEnhancement contrastEnhancement;

    /** Default is no shaded relief. */
    public ShadedRelief shaded;

    /** Default is no image outline (should be line or polygon parameterized). */
    public Symbolizer<?> imageOutline;

    /**
     * <code>ShadedRelief</code>
     * 
     * @author <a href="mailto:schmitz@lat-lon.de">Andreas Schmitz</a>
     * @author last edited by: $Author$
     * 
     * @version $Revision$, $Date$
     */
    public static class ShadedRelief implements Copyable<ShadedRelief> {
        /** Default is false. */
        public boolean brightnessOnly;

        /** Default is 55. */
        public double reliefFactor = 55;

        public ShadedRelief copy() {
            ShadedRelief copy = new ShadedRelief();

            copy.brightnessOnly = brightnessOnly;
            copy.reliefFactor = reliefFactor;

            return copy;
        }
    }

    /**
     * <code>ContrastEnhancement</code>
     * 
     * @author <a href="mailto:schmitz@lat-lon.de">Andreas Schmitz</a>
     * @author last edited by: $Author$
     * 
     * @version $Revision$, $Date$
     */
    public static class ContrastEnhancement implements Copyable<ContrastEnhancement> {
        /***/
        public boolean normalize;

        /***/
        public boolean histogram;

        /** Default is 1 == no gamma correction. */
        public double gamma = 1;

        public ContrastEnhancement copy() {
            ContrastEnhancement copy = new ContrastEnhancement();

            copy.normalize = normalize;
            copy.histogram = histogram;
            copy.gamma = gamma;

            return copy;
        }
    }

    /**
     * <code>Overlap</code>
     * 
     * @author <a href="mailto:schmitz@lat-lon.de">Andreas Schmitz</a>
     * @author last edited by: $Author$
     * 
     * @version $Revision$, $Date$
     */
    public static enum Overlap {
        /** */
        LATEST_ON_TOP,
        /** */
        EARLIEST_ON_TOP,
        /** */
        AVERAGE,
        /** */
        RANDOM
    }

    public RasterStyling copy() {
        RasterStyling copy = new RasterStyling();

        copy.opacity = opacity;
        copy.redChannel = redChannel;
        copy.greenChannel = greenChannel;
        copy.blueChannel = blueChannel;
        if ( channelContrastEnhancements != null ) {
            copy.channelContrastEnhancements = new HashMap<String, ContrastEnhancement>();
            for ( String chan : channelContrastEnhancements.keySet() ) {
                copy.channelContrastEnhancements.put( chan, channelContrastEnhancements.get( chan ).copy() );
            }
        }
        copy.overlap = overlap;
        copy.contrastEnhancement = contrastEnhancement == null ? null : contrastEnhancement.copy();
        copy.shaded = shaded == null ? null : shaded.copy();
        // should be able to share the symbolizers:
        copy.imageOutline = imageOutline;

        return copy;
    }

}
