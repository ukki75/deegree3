//$HeadURL: svn+ssh://mschneider@svn.wald.intevation.org/deegree/base/trunk/resources/eclipse/files_template.xml $
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

package org.deegree.services.wps;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import javax.xml.bind.JAXBElement;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.deegree.commons.config.DeegreeWorkspace;
import org.deegree.commons.config.ResourceManager;
import org.deegree.commons.config.ResourceManagerMetadata;
import org.deegree.commons.config.WorkspaceInitializationException;
import org.deegree.commons.tom.ows.CodeType;
import org.deegree.commons.utils.ProxyUtils;
import org.deegree.commons.xml.stax.StAXParsingHelper;
import org.deegree.process.jaxb.java.ProcessDefinition;
import org.deegree.process.jaxb.java.ProcessletInputDefinition;
import org.deegree.process.jaxb.java.ProcessletOutputDefinition;
import org.deegree.process.jaxb.java.ProcessDefinition.InputParameters;
import org.deegree.process.jaxb.java.ProcessDefinition.OutputParameters;
import org.deegree.services.exception.ServiceInitException;
import org.deegree.services.wps.provider.ProcessProvider;
import org.deegree.services.wps.provider.ProcessProviderProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the available {@link WPSProcess} instances and {@link ProcessProvider}s for the {@link WPService}
 * 
 * @see WPService
 * 
 * @author <a href="mailto:apadberg@uni-bonn.de">Alexander Padberg</a>
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author: schneider $
 * 
 * @version $Revision: $, $Date: $
 */
public class ProcessManager implements ResourceManager {

    private static final Logger LOG = LoggerFactory.getLogger( ProcessManager.class );

    private static ServiceLoader<ProcessProviderProvider> providerLoader = ServiceLoader.load( ProcessProviderProvider.class );

    private static Map<String, ProcessProviderProvider> nsToProvider = null;

    private List<ProcessProvider> providers = new ArrayList<ProcessProvider>();

    public ProcessManager() {
        // for SPI
    }

    /**
     * Creates a new {@link ProcessManager} instance with the given configuration.
     * 
     * @param processesDir
     *            directory to be scanned for process provider configuration documents, never <code>null</code>
     * @throws ServiceInitException
     */
    public ProcessManager( File processesDir, DeegreeWorkspace workspace ) throws ServiceInitException {
        File[] fsConfigFiles = processesDir.listFiles( new FilenameFilter() {
            @Override
            public boolean accept( File dir, String name ) {
                return name.toLowerCase().endsWith( ".xml" );
            }
        } );
        for ( File fsConfigFile : fsConfigFiles ) {
            String fileName = fsConfigFile.getName();
            LOG.info( "Setting up process provider from file '" + fileName + "'..." + "" );
            try {
                ProcessProvider provider = create( fsConfigFile.toURI().toURL(), workspace );
                provider.init( workspace );
                for ( WPSProcess process : provider.getProcesses().values() ) {
                    ProcessDefinition processDefinition = process.getDescription();
                    InputParameters inputParams = processDefinition.getInputParameters();
                    if ( inputParams != null ) {
                        for ( JAXBElement<? extends ProcessletInputDefinition> el : inputParams.getProcessInput() ) {
                            LOG.info( "- input parameter: " + el.getValue().getIdentifier().getValue() );
                        }
                    }

                    if ( processDefinition.getOutputParameters() != null ) {
                        OutputParameters outputParams = processDefinition.getOutputParameters();
                        for ( JAXBElement<? extends ProcessletOutputDefinition> el : outputParams.getProcessOutput() ) {
                            LOG.info( "- output parameter: " + el.getValue().getIdentifier().getValue() );
                        }
                    }
                }

                providers.add( provider );
            } catch ( Exception e ) {
                LOG.error( "Error creating process provider: " + e.getMessage(), e );
            }
        }
    }

