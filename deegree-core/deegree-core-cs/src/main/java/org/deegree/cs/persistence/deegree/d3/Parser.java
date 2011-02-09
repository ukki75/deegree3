//$HeadURL$
/*----------------    FILE HEADER  ------------------------------------------
 This file is part of deegree.
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 http://www.giub.uni-bonn.de/deegree/
 lat/lon GmbH
 http://www.lat-lon.de

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.
 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 Contact:

 Andreas Poth
 lat/lon GmbH
 Aennchenstr. 19
 53177 Bonn
 Germany
 E-Mail: poth@lat-lon.de

 Prof. Dr. Klaus Greve
 Department of Geography
 University of Bonn
 Meckenheimer Allee 166
 53115 Bonn
 Germany
 E-Mail: greve@giub.uni-bonn.de
 ---------------------------------------------------------------------------*/

package org.deegree.cs.persistence.deegree.d3;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.deegree.commons.xml.stax.StAXParsingHelper;
import org.deegree.cs.CRSCodeType;
import org.deegree.cs.CRSIdentifiable;
import org.deegree.cs.components.Ellipsoid;
import org.deegree.cs.components.GeodeticDatum;
import org.deegree.cs.components.PrimeMeridian;
import org.deegree.cs.coordinatesystems.CoordinateSystem;
import org.deegree.cs.coordinatesystems.GeographicCRS;
import org.deegree.cs.exceptions.CRSConfigurationException;
import org.deegree.cs.persistence.deegree.CRSParser;
import org.deegree.cs.persistence.deegree.DeegreeCRSStore;
import org.deegree.cs.persistence.deegree.d3.parsers.CoordinateSystemParser;
import org.deegree.cs.persistence.deegree.d3.parsers.DatumParser;
import org.deegree.cs.persistence.deegree.d3.parsers.EllipsoidParser;
import org.deegree.cs.persistence.deegree.d3.parsers.PrimemeridianParser;
import org.deegree.cs.persistence.deegree.d3.parsers.ProjectionParser;
import org.deegree.cs.persistence.deegree.d3.parsers.TransformationParser;
import org.deegree.cs.projections.Projection;
import org.deegree.cs.transformations.Transformation;
import org.deegree.cs.transformations.TransformationFactory;
import org.deegree.cs.transformations.helmert.Helmert;

