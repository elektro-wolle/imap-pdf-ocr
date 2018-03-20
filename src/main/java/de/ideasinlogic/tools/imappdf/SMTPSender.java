package de.ideasinlogic.tools.imappdf;

import com.sun.mail.smtp.SMTPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Properties;

/**
 * send a single PDF file as attachment to the given email-address.
 */
public class SMTPSender {

  private static Logger log = LoggerFactory.getLogger(SMTPSender.class);
  private final Properties prop;
  private final Session session;

  SMTPSender(Session session, Properties prop) {
    this.session = session;
    this.prop = prop;
  }

  /**
   * Send the file to the recipient associated to the given recipient.
   *
   * @param recipient  the recipient of the mail
   * @param scannedPDF PDF file
   * @throws MessagingException if message could not be sent.
   * @throws IOException        if File could not be read.
   */
  void sendMail(InternetAddress recipient, File scannedPDF) throws MessagingException, IOException {
    String ts = MessageFormat.format("{0,date,yyyy.MM.dd-HHmmss}", new Date());

    Transport transport = session.getTransport("smtp");
    transport.connect(prop.getProperty("mail.smtp.user"), prop.getProperty("mail.smtp.pass"));

    // prepare message
    SMTPMessage msg = new SMTPMessage(session);

    // from/to
    InternetAddress fromAddress = new InternetAddress(prop.getProperty("mail.smtp.from"));
    msg.setFrom(fromAddress);
    msg.setSender(fromAddress);
    msg.setEnvelopeFrom(fromAddress.getAddress());
    msg.setRecipient(Message.RecipientType.TO, recipient);

    msg.setSubject(MimeUtility.encodeText("Scanned PDF " + ts, "utf-8", null));
    msg.setSentDate(new Date());

    // encapsulate PDF as mime message
    Multipart multipart = new MimeMultipart();
    MimeBodyPart messageBodyPart = new MimeBodyPart();
    String message = "See attached PDF " + ts + "\n";
    messageBodyPart.setText(message, "utf-8", "text");
    multipart.addBodyPart(messageBodyPart);
    MimeBodyPart attachmentBodyPart = new MimeBodyPart();
    attachmentBodyPart.attachFile(scannedPDF, "application/pdf", null);
    attachmentBodyPart.setFileName("scan-" + ts + ".pdf");
    multipart.addBodyPart(attachmentBodyPart);
    msg.setContent(multipart);

    // send mail
    transport.sendMessage(msg, new Address[]{recipient});
    log.info("forwarded pdf " + attachmentBodyPart.getFileName() + " to " + recipient);

    transport.close();
  }

}
