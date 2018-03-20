package de.ideasinlogic.tools.imappdf;

import com.sun.mail.imap.IMAPBodyPart;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;


/**
 * Read a single mail with attachments, convert the first PDF via ocrmypdf and send to new mail address.
 * Configuration via <code>docker/home/java/conf/config.properties</code>.
 */
public class IMAPMessageReader {
  private static final String MESSAGE_ID = "messageID";
  private static Logger log = LoggerFactory.getLogger(IMAPMessageReader.class);
  private final Properties prop;
  private final OCRWrapper ocr = new OCRWrapper();
  private final SMTPSender smtpSender;

  IMAPMessageReader(Session session, Properties prop) {
    this.prop = prop;
    smtpSender = new SMTPSender(session, prop);
  }

  /**
   * Check, if the message can be processed.
   *
   * @param message an IMAPMessage
   * @return true, if all preconditions are fulfilled
   * @throws MessagingException on message errors
   */
  private boolean shouldHandleMessage(IMAPMessage message) throws MessagingException {
    // ignore deleted messages
    if (message.getFlags().contains(Flags.Flag.DELETED)) {
      log.trace("ignore deleted message");
      return false;
    }
    if (message.getFrom() == null || message.getFrom().length == 0) {
      log.trace("ignore message without sender");
      return false;
    }

    // messages should have at least one recipient
    Address[] recipients = message.getRecipients(Message.RecipientType.TO);
    if (recipients.length == 0) {
      log.debug("ignore message " + message.getMessageNumber() + " without rcpt");
      return false;
    }
    log.info("loaded message " + message.getMessageNumber() + " rcpt=" + recipients[0].toString());

    return true;
  }

  /**
   * Handle a single IMAP message.
   *
   * @param message a message to process.
   * @throws MessagingException   if message could not be read or sent.
   * @throws IOException          if data could not be read
   * @throws InterruptedException if OCR is interrupted
   */
  void handleMessage(IMAPMessage message) throws MessagingException, IOException, InterruptedException {
    try {
      MDC.put(MESSAGE_ID, message.getMessageID());

      // sender, recipient given?
      if (!shouldHandleMessage(message)) {
        message.setFlag(Flags.Flag.DELETED, true);
        return;
      }

      String subject = message.getSubject();

      // try to traverse until first PDF is found
      File inputFile = traverse(message.getContent());
      if (inputFile == null) {
        log.info("no inputFile present, aborting");
        message.setFlag(Flags.Flag.DELETED, true);
        return;
      }

      String lang = prop.getProperty("ocr.lang", "deu+eng");
      // PDF is present, start OCR
      Optional<File> out = ocr.runOCR(inputFile, lang);

      // send to target
      InternetAddress fwd = getTargetRecipient(message);
      if (out.isPresent()) {
        File outFile = out.get();
        smtpSender.sendMail(fwd, outFile, subject);
        //noinspection ResultOfMethodCallIgnored
        outFile.delete();
      } else {
        log.info("Empty inputFile? Using original instead");
        smtpSender.sendMail(fwd, inputFile, subject);
      }
      //noinspection ResultOfMethodCallIgnored
      inputFile.delete();
      message.setFlag(Flags.Flag.DELETED, true);

    } finally {
      MDC.remove(MESSAGE_ID);
    }
  }

  /**
   * Find the recipient for this message: Either the sender or – if configured – a replacement from the config file.
   *
   * @param message an IMAPMessage
   * @return the calculated recipients of the message.
   * @throws MessagingException if IMAP connection is broken.
   */
  private InternetAddress getTargetRecipient(IMAPMessage message) throws MessagingException {
    InternetAddress from = (InternetAddress) message.getFrom()[0];
    Address[] recipients = message.getRecipients(Message.RecipientType.TO);
    // if sender is given -> use it
    InternetAddress fwd = from;
    // if target recipient is in config file
    String address = ((InternetAddress) recipients[0]).getAddress();
    if (prop.containsKey("fwd." + address)) {
      fwd = new InternetAddress(prop.getProperty("fwd." + address));
    }
    log.trace("forward pdf from " + address + " to " + fwd);
    return fwd;
  }


  /**
   * Traverse the mail object.
   *
   * @param content a mime body.
   * @return a File, if PDF found.
   * @throws MessagingException if message could not be opened.
   * @throws IOException        if message could not be read.
   */
  private File traverse(Object content) throws MessagingException, IOException {
    log.trace("got content=" + content.getClass());
    // original mail must be multipart
    if (!(content instanceof MimeMultipart)) {
      return null;
    }
    // check every part.
    MimeMultipart part = (MimeMultipart) content;
    for (int i = 0; i < part.getCount(); i++) {
      BodyPart body = part.getBodyPart(i);
      log.trace("got body part " + body.getClass() + " with content=" + body.getContentType() + " reader=" + body.getContent().getClass());
      // parts should be IMAPBodyPart
      if (!(body instanceof IMAPBodyPart)) {
        continue;
      }
      IMAPBodyPart attachment = (IMAPBodyPart) body;
      if (attachment.getContent() instanceof MimeMultipart) {
        File f = traverse(attachment.getContent());
        if (f != null) {
          return f;
        }
      }
      // attachment should be PDF
      if (!attachment.getContentType().startsWith("application/pdf")) {
        continue;
      }
      log.trace("got pdf=" + attachment.getFileName() + " content=" + attachment.getContent().getClass());
      if (!(attachment.getContent() instanceof InputStream)) {
        continue;
      }
      // save attachment to temporary file
      try (InputStream is = (InputStream) attachment.getContent()) {
        File file = File.createTempFile("scan-", ".pdf");
        try (FileOutputStream fos = new FileOutputStream(file)) {
          byte[] buffer = new byte[32768];
          int len;
          while ((len = is.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
          }
          fos.close();
          log.trace("wrote pdf=" + attachment.getFileName() + " to file " + file);

          return file;
        }
      }
    }
    return null;
  }

}
