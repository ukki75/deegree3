//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2011 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

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
package org.deegree.metadata;

import static org.deegree.metadata.DCRecord.DC_RECORD_NS;
import static org.deegree.metadata.ISORecord.ISO_RECORD_NS;

import java.io.File;

import org.apache.axiom.om.OMElement;
import org.deegree.commons.xml.XMLAdapter;

/**
 * Main entry point for creating {@link MetadataRecord} instances from XML representations.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class MetadataRecordFactory {

    /**
     * Creates a new {@link MetadataRecord} from the given element.
     * 
     * @param rootEl
     *            root element, must not be <code>null</code>
     * @return metadata record instance, never <code>null</code>
     * @throws IllegalArgumentException
     *             if the metadata format is unknown / record invalid
     */
    public static MetadataRecord create( OMElement rootEl )
                            throws IllegalArgumentException {
        String ns = rootEl.getNamespace().getNamespaceURI();
        if ( ISO_RECORD_NS.equals( ns ) ) {
            return new ISORecord( rootEl );
        }
        if ( DC_RECORD_NS.equals( ns ) ) {
            throw new UnsupportedOperationException( "Creating DC records from XML is not implemented yet." );
        }
        throw new IllegalArgumentException( "Unknown / unsuppported metadata namespace '" + ns + "'." );
    }

    /**
     * Creates a new {@link MetadataRecord} from the given file.
     * 
     * @param file
     *            record file, must not be <code>null</code>
     * @return metadata record instance, never <code>null</code>
     * @throws IllegalArgumentException
     *             if the metadata format is unknown / record invalid
     */
    public static MetadataRecord create( File file )
                            throws IllegalArgumentException {
        return create( new XMLAdapter( file ).getRootElement() );
    }
}