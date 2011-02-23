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

package org.deegree.feature.persistence.postgis;

import static java.sql.Statement.RETURN_GENERATED_KEYS;
import static org.deegree.gml.GMLVersion.GML_32;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.deegree.commons.jdbc.QTableName;
import org.deegree.commons.tom.TypedObjectNode;
import org.deegree.commons.tom.genericxml.GenericXMLElement;
import org.deegree.commons.tom.genericxml.GenericXMLElementContent;
import org.deegree.commons.tom.ows.CodeType;
import org.deegree.commons.tom.primitive.PrimitiveValue;
import org.deegree.commons.tom.primitive.SQLValueMangler;
import org.deegree.commons.utils.JDBCUtils;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.feature.Feature;
import org.deegree.feature.FeatureCollection;
import org.deegree.feature.persistence.FeatureStore;
import org.deegree.feature.persistence.FeatureStoreException;
import org.deegree.feature.persistence.FeatureStoreTransaction;
import org.deegree.feature.persistence.lock.Lock;
import org.deegree.feature.persistence.query.FeatureResultSet;
import org.deegree.feature.persistence.query.Query;
import org.deegree.feature.persistence.sql.FeatureTypeMapping;
import org.deegree.feature.persistence.sql.MappedApplicationSchema;
import org.deegree.feature.persistence.sql.blob.BlobCodec;
import org.deegree.feature.persistence.sql.blob.BlobMapping;
import org.deegree.feature.persistence.sql.expressions.JoinChain;
import org.deegree.feature.persistence.sql.id.FIDMapping;
import org.deegree.feature.persistence.sql.id.IDGenerator;
import org.deegree.feature.persistence.sql.id.IdAnalysis;
import org.deegree.feature.persistence.sql.id.UUIDGenerator;
import org.deegree.feature.persistence.sql.insert.InsertRow;
import org.deegree.feature.persistence.sql.insert.InsertRowManager;
import org.deegree.feature.persistence.sql.insert.InsertRowNode;
import org.deegree.feature.persistence.sql.rules.CodeMapping;
import org.deegree.feature.persistence.sql.rules.CompoundMapping;
import org.deegree.feature.persistence.sql.rules.FeatureMapping;
import org.deegree.feature.persistence.sql.rules.GeometryMapping;
import org.deegree.feature.persistence.sql.rules.Mapping;
import org.deegree.feature.persistence.sql.rules.PrimitiveMapping;
import org.deegree.feature.property.Property;
import org.deegree.feature.xpath.FeatureXPathEvaluator;
import org.deegree.filter.Filter;
import org.deegree.filter.FilterEvaluationException;
import org.deegree.filter.IdFilter;
import org.deegree.filter.OperatorFilter;
import org.deegree.filter.expression.PropertyName;
import org.deegree.filter.sql.DBField;
import org.deegree.filter.sql.MappingExpression;
import org.deegree.geometry.Envelope;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.GeometryTransformer;
import org.deegree.geometry.io.WKBWriter;
import org.deegree.gml.feature.FeatureReference;
import org.deegree.protocol.wfs.transaction.IDGenMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link FeatureStoreTransaction} implementation used by the {@link PostGISFeatureStore}.
 * 
 * @see PostGISFeatureStore
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class PostGISFeatureStoreTransaction implements FeatureStoreTransaction {

    private static final Logger LOG = LoggerFactory.getLogger( PostGISFeatureStoreTransaction.class );

    private final PostGISFeatureStore fs;

    private final MappedApplicationSchema schema;

    private final BlobMapping blobMapping;

    private final TransactionManager taManager;

    private final Connection conn;

    /**
     * Creates a new {@link PostGISFeatureStoreTransaction} instance.
     * 
     * @param store
     *            invoking feature store instance, never <code>null</code>
     * @param taManager
     * @param conn
     *            JDBC connection associated with the transaction, never <code>null</code> and has
     *            <code>autocommit</code> set to <code>false</code>
     * @param schema
     */
    PostGISFeatureStoreTransaction( PostGISFeatureStore store, TransactionManager taManager, Connection conn,
                                    MappedApplicationSchema schema ) {
        this.fs = store;
        this.taManager = taManager;
        this.conn = conn;
        this.schema = schema;
        blobMapping = schema.getBlobMapping();
    }

    @Override
    public void commit()
                            throws FeatureStoreException {

        LOG.debug( "Committing transaction." );
        try {
            conn.commit();
            fs.clearEnvelopeCache();
        } catch ( SQLException e ) {
            LOG.debug( e.getMessage(), e );
            LOG.debug( e.getMessage(), e.getNextException() );
            throw new FeatureStoreException( "Unable to commit SQL transaction: " + e.getMessage() );
        } finally {
            taManager.releaseTransaction( this );
        }
    }

    @Override
    public FeatureStore getStore() {
        return fs;
    }

    /**
     * Returns the underlying JDBC connection. Can be used for performing other operations in the same transaction
     * context.
     * 
     * @return the underlying JDBC connection, never <code>null</code>
     */
    public Connection getConnection() {
        return conn;
    }

    @Override
    public int performDelete( QName ftName, OperatorFilter filter, Lock lock )
                            throws FeatureStoreException {

        // TODO implement this more efficiently
        return performDelete( getIdFilter( ftName, filter ), lock );
    }

    @Override
    public int performDelete( IdFilter filter, Lock lock )
                            throws FeatureStoreException {

        int deleted = 0;
        if ( blobMapping != null ) {
            deleted = performDeleteBlob( filter, lock );
        } else {
            deleted = performDeleteRelational( filter, lock );
        }
        return deleted;
    }

    private int performDeleteBlob( IdFilter filter, Lock lock )
                            throws FeatureStoreException {

        // TODO implement this more efficiently (using IN / temporary tables)
        int deleted = 0;
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement( "DELETE FROM " + blobMapping.getTable() + " WHERE "
                                          + blobMapping.getGMLIdColumn() + "=?" );
            for ( String id : filter.getMatchingIds() ) {
                stmt.setString( 1, id );
                stmt.addBatch();
            }
            int[] deletes = stmt.executeBatch();
            for ( int noDeleted : deletes ) {
                deleted += noDeleted;
            }
        } catch ( SQLException e ) {
            LOG.debug( e.getMessage(), e );
            throw new FeatureStoreException( e.getMessage(), e );
        } finally {
            JDBCUtils.close( stmt );
        }
        LOG.debug( "Deleted " + deleted + " features." );
        return deleted;
    }

    private int performDeleteRelational( IdFilter filter, Lock lock )
                            throws FeatureStoreException {
        int deleted = 0;
        for ( String id : filter.getMatchingIds() ) {
            LOG.debug( "Analyzing id: " + id );
            IdAnalysis analysis = null;
            try {
                analysis = schema.analyzeId( id );
                LOG.debug( "Analysis: " + analysis );
                FeatureTypeMapping ftMapping = schema.getFtMapping( analysis.getFeatureType().getName() );
                FIDMapping fidMapping = ftMapping.getFidMapping();
                PreparedStatement stmt = null;
                try {
                    stmt = conn.prepareStatement( "DELETE FROM " + ftMapping.getFtTable() + " WHERE "
                                                  + fidMapping.getColumn() + "=?" );
                    PrimitiveValue value = new PrimitiveValue( analysis.getIdKernel(), fidMapping.getColumnType() );
                    Object sqlValue = SQLValueMangler.internalToSQL( value );
                    stmt.setObject( 1, sqlValue );
                    deleted += stmt.executeUpdate();
                } catch ( SQLException e ) {
                    LOG.debug( e.getMessage(), e );
                    throw new FeatureStoreException( e.getMessage(), e );
                } finally {
                    JDBCUtils.close( stmt );
                }
            } catch ( IllegalArgumentException e ) {
                throw new FeatureStoreException( "Unable to determine feature type for id '" + id + "'." );
            }
        }
        return deleted;
    }

    @Override
    public List<String> performInsert( FeatureCollection fc, IDGenMode mode )
                            throws FeatureStoreException {

        LOG.debug( "performInsert()" );

        Set<Geometry> geometries = new LinkedHashSet<Geometry>();
        Set<Feature> features = new LinkedHashSet<Feature>();
        Set<String> fids = new LinkedHashSet<String>();
        Set<String> gids = new LinkedHashSet<String>();
        findFeaturesAndGeometries( fc, geometries, features, fids, gids );

        LOG.debug( features.size() + " features / " + geometries.size() + " geometries" );

        long begin = System.currentTimeMillis();

        String fid = null;
        try {
            PreparedStatement blobInsertStmt = null;
            if ( blobMapping != null ) {
                switch ( mode ) {
                case GENERATE_NEW: {
                    // TODO don't change incoming features / geometries
                    for ( Feature feature : features ) {
                        String newFid = "FEATURE_" + generateNewId();
                        String oldFid = feature.getId();
                        if ( oldFid != null ) {
                            fids.remove( oldFid );
                        }
                        fids.add( newFid );
                        feature.setId( newFid );
                    }
                    for ( Geometry geometry : geometries ) {
                        String newGid = "GEOMETRY_" + generateNewId();
                        String oldGid = geometry.getId();
                        if ( oldGid != null ) {
                            gids.remove( oldGid );
                        }
                        gids.add( newGid );
                        geometry.setId( newGid );
                    }
                    break;
                }
                case REPLACE_DUPLICATE: {
                    throw new FeatureStoreException( "REPLACE_DUPLICATE is not available yet." );
                }
                case USE_EXISTING: {
                    // TODO don't change incoming features / geometries
                    for ( Feature feature : features ) {
                        if ( feature.getId() == null ) {
                            String newFid = "FEATURE_" + generateNewId();
                            feature.setId( newFid );
                            fids.add( newFid );
                        }
                    }

                    for ( Geometry geometry : geometries ) {
                        if ( geometry.getId() == null ) {
                            String newGid = "GEOMETRY_" + generateNewId();
                            geometry.setId( newGid );
                            gids.add( newGid );
                        }
                    }
                    break;
                }
                }
                StringBuilder sql = new StringBuilder( "INSERT INTO " );
                sql.append( blobMapping.getTable() );
                sql.append( " (" );
                sql.append( blobMapping.getGMLIdColumn() );
                sql.append( "," );
                sql.append( blobMapping.getTypeColumn() );
                sql.append( "," );
                sql.append( blobMapping.getDataColumn() );
                sql.append( "," );
                sql.append( blobMapping.getBBoxColumn() );
                sql.append( ") VALUES(?,?,?,?)" );
                blobInsertStmt = conn.prepareStatement( sql.toString(), RETURN_GENERATED_KEYS );
                for ( Feature feature : features ) {
                    fid = feature.getId();
                    int internalId = -1;
                    if ( blobInsertStmt != null ) {
                        internalId = insertFeatureBlob( blobInsertStmt, feature );
                    }
                    FeatureTypeMapping ftMapping = fs.getMapping( feature.getName() );
                    if ( ftMapping != null ) {
                        insertFeatureRelational( internalId, feature, ftMapping );
                    }
                }
                if ( blobInsertStmt != null ) {
                    blobInsertStmt.close();
                }
            } else {
                // pure relational mode
                for ( Feature feature : features ) {
                    fid = feature.getId();
                    FeatureTypeMapping ftMapping = fs.getMapping( feature.getName() );
                    if ( ftMapping == null ) {
                        throw new FeatureStoreException( "Cannot insert feature of type '" + feature.getName()
                                                         + "'. No mapping defined and BLOB mode is off." );
                    }
                    fids.add( insertFeatureRelational( feature, ftMapping, mode ) );
                }
            }
        } catch ( SQLException e ) {
            String msg = "Error inserting feature '" + fid + "':" + e.getMessage();
            LOG.debug( msg, e );
            throw new FeatureStoreException( msg, e );
        } catch ( FilterEvaluationException e ) {
            String msg = "Error inserting feature '" + fid + "':" + e.getMessage();
            LOG.debug( msg, e );
            throw new FeatureStoreException( msg, e );
        }

        long elapsed = System.currentTimeMillis() - begin;
        LOG.debug( "Insertion of " + features.size() + " features: " + elapsed + " [ms]" );
        return new ArrayList<String>( fids );
    }

    private String insertFeatureRelational( Feature feature, FeatureTypeMapping ftMapping, IDGenMode mode )
                            throws FeatureStoreException {

        InsertRowManager rowManager = new InsertRowManager();
        InsertRow featureRow = rowManager.newRow( ftMapping.getFtTable(), null );

        FIDMapping fidMapping = ftMapping.getFidMapping();
        IDGenerator generator = fidMapping.getIdGenerator();
        if ( generator instanceof UUIDGenerator ) {
            featureRow.addPreparedArgument( fidMapping.getColumn(), feature.getId() );
        }

        for ( Property prop : feature.getProperties() ) {
            QName propName = prop.getName();
            Mapping mapping = ftMapping.getMapping( propName );
            if ( mapping != null ) {
                TypedObjectNode particle = prop.getValue();
                if ( particle instanceof GenericXMLElementContent ) {
                    particle = new GenericXMLElement( propName, (GenericXMLElementContent) particle );
                }
                JoinChain jc = mapping.getJoinedTable();
                if ( jc == null ) {
                    LOG.debug( "Inserting property '" + propName + "' (feature root table)" );
                    buildInsertRows( mapping, particle, featureRow, rowManager );
                } else {
                    LOG.debug( "Inserting property '" + propName + "' (property table)" );
                    // TODO qualified table name
                    QTableName table = new QTableName( jc.getFields().get( 1 ).getTable() );
                    InsertRow propertyRow = rowManager.newRow( table, "id" );
                    // TODO foreign key parameters
                    rowManager.addForeignKeyRelation( featureRow, fidMapping.getColumn(), propertyRow, "parentfk" );
                    buildInsertRows( mapping, particle, propertyRow, rowManager );
                }
            } else {
                LOG.warn( "No mapping for property '" + propName + "'. Omitting." );
            }
        }

        try {
            rowManager.performInserts( conn );
        } catch ( SQLException e ) {
            throw new FeatureStoreException( e.getMessage() );
        }

        return feature.getId();
    }

    private void buildInsertRows( Mapping mapping, TypedObjectNode particle, InsertRow currentRow,
                                  InsertRowManager rowManager )
                            throws FeatureStoreException {

        if ( mapping instanceof PrimitiveMapping ) {
            String column = null;
            MappingExpression me = ( (PrimitiveMapping) mapping ).getMapping();
            if ( !( me instanceof DBField ) ) {
                LOG.debug( "Primitive particle is not mapped to a column. Skipping it." );
                return;
            }
            column = ( (DBField) me ).getColumn();
            Object sqlValue = SQLValueMangler.internalToSQL( (PrimitiveValue) particle );
            currentRow.addPreparedArgument( column, sqlValue );
        } else if ( mapping instanceof GeometryMapping ) {
            String column = null;
            MappingExpression me = ( (GeometryMapping) mapping ).getMapping();
            if ( !( me instanceof DBField ) ) {
                LOG.debug( "Geometry particle is not mapped to a column. Skipping it." );
                return;
            }
            column = ( (DBField) me ).getColumn();
            Object sqlValue = null;

            String srid = ( (GeometryMapping) mapping ).getSrid();
            ICRS storageCRS = ( (GeometryMapping) mapping ).getCRS();
            try {
                Geometry value = (Geometry) particle;
                Geometry compatible = fs.getCompatibleGeometry( value, storageCRS );
                sqlValue = WKBWriter.write( compatible );
            } catch ( Exception e ) {
                throw new FeatureStoreException( e.getMessage(), e );
            }
            currentRow.addPreparedArgument( column, sqlValue, fs.getWKBParamTemplate( srid ) );
        } else if ( mapping instanceof CodeMapping ) {
            String column = null;
            MappingExpression me = ( (CodeMapping) mapping ).getMapping();
            if ( !( me instanceof DBField ) ) {
                LOG.debug( "Code particle is not mapped to a column. Skipping it." );
                return;
            }
            column = ( (DBField) me ).getColumn();
            CodeType value = (CodeType) particle;
            currentRow.addPreparedArgument( column, value.getCode() );

            me = ( (CodeMapping) mapping ).getCodeSpaceMapping();
            if ( !( me instanceof DBField ) ) {
                LOG.debug( "Code particle is not mapped to a column. Skipping it." );
                return;
            }
            column = ( (DBField) me ).getColumn();
            currentRow.addPreparedArgument( column, value.getCode() );
        } else if ( mapping instanceof CompoundMapping ) {
            CompoundMapping cm = (CompoundMapping) mapping;
            GenericXMLElement propNode = (GenericXMLElement) particle;
            for ( Mapping particleMapping : cm.getParticles() ) {
                try {
                    insertParticle( particleMapping, propNode, currentRow, rowManager );
                } catch ( FilterEvaluationException e ) {
                    throw new FeatureStoreException( e.getMessage(), e );
                }
            }
        } else {
            LOG.warn( "Updating of " + mapping.getClass() + " is currently not implemented. Omitting." );
        }
    }

    private void insertParticle( Mapping mapping, GenericXMLElement particle, InsertRow insertRow,
                                 InsertRowManager rowManager )
                            throws FilterEvaluationException {

        if ( mapping.getJoinedTable() != null ) {
            // TODO
            QTableName tableName = new QTableName( mapping.getJoinedTable().getFields().get( 1 ).getTable() );
            InsertRow relatedRow = rowManager.newRow( tableName, "id" );
            // TODO foreign key parameters
            rowManager.addForeignKeyRelation( insertRow, "id", relatedRow, "parentfk" );
            insertRow = relatedRow;
        }

        if ( mapping instanceof PrimitiveMapping ) {
            MappingExpression me = ( (PrimitiveMapping) mapping ).getMapping();
            if ( !( me instanceof DBField ) ) {
                LOG.debug( "Particle is not mapped to a column. Skipping it." );
                return;
            }
            PropertyName path = mapping.getPath();
            // TODO: GML version
            FeatureXPathEvaluator evaluator = new FeatureXPathEvaluator( GML_32 );
            TypedObjectNode[] primitiveValues = evaluator.eval( particle, path );
            for ( TypedObjectNode pv : primitiveValues ) {
                if ( pv instanceof PrimitiveValue ) {
                    insertRow.addPreparedArgument( me.toString(), SQLValueMangler.internalToSQL( (PrimitiveValue) pv ) );
                } else if ( pv instanceof GenericXMLElementContent ) {
                    PrimitiveValue textNode = ( (GenericXMLElementContent) pv ).getValue();
                    insertRow.addPreparedArgument( me.toString(), SQLValueMangler.internalToSQL( textNode ) );
                }
            }
        } else if ( mapping instanceof CompoundMapping ) {
            PropertyName path = mapping.getPath();
            // TODO: GML version
            FeatureXPathEvaluator evaluator = new FeatureXPathEvaluator( GML_32 );
            TypedObjectNode[] particleValues = evaluator.eval( particle, path );

            for ( Mapping particleMapping : ( (CompoundMapping) mapping ).getParticles() ) {
                for ( TypedObjectNode particleValue : particleValues ) {
                    if ( particleValue instanceof GenericXMLElement ) {
                        insertParticle( particleMapping, (GenericXMLElement) particleValue, insertRow, rowManager );
                    } else {
                        LOG.warn( "Unexpected particle value type (=" + particleValue.getClass() + ")" );
                    }
                }
            }
        }
    }

    private String generateNewId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Inserts the given feature into BLOB table and returns the generated primary key.
     * 
     * @param stmt
     * @param feature
     * @return primary key of the feature
     * @throws SQLException
     * @throws FeatureStoreException
     */
    private int insertFeatureBlob( PreparedStatement stmt, Feature feature )
                            throws SQLException, FeatureStoreException {

        LOG.debug( "Inserting feature with id '" + feature.getId() + "' (BLOB)" );

        if ( fs.getSchema().getFeatureType( feature.getName() ) == null ) {
            throw new FeatureStoreException( "Cannot insert feature '" + feature.getName()
                                             + "': feature type is not served by this feature store." );
        }
        ICRS crs = blobMapping.getCRS();
        stmt.setString( 1, feature.getId() );
        stmt.setShort( 2, fs.getFtId( feature.getName() ) );

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            BlobCodec codec = fs.getSchema().getBlobMapping().getCodec();
            codec.encode( feature, fs.getNamespaceContext(), bos, crs );
        } catch ( Exception e ) {
            String msg = "Error encoding feature for BLOB: " + e.getMessage();
            LOG.error( msg, e );
            throw new SQLException( msg, e );
        }
        byte[] bytes = bos.toByteArray();
        stmt.setBytes( 3, bytes );
        LOG.debug( "Feature blob size: " + bytes.length );
        Envelope bbox = feature.getEnvelope();
        if ( bbox != null ) {
            try {
                GeometryTransformer bboxTransformer = new GeometryTransformer( crs );
                bbox = (Envelope) bboxTransformer.transform( bbox );
            } catch ( Exception e ) {
                throw new SQLException( e.getMessage(), e );
            }
        }
        stmt.setObject( 4, fs.toPGPolygon( bbox, -1 ) );
        // stmt.addBatch();
        stmt.execute();

        int internalId = -1;

        ResultSet rs = null;
        try {
            // TODO only supported for PostgreSQL >= 8.2
            rs = stmt.getGeneratedKeys();
            rs.next();
            internalId = rs.getInt( 1 );
        } finally {
            if ( rs != null ) {
                rs.close();
            }
        }
        return internalId;
    }

    private void insertFeatureRelational( int internalId, Feature feature, FeatureTypeMapping ftMapping )
                            throws SQLException, FeatureStoreException, FilterEvaluationException {
        InsertRowNode node = new InsertRowNode( ftMapping.getFtTable(), null );
        node.getRow().addPreparedArgument( "id", internalId );
        buildInsertRows( feature, ftMapping, node );
        node.performInsert( conn );
    }

    private void buildInsertRows( Feature feature, FeatureTypeMapping ftMapping, InsertRowNode node )
                            throws FilterEvaluationException, SQLException, FeatureStoreException {

        for ( Property prop : feature.getProperties() ) {
            // TODO use the name of the type or the name of the actual property (substitutions...)
            Mapping propMapping = ftMapping.getMapping( prop.getType().getName() );
            if ( propMapping != null ) {
                buildInsertRows( prop, propMapping, node );
                // if ( propMapping instanceof SimplePropertyMappingType ) {
                // SimplePropertyMappingType simplePropMapping = (SimplePropertyMappingType) propMapping;
                // dbColumn = simplePropMapping.getDBColumn().getName();
                // } else if ( propMapping instanceof GeometryPropertyMappingType ) {
                // GeometryPropertyMappingType geoPropMapping = (GeometryPropertyMappingType) propMapping;
                // dbColumn = geoPropMapping.getGeometryDBColumn().getName();
                // } else {
                // String msg = "Relational mapping of " + propMapping.getClass().getName()
                // + " is not implemented yet.";
                // throw new UnsupportedOperationException( msg );
                // }
                //
                // Object value = prop.getValue();
                //
                // if ( value instanceof Geometry ) {
                // value = store.getCompatibleGeometry( ( (Geometry) value ), store.storageSRS );
                // }
                // LOG.debug( "Property '" + prop.getName() + "', colum: " + dbColumn );
                //
                // columnsToValues.put( dbColumn, value );
            }
        }
    }

    private void buildInsertRows( Property prop, Mapping propMapping, InsertRowNode node )
                            throws FilterEvaluationException, SQLException, FeatureStoreException {

        JoinChain jc = propMapping.getJoinedTable();
        if ( jc != null ) {
            if ( jc.getFields().size() != 2 ) {
                throw new FeatureStoreException( "Handling of joins with " + jc.getFields().size()
                                                 + " steps is not implemented." );
            }
            QTableName tableName = new QTableName( jc.getFields().get( 1 ).getTable() );
            InsertRowNode child = new InsertRowNode( tableName, jc );
            node.getRelatedRows().add( child );

            if ( propMapping instanceof PrimitiveMapping ) {
                MappingExpression me = ( (PrimitiveMapping) propMapping ).getMapping();
                PrimitiveValue primitiveValue = (PrimitiveValue) prop.getValue();
                if ( primitiveValue != null ) {
                    if ( !( me instanceof DBField ) ) {
                        LOG.debug( "Skipping primitive mapping. Not mapped to DBField." );
                    } else {
                        String column = ( (DBField) me ).getColumn();
                        Object sqlValue = SQLValueMangler.internalToSQL( primitiveValue.getValue() );
                        child.getRow().addPreparedArgument( column, sqlValue );
                    }
                }
            } else if ( propMapping instanceof GeometryMapping ) {
                LOG.warn( "TODO geometry mapping" );
            } else if ( propMapping instanceof FeatureMapping ) {
                LOG.warn( "TODO feature mapping" );
            } else if ( propMapping instanceof CompoundMapping ) {
                GenericXMLElement value = null;
                if ( prop.getValue() instanceof GenericXMLElement ) {
                    value = (GenericXMLElement) prop.getValue();
                } else if ( prop.getValue() instanceof GenericXMLElementContent ) {
                    value = new GenericXMLElement( null, (GenericXMLElementContent) prop.getValue() );
                }
                for ( Mapping particle : ( (CompoundMapping) propMapping ).getParticles() ) {
                    buildInsertRows( value, particle, child );
                }
            }

        } else {
            if ( propMapping instanceof PrimitiveMapping ) {
                MappingExpression me = ( (PrimitiveMapping) propMapping ).getMapping();
                PrimitiveValue primitiveValue = (PrimitiveValue) prop.getValue();
                if ( primitiveValue != null ) {
                    if ( !( me instanceof DBField ) ) {
                        LOG.debug( "Skipping primitive mapping. Not mapped to DBField." );
                    } else {
                        String column = ( (DBField) me ).getColumn();
                        Object sqlValue = SQLValueMangler.internalToSQL( primitiveValue.getValue() );
                        node.getRow().addPreparedArgument( column, sqlValue );
                    }
                }
            } else if ( propMapping instanceof GeometryMapping ) {
                LOG.warn( "TODO geometry mapping" );
            } else if ( propMapping instanceof FeatureMapping ) {
                LOG.warn( "TODO feature mapping" );
            } else if ( propMapping instanceof CompoundMapping ) {
                GenericXMLElement value = null;
                if ( prop.getValue() instanceof GenericXMLElement ) {
                    value = (GenericXMLElement) prop.getValue();
                } else if ( prop.getValue() instanceof GenericXMLElementContent ) {
                    value = new GenericXMLElement( null, (GenericXMLElementContent) prop.getValue() );
                }
                for ( Mapping particle : ( (CompoundMapping) propMapping ).getParticles() ) {
                    buildInsertRows( value, particle, node );
                }
            }
        }
    }

    private void buildInsertRows( GenericXMLElement genericXML, Mapping mapping, InsertRowNode row )
                            throws FilterEvaluationException {

        PropertyName path = mapping.getPath();
        // TODO gml version
        FeatureXPathEvaluator evaluator = new FeatureXPathEvaluator( GML_32 );
        TypedObjectNode[] values = evaluator.eval( genericXML, path );

        JoinChain jc = mapping.getJoinedTable();
        if ( jc != null ) {
            LOG.warn( "TODO mapping to related table needs implementing" );
        } else {
            if ( values.length > 1 ) {
                LOG.warn( "Skipping node. Multiple occurrences, but not mapped to related table." );
            } else if ( values.length < 1 ) {
                LOG.warn( "Skipping node (not present)." );
            } else {
                TypedObjectNode node = values[0];
                if ( node instanceof PrimitiveValue ) {
                    PrimitiveValue primitiveValue = (PrimitiveValue) node;
                    MappingExpression me = ( (PrimitiveMapping) mapping ).getMapping();
                    if ( !( me instanceof DBField ) ) {
                        LOG.debug( "Skipping primitive mapping. Not mapped to DBField." );
                    } else {
                        String column = ( (DBField) me ).getColumn();
                        Object sqlValue = SQLValueMangler.internalToSQL( primitiveValue.getValue() );
                        row.getRow().addPreparedArgument( column, sqlValue );
                    }
                } else if ( mapping instanceof GeometryMapping ) {
                    LOG.warn( "TODO geometry mapping" );
                } else if ( mapping instanceof FeatureMapping ) {
                    LOG.warn( "TODO feature mapping" );
                } else if ( mapping instanceof CompoundMapping ) {

                    GenericXMLElement value = null;
                    if ( node instanceof GenericXMLElement ) {
                        value = (GenericXMLElement) node;
                    } else if ( node instanceof GenericXMLElementContent ) {
                        value = new GenericXMLElement( null, (GenericXMLElementContent) node );
                    }

                    for ( Mapping particle : ( (CompoundMapping) mapping ).getParticles() ) {
                        buildInsertRows( value, particle, row );
                    }
                }
            }
        }
    }

    private void findFeaturesAndGeometries( Feature feature, Set<Geometry> geometries, Set<Feature> features,
                                            Set<String> fids, Set<String> gids ) {

        if ( feature instanceof FeatureCollection ) {
            // try to keep document order
            for ( Feature member : (FeatureCollection) feature ) {
                if ( !( member instanceof FeatureReference ) ) {
                    features.add( member );
                }
            }
            for ( Feature member : (FeatureCollection) feature ) {
                findFeaturesAndGeometries( member, geometries, features, fids, gids );
            }
        } else {
            if ( feature.getId() == null || !( fids.contains( feature.getId() ) ) ) {
                features.add( feature );
                if ( feature.getId() != null ) {
                    fids.add( feature.getId() );
                }
            }
            for ( Property property : feature.getProperties() ) {
                Object propertyValue = property.getValue();
                if ( propertyValue instanceof Feature ) {
                    if ( !( propertyValue instanceof FeatureReference ) ) {
                        if ( !features.contains( propertyValue ) ) {
                            findFeaturesAndGeometries( (Feature) propertyValue, geometries, features, fids, gids );
                        }
                    } else if ( ( (FeatureReference) propertyValue ).isResolved()
                                && !( features.contains( ( (FeatureReference) propertyValue ).getReferencedObject() ) ) ) {
                        findFeaturesAndGeometries( ( (FeatureReference) propertyValue ).getReferencedObject(),
                                                   geometries, features, fids, gids );
                    }
                } else if ( propertyValue instanceof Geometry ) {
                    findGeometries( (Geometry) propertyValue, geometries, gids );
                }
            }
        }
    }

    private void findGeometries( Geometry geometry, Set<Geometry> geometries, Set<String> gids ) {
        if ( geometry.getId() == null || !( gids.contains( geometry.getId() ) ) ) {
            geometries.add( geometry );
            if ( geometry.getId() != null ) {
                gids.add( geometry.getId() );
            }
        }
    }

    @Override
    public int performUpdate( QName ftName, List<Property> replacementProps, Filter filter, Lock lock )
                            throws FeatureStoreException {
        LOG.debug( "Updating feature type '" + ftName + "', filter: " + filter + ", replacement properties: "
                   + replacementProps.size() );
        // TODO implement update more efficiently
        IdFilter idFilter = null;
        try {
            if ( filter instanceof IdFilter ) {
                idFilter = (IdFilter) filter;
            } else {
                idFilter = getIdFilter( ftName, (OperatorFilter) filter );
            }
        } catch ( Exception e ) {
            LOG.debug( e.getMessage(), e );
        }
        return performUpdate( ftName, replacementProps, idFilter );
    }

    private int performUpdate( QName ftName, List<Property> replacementProps, IdFilter filter )
                            throws FeatureStoreException {
        int updated = 0;
        if ( blobMapping != null ) {
            throw new FeatureStoreException(
                                             "Updates in PostGISFeatureStore (BLOB mode) are currently not implemented." );
        } else {
            try {
                updated = performUpdateRelational( ftName, replacementProps, filter );
                for ( String id : filter.getMatchingIds() ) {
                    fs.getCache().remove( id );
                }
            } catch ( Exception e ) {
                LOG.debug( e.getMessage(), e );
                throw new FeatureStoreException( e.getMessage(), e );
            }
        }
        return updated;
    }

    private int performUpdateRelational( QName ftName, List<Property> replacementProps, IdFilter filter )
                            throws FeatureStoreException {

        FeatureTypeMapping ftMapping = schema.getFtMapping( ftName );
        FIDMapping fidMapping = ftMapping.getFidMapping();

        List<Object> sqlObjects = new ArrayList<Object>( replacementProps.size() );

        StringBuffer sql = new StringBuffer( "UPDATE " );
        sql.append( ftMapping.getFtTable() );
        sql.append( " SET " );
        boolean first = true;
        for ( Property replacementProp : replacementProps ) {
            QName propName = replacementProp.getType().getName();
            Mapping mapping = ftMapping.getMapping( propName );
            if ( mapping != null ) {
                String column = null;
                if ( mapping instanceof PrimitiveMapping ) {
                    MappingExpression me = ( (PrimitiveMapping) mapping ).getMapping();
                    if ( !( me instanceof DBField ) ) {
                        continue;
                    }
                    column = ( (DBField) me ).getColumn();
                    PrimitiveValue value = (PrimitiveValue) replacementProp.getValue();
                    sqlObjects.add( SQLValueMangler.internalToSQL( value ) );
                    if ( !first ) {
                        sql.append( "," );
                    } else {
                        first = false;
                    }
                    sql.append( column );
                    sql.append( "=?" );
                } else if ( mapping instanceof GeometryMapping ) {
                    MappingExpression me = ( (GeometryMapping) mapping ).getMapping();
                    if ( !( me instanceof DBField ) ) {
                        continue;
                    }
                    column = ( (DBField) me ).getColumn();
                    String srid = ( (GeometryMapping) mapping ).getSrid();
                    ICRS storageCRS = ( (GeometryMapping) mapping ).getCRS();
                    Geometry value = (Geometry) replacementProp.getValue();
                    try {
                        Geometry compatible = fs.getCompatibleGeometry( value, storageCRS );
                        sqlObjects.add( WKBWriter.write( compatible ) );
                    } catch ( Exception e ) {
                        throw new FeatureStoreException( e.getMessage(), e );
                    }
                    if ( !first ) {
                        sql.append( "," );
                    } else {
                        first = false;
                    }
                    sql.append( column );
                    sql.append( "=" );
                    sql.append( fs.getWKBParamTemplate( srid ) );
                } else {
                    LOG.warn( "Updating of " + mapping.getClass() + " is currently not implemented. Omitting." );
                    continue;
                }
            } else {
                LOG.warn( "No mapping for update property '" + propName + "'. Omitting." );
            }
        }
        sql.append( " WHERE " );
        sql.append( fidMapping.getColumn() );
        sql.append( "=?" );

        LOG.debug( "Update: " + sql );

        int updated = 0;
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement( sql.toString() );
            int i = 1;
            for ( Object param : sqlObjects ) {
                stmt.setObject( i++, param );
            }
            for ( String id : filter.getMatchingIds() ) {
                IdAnalysis analysis = schema.analyzeId( id );
                PrimitiveValue value = new PrimitiveValue( analysis.getIdKernel(), fidMapping.getColumnType() );
                Object sqlValue = SQLValueMangler.internalToSQL( value );
                stmt.setObject( i, sqlValue );
                stmt.addBatch();
            }
            int[] updates = stmt.executeBatch();
            for ( int noUpdated : updates ) {
                updated += noUpdated;
            }
        } catch ( SQLException e ) {
            JDBCUtils.log( e, LOG );
            throw new FeatureStoreException( JDBCUtils.getMessage( e ), e );
        } finally {
            JDBCUtils.close( stmt );
        }
        LOG.debug( "Updated" + updated + " features." );
        return updated;
    }

    private IdFilter getIdFilter( QName ftName, OperatorFilter filter )
                            throws FeatureStoreException {
        Set<String> ids = new HashSet<String>();
        Query query = new Query( ftName, filter, -1, -1, -1 );
        FeatureResultSet rs = null;
        try {
            rs = fs.query( query );
            for ( Feature feature : rs ) {
                ids.add( feature.getId() );
            }
        } catch ( FilterEvaluationException e ) {
            throw new FeatureStoreException( e );
        } finally {
            if ( rs != null ) {
                rs.close();
            }
        }
        return new IdFilter( ids );
    }

    private String getAutoIncrementFID( FIDMapping fidMapping, Statement stmt )
                            throws SQLException {
        // TODO check for PostgreSQL >= 8.2 first
        String fid = null;
        ResultSet rs = null;
        try {
            rs = stmt.getGeneratedKeys();
            rs.next();
            Object idKernel = rs.getObject( fidMapping.getColumn() );
            fid = fidMapping.getPrefix() + idKernel;
        } finally {
            if ( rs != null ) {
                rs.close();
            }
        }
        return fid;
    }

    @Override
    public void rollback()
                            throws FeatureStoreException {
        LOG.debug( "Performing rollback of transaction." );
        try {
            conn.rollback();
        } catch ( SQLException e ) {
            LOG.debug( e.getMessage(), e );
            throw new FeatureStoreException( "Unable to rollback SQL transaction: " + e.getMessage() );
        } finally {
            taManager.releaseTransaction( this );
        }
    }
}