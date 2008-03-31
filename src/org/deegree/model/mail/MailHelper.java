//$HeadURL: svn+ssh://developername@svn.wald.intevation.org/deegree/base/trunk/src/org/deegree/framework/mail/MailHelper.java $
/*----------------    FILE HEADER  ------------------------------------------

 This file is part of deegree.
 Copyright (C) 2001-2008 by:
 EXSE, Department of Geography, University of Bonn
 http://www.giub.uni-bonn.de/deegree/
 lat/lon GmbH
 http://www.lat-lon.de

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.

 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 Contact:

 Andreas Poth
 lat/lon GmbH
 Aennchenstr. 19
 53115 Bonn
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
package org.deegree.model.mail;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.deegree.model.i18n.Messages;
import org.deegree.model.logging.ILogger;
import org.deegree.model.logging.LoggerFactory;
import org.deegree.model.util.StringTools;

/**
 * A helper class to create and send mail.
 * 
 * @author <a href="mailto:tfr@users.sourceforge.net">Torsten Friebe </A>
 * @author last edited by: $Author: apoth $
 * 
 * @version $Revision: 10660 $,$Date: 2008-03-24 22:39:54 +0100 (Mo, 24 Mrz 2008) $
 * 
 * @see javax.mail.Message
 * @see javax.mail.internet.MimeMessage
 */

public final class MailHelper {

    private static final ILogger LOG = LoggerFactory.getLogger( MailHelper.class );

    /**
     * Creates a mail helper to send a message.
     * 
     */
    public MailHelper() {
    }

    /**
     * This method creates an email message and sends it using the J2EE mail services
     * 
     * @param eMess
     *            a email message
     * @param mailHost
     *            name of the SMTP host
     * 
     * @throws SendMailException
     *             an exception if the message is undeliverable
     */
    public static void createAndSendMail( MailMessage eMess, String mailHost )
                            throws SendMailException {
        Properties p = System.getProperties();
        p.put( "mail.smtp.host", mailHost );
        new MailHelper().createAndSendMail( eMess, Session.getDefaultInstance( p ) );
    }

    /**
     * This method creates an email message and sends it using the J2EE mail services
     * 
     * @param eMess
     *            a email message
     * @param mailHost
     *            name of the SMTP host
     * @param attachment
     *            Object to attach to a mail
     * @param mimeType
     *            mimetype of the attchment
     * @throws SendMailException
     */
    public static void createAndSendMail( MailMessage eMess, String mailHost, Object[] attachment, String[] mimeType )
                            throws SendMailException {
        Properties p = System.getProperties();
        p.put( "mail.smtp.host", mailHost );
        new MailHelper().createAndSendMail( eMess, Session.getDefaultInstance( p ), attachment, mimeType );
    }

    /**
     * This method creates an email message and sends it using the J2EE mail services
     * 
     * @param eMess
     *            a email message
     * @param mailHost
     *            name of the SMTP host
     * @param files
     *            files to attach to a mail
     * @param mimeType
     *            mimetype of the attchment
     * @throws SendMailException
     */
    public static void createAndSendMail( MailMessage eMess, String mailHost, File[] files, String[] mimeType )
                            throws SendMailException {
        Properties p = System.getProperties();
        p.put( "mail.smtp.host", mailHost );
        new MailHelper().createAndSendMail( eMess, Session.getDefaultInstance( p ), files, mimeType );
    }

    /**
     * This method creates an email message and sends it using the J2EE mail services
     * 
     * @param eMess
     *            a email message
     * @param session
     * 
     * @throws SendMailException
     *             an exception if the message is undeliverable
     * 
     * @see javax.mail.Transport
     * @see <a href="http://java.sun.com/j2ee/tutorial/1_3-fcs/doc/Resources4.html#63060">J2EE Mail
     *      Session connection </a>
     */
    public void createAndSendMail( MailMessage eMess, Session session )
                            throws SendMailException {
        createAndSendMail( eMess, session, null, null );
    }