    /**
     * Returns all available {@link ProcessProviderProvider} instances.
     * 
     * @return all available providers, keys: config namespace, value: provider instance
     */
    private static synchronized Map<String, ProcessProviderProvider> getProviders() {
        if ( nsToProvider == null ) {
            nsToProvider = new HashMap<String, ProcessProviderProvider>();
            try {
                for ( ProcessProviderProvider provider : providerLoader ) {
                    LOG.info( "Process provider provider: " + provider + ", namespace: "
                              + provider.getConfigNamespace() );
                    if ( nsToProvider.containsKey( provider.getConfigNamespace() ) ) {
                        LOG.error( "Multiple process providers for config namespace: '" + provider.getConfigNamespace()
                                   + "' on classpath -- omitting provider '" + provider.getClass().getName() + "'." );
                        continue;
                    }
                    nsToProvider.put( provider.getConfigNamespace(), provider );
                }
            } catch ( Exception e ) {
                LOG.error( e.getMessage(), e );
            }

        }
        return nsToProvider;
    }

    /**
     * Returns an uninitialized {@link ProcessProvider} instance that's created from the specified process manager
     * configuration document.
     * 
     * @param configURL
     *            URL of the configuration document, must not be <code>null</code>
     * @return corresponding {@link ProcessProvider} instance, not yet initialized, never <code>null</code>
     * @throws ServiceInitException
     */
    public static synchronized ProcessProvider create( URL configURL, DeegreeWorkspace workspace )
                            throws ServiceInitException {

        String namespace = null;
        try {
            XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader( configURL.openStream() );
            StAXParsingHelper.nextElement( xmlReader );
            namespace = xmlReader.getNamespaceURI();
        } catch ( Exception e ) {
            String msg = "Error determining configuration namespace for file '" + configURL + "'.";
            LOG.error( msg );
            throw new ServiceInitException( msg );
        }
        LOG.debug( "Config namespace: '" + namespace + "'" );
        ProcessProviderProvider providerProvider = getProviders().get( namespace );
        if ( providerProvider == null ) {
            String msg = "No process provider for namespace '" + namespace + "' (file: '" + configURL
                         + "') registered. Skipping it.";
            LOG.error( msg );
            throw new ServiceInitException( msg );
        }
        return providerProvider.createProvider( configURL, workspace );
    }

    /**
     * Invoked by the {@link WPService} to clean up the resources associated with {@link WPSProcess} and
     * {@link ProcessProvider} instances.
     */
    void destroy() {
        for ( ProcessProvider manager : providers ) {
            manager.destroy();
        }
    }

    /**
     * Returns all available processes.
     * 
     * @return available process, may be empty, but never <code>null</code>
     */
    public Map<CodeType, WPSProcess> getProcesses() {
        Map<CodeType, WPSProcess> processes = new HashMap<CodeType, WPSProcess>();
        for ( ProcessProvider manager : providers ) {
            Map<CodeType, WPSProcess> managerProcesses = manager.getProcesses();
            if ( managerProcesses != null ) {
                processes.putAll( manager.getProcesses() );
            }
        }
        return processes;
    }

    /**
     * Returns the process with the specified identifier.
     * 
     * @param id
     *            identifier of the process, must not be <code>null</code>
     * @return process with the specified identifier or <code>null</code> if no such process exists
     */
    public WPSProcess getProcess( CodeType id ) {
        WPSProcess process = null;
        for ( ProcessProvider manager : providers ) {
            process = manager.getProcess( id );
            if ( process != null ) {
                break;
            }
        }
        return process;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends ResourceManager>[] getDependencies() {
        return new Class[] { ProxyUtils.class };
    }

    public ResourceManagerMetadata getMetadata() {
        return null;
    }

    public void shutdown() {
        if ( nsToProvider != null ) {
            nsToProvider.clear();
        }
        nsToProvider = null;
        providerLoader = null;
        destroy();
    }

    public void startup( DeegreeWorkspace workspace )
                            throws WorkspaceInitializationException {
        providerLoader = ServiceLoader.load( ProcessProviderProvider.class, workspace.getModuleClassLoader() );
    }
}