//$HeadURL: svn+ssh://lbuesching@svn.wald.intevation.de/deegree/base/trunk/resources/eclipse/files_template.xml $
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2010 by:
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
package org.deegree.client.mdeditor.config;

import java.util.List;

import junit.framework.TestCase;

import org.deegree.client.mdeditor.model.FormElement;
import org.deegree.client.mdeditor.model.FormGroup;
import org.junit.Test;

/**
 * TODO add class documentation here
 * 
 * @author <a href="mailto:buesching@lat-lon.de">Lyn Buesching</a>
 * @author last edited by: $Author: lyn $
 * 
 * @version $Revision: $, $Date: $
 */
public class FormConfigurationParserTest extends TestCase {

    @Test
    public void testParseFormGroups() {
        Configuration.setFormConfURL( "/home/lyn/workspace/deegree-mdeditor/src/test/resources/org/deegree/client/mdeditor/config/simpleTestConfig.xml" );
        List<FormGroup> formGroups = FormConfigurationParser.getFormGroups();

        assertNotNull( formGroups );
        assertTrue( formGroups.size() == 2 );

        assertEquals( "FormGroup3", formGroups.get( 0 ).getId() );
        assertEquals( "FormGroup", formGroups.get( 1 ).getId() );

        assertEquals( 1, formGroups.get( 0 ).getFormElements().size() );
        assertEquals( 3, formGroups.get( 1 ).getFormElements().size() );

        FormElement formElement = formGroups.get( 1 ).getFormElements().get( 2 );
        assertTrue( formElement instanceof FormGroup );
        assertEquals( 4, ( (FormGroup) formElement ).getFormElements().size() );

    }

}