/**
 * The <code>CRSParser</code> holds the instances to the StAX based crs components parsers.
 * 
 * @author <a href="mailto:bezema@lat-lon.de">Rutger Bezema</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class Parser implements CRSParser<StAXResource> {

    /** Default namespace of the crs configuration */
    public static final String CRS_NS = "http://www.deegree.org/crs";

    private final org.deegree.cs.persistence.deegree.d3.parsers.CoordinateSystemParser crs;

    private final DatumParser datums;

    private final ProjectionParser proj;

    private final TransformationParser trans;

    private final EllipsoidParser ellips;

    private final PrimemeridianParser pm;

    /**
     * @param provider
     * @param properties
     */
    public Parser( DeegreeCRSStore<StAXResource> provider, URL resolvedURL ) {
        TransformationFactory.DSTransform datumShift = TransformationFactory.DSTransform.HELMERT;
        if ( resolvedURL == null ) {
            throw new CRSConfigurationException(
                                                 "Could not instantiate crs definitions, please make sure the coordinate system definitions are on the class path." );
        }

        try {
            XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader( resolvedURL.toExternalForm(),
                                                                                          resolvedURL.openStream() );
            // CRSConfigurations
            StAXParsingHelper.nextElement( reader );
            if ( reader.getName().equals( new QName( CRS_NS, "CRSConfiguration" ) ) ) {
                // ProjectionsFile.
                StAXParsingHelper.nextElement( reader );
                /* instantiate the parsers */
                String cUrl = StAXParsingHelper.getText( reader, new QName( CRS_NS, "ProjectionsFile" ),
                                                         "projection-definitions.xml", true );
                URL url = StAXParsingHelper.resolve( cUrl, reader );
                proj = new ProjectionParser( provider, url );

                cUrl = StAXParsingHelper.getText( reader, new QName( CRS_NS, "TransformationsFile" ),
                                                  "transformation-definitions.xml", true );
                url = StAXParsingHelper.resolve( cUrl, reader );
                trans = new TransformationParser( provider, url, datumShift );

                cUrl = StAXParsingHelper.getText( reader, new QName( CRS_NS, "PrimeMeridiansFile" ),
                                                  "pm-definitions.xml", true );
                url = StAXParsingHelper.resolve( cUrl, reader );
                pm = new PrimemeridianParser( provider, url );

                cUrl = StAXParsingHelper.getText( reader, new QName( CRS_NS, "EllispoidsFile" ),
                                                  "ellipsoid-definitions.xml", true );
                url = StAXParsingHelper.resolve( cUrl, reader );
                ellips = new EllipsoidParser( provider, url );

                cUrl = StAXParsingHelper.getText( reader, new QName( CRS_NS, "DatumsFile" ), "datum-definitions.xml",
                                                  true );
                url = StAXParsingHelper.resolve( cUrl, reader );
                datums = new DatumParser( provider, url );

                cUrl = StAXParsingHelper.getText( reader, new QName( CRS_NS, "CRSsFile" ), "crs-definitions.xml", true );
                url = StAXParsingHelper.resolve( cUrl, reader );
                crs = new CoordinateSystemParser( provider, url );
            } else {
                throw new CRSConfigurationException(
                                                     "Could not instantiate crs definitions because the root element is not {"
                                                                             + CRS_NS + "}:CRSConfiguration." );
            }
        } catch ( XMLStreamException e ) {
            throw new CRSConfigurationException( "Could not instantiate crs definitions because: "
                                                 + e.getLocalizedMessage() );
        } catch ( FactoryConfigurationError e ) {
            throw new CRSConfigurationException( "Could not instantiate crs definitions because: "
                                                 + e.getLocalizedMessage() );
        } catch ( IOException e ) {
            throw new CRSConfigurationException( "Could not instantiate crs definitions because: "
                                                 + e.getLocalizedMessage() );
        }
    }

    @Override
    public Transformation getTransformation( CoordinateSystem sourceCRS, CoordinateSystem targetCRS ) {
        return trans.getTransformation( sourceCRS, targetCRS );
    }

    @Override
    public Helmert getWGS84Transformation( GeographicCRS sourceCRS ) {
        return trans.getConversionInfo( sourceCRS );
    }

    @Override
    public StAXResource getURIAsType( String uri )
                            throws IOException {
        return new StAXResource( uri );
    }

    public StAXResource getURIAsType( List<String> uris )
                            throws IOException {
        for ( String uri : uris ) {
            StAXResource result = getURIAsType( uri );
            if ( result != null )
                return result;
        }
        return null;
    }

    @Override
    public List<CRSCodeType[]> getAvailableCRSCodes()
                            throws CRSConfigurationException {
        return crs.getAvailableCRSs();
    }

    @Override
    public Ellipsoid getEllipsoidForId( String ellipsoidId )
                            throws CRSConfigurationException {
        return ellips.getEllipsoidForId( ellipsoidId );
    }

    @Override
    public GeodeticDatum getGeodeticDatumForId( String datumId )
                            throws CRSConfigurationException {
        return datums.getGeodeticDatumForId( datumId );
    }

    @Override
    public PrimeMeridian getPrimeMeridianForId( String meridianId )
                            throws CRSConfigurationException {
        return pm.getPrimeMeridianForId( meridianId );
    }

    @Override
    public Projection getProjectionForId( String id ) {
        return proj.getProjectionForId( id );
    }

    @Override
    public String getVersion()
                            throws CRSConfigurationException {
        return "0.5.0";
    }

    @Override
    public CoordinateSystem parseCoordinateSystem( StAXResource crsDefintion )
                            throws CRSConfigurationException {
        return crs.getCRSForId( crsDefintion.getRequestedId() );
    }

    @Override
    public CRSIdentifiable parseIdentifiableObject( String id ) {
        CRSIdentifiable result = crs.getCRSForId( id );
        if ( result == null ) {
            result = ellips.getEllipsoidForId( id );
        }
        if ( result == null ) {
            result = datums.getGeodeticDatumForId( id );
        }
        if ( result == null ) {
            result = pm.getPrimeMeridianForId( id );
        }
        if ( result == null ) {
            result = proj.getProjectionForId( id );
        }
        if ( result == null ) {
            result = trans.getTransformationForId( id );
        }
        return result;
    }

    @Override
    public Transformation parseTransformation( StAXResource transformationDefinition ) {
        return trans.getTransformationForId( transformationDefinition.getRequestedId() );
    }

}