    /**
     * This method creates an email message and sends it using the J2EE mail services
     * 
     * @param eMess
     *            an email message
     * @param session
     * @param attachment
     *            Object to attach to a mail
     * @param mimeType
     *            mimetype of the attchment
     * @throws SendMailException
     *             an exception if the message is undeliverable
     */
    public void createAndSendMail( MailMessage eMess, Session session, Object[] attachment, String[] mimeType )
                            throws SendMailException {
        if ( eMess == null || !eMess.isValid() ) {
            throw new SendMailException( "Not a valid e-mail!" );
        }
        try {
            int k = eMess.getMessageBody().length() > 60 ? 60 : eMess.getMessageBody().length() - 1;
            LOG.logDebug( StringTools.concat( 500, "Sending message, From: ", eMess.getSender(), "\nTo: ",
                                              eMess.getReceiver(), "\nSubject: ", eMess.getSubject(), "\nContents: ",
                                              eMess.getMessageBody().substring( 0, k ), "..." ) );
            // construct the message
            MimeMessage msg = new MimeMessage( session );
            msg.setFrom( new InternetAddress( eMess.getSender() ) );
            msg.setRecipients( Message.RecipientType.TO, InternetAddress.parse( eMess.getReceiver(), false ) );
            msg.setSubject( eMess.getSubject(), Charset.defaultCharset().name() );
            msg.setHeader( "X-Mailer", "JavaMail" );
            msg.setSentDate( new Date() );

            // create and fill the first message part
            List<MimeBodyPart> mbps = new ArrayList<MimeBodyPart>();
            MimeBodyPart mbp = new MimeBodyPart();
            mbp.setContent( eMess.getMessageBody(), eMess.getMimeType() );
            mbps.add( mbp );
            if ( attachment != null ) {
                if ( attachment.length != mimeType.length ) {
                    throw new SendMailException( Messages.getMessage( "MAIL_ATTACH" ) );
                }
                for ( int i = 0; i < attachment.length; i++ ) {
                    mbp = new MimeBodyPart();
                    mbp.setContent( attachment[i], mimeType[i] );
                    mbp.setFileName( "file" + i );
                    mbps.add( mbp );
                }
            }
            // create the Multipart and add its parts to it
            Multipart mp = new MimeMultipart();
            for ( int i = 0; i < mbps.size(); i++ ) {
                mp.addBodyPart( mbps.get( i ) );
            }
            msg.setContent( mp );
            // send the mail off
            Transport.send( msg );
            LOG.logDebug( "Mail sent successfully! Header=", eMess.getHeader() );
        } catch ( Exception e ) {
            LOG.logError( e.getMessage(), e );
            String s = Messages.getMessage( "MAIL_SEND_ERROR", eMess.getHeader() );
            throw new SendMailException( s, e );
        }
    }

    /**
     * This method creates an email message and sends it using the J2EE mail services
     * 
     * @param eMess
     *            an email message
     * @param session
     * @param files
     *            files to attach to a mail
     * @param mimeType
     *            mimetype of the attchment
     * @throws SendMailException
     *             an exception if the message is undeliverable
     */
    public void createAndSendMail( MailMessage eMess, Session session, File[] files, String[] mimeType )
                            throws SendMailException {
        if ( eMess == null || !eMess.isValid() ) {
            throw new SendMailException( "Not a valid e-mail!" );
        }
        try {
            int k = eMess.getMessageBody().length() > 60 ? 60 : eMess.getMessageBody().length() - 1;
            LOG.logDebug( StringTools.concat( 500, "Sending message, From: ", eMess.getSender(), "\nTo: ",
                                              eMess.getReceiver(), "\nSubject: ", eMess.getSubject(), "\nContents: ",
                                              eMess.getMessageBody().substring( 0, k ), "..." ) );
            // construct the message
            MimeMessage msg = new MimeMessage( session );
            msg.setFrom( new InternetAddress( eMess.getSender() ) );
            msg.setRecipients( Message.RecipientType.TO, InternetAddress.parse( eMess.getReceiver(), false ) );
            msg.setSubject( eMess.getSubject(), Charset.defaultCharset().name() );
            msg.setHeader( "X-Mailer", "JavaMail" );
            msg.setSentDate( new Date() );

            // create and fill the first message part
            List<MimeBodyPart> mbps = new ArrayList<MimeBodyPart>();
            MimeBodyPart mbp = new MimeBodyPart();
            mbp.setContent( eMess.getMessageBody(), eMess.getMimeType() );
            mbps.add( mbp );
            if ( files != null ) {
                if ( files.length != mimeType.length ) {
                    throw new SendMailException( Messages.getMessage( "MAIL_ATTACH" ) );
                }
                for ( int i = 0; i < files.length; i++ ) {
                    mbp = new MimeBodyPart();
                    mbp.attachFile( files[i] );
                    mbp.setHeader( "Content-Type", mimeType[i] );
                    mbps.add( mbp );
                }
            }
            // create the Multipart and add its parts to it
            Multipart mp = new MimeMultipart();
            for ( int i = 0; i < mbps.size(); i++ ) {
                mp.addBodyPart( mbps.get( i ) );
            }
            msg.setContent( mp );
            // send the mail off
            Transport.send( msg );
            LOG.logDebug( "Mail sent successfully! Header=", eMess.getHeader() );
        } catch ( Exception e ) {
            LOG.logError( e.getMessage(), e );
            String s = Messages.getMessage( "MAIL_SEND_ERROR", eMess.getHeader() );
            throw new SendMailException( s, e );
        }
    }
}
