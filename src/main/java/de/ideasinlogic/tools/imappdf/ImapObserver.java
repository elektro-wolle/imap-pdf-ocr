package de.ideasinlogic.tools.imappdf;

import com.sun.mail.imap.IMAPBodyPart;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.IdleManager;
import com.sun.mail.smtp.SMTPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Read IMAP inbox, fetch mail with attachments, convert via ocrmypdf and send to new mail address.
 * Configuration via <code>docker/home/java/conf/config.properties</code>.
 */
public class ImapObserver {
  private final Properties prop;
  private final Session session;
  private Executor imapListenerExecutor = Executors.newCachedThreadPool();
  private Logger log = LoggerFactory.getLogger(this.getClass());

  private ImapObserver() throws IOException {
    prop = new Properties();
    try (InputStream fis = new FileInputStream("conf/config.properties")) {
      prop.load(fis);
    }
    prop.put("mail.event.executor", imapListenerExecutor);
    session = Session.getInstance(prop);
  }

  public static void main(String[] args) throws Exception {
    ImapObserver o = new ImapObserver();
    while (true) {
      o.run();
    }
  }

  /**
   * Main process, looped from {@link #main(String[])}.
   * @throws MessagingException if message could not be read or sent.
   * @throws IOException if data could not be read
   * @throws InterruptedException if OCR is interrupted
   */
  private void run() throws MessagingException, IOException, InterruptedException {
    // open the inbox
    final IMAPStore store = (IMAPStore) session.getStore("imap");
    store.connect(prop.getProperty("mail.imap.user"), prop.getProperty("mail.imap.pass"));
    final IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
    inbox.open(IMAPFolder.READ_WRITE);
    log.debug("opened INBOX with " + inbox.getMessageCount() + " messages");
    // handle all current messages
    for (int i = 1; i <= inbox.getMessageCount(); i++) {
      handleMessage(inbox.getMessage(i));
    }

    // listen on new messages
    final IdleManager idleManager = new IdleManager(session, imapListenerExecutor);
    inbox.addMessageCountListener(new MessageCountAdapter() {
      @Override
      public void messagesAdded(MessageCountEvent mce) {
        log.trace("new messages arrived");
        for (Message m : mce.getMessages()) {
          try {
            handleMessage(m);
          } catch (Exception e) {
            log.warn("Failed to handle message: " + e, e);
          }
        }
        try {
          // listen on new messages
          idleManager.watch(inbox);
        } catch (Exception e) {
          log.warn("Failed to restart idle: " + e, e);
        }
      }
    });
    idleManager.watch(inbox);
    // wait 30 minutes before reconnect
    Thread.sleep(TimeUnit.MINUTES.toMillis(30));
    idleManager.stop();
    inbox.close(true);
    store.close();
  }

  // @SuppressWarnings("ResultOfMethodCallIgnored")

  /**
   * Handle a single IMAP message.
   * @param message a message to process.
   * @throws MessagingException if message could not be read or sent.
   * @throws IOException if data could not be read
   * @throws InterruptedException if OCR is interrupted
   */
  private void handleMessage(Message message) throws MessagingException, IOException, InterruptedException {
    // ignore deleted messages
    if (message.getFlags().contains(Flags.Flag.DELETED)) {
      return;
    }

    // messages should have at least one recipient
    Address[] recipients = message.getRecipients(Message.RecipientType.TO);
    if (recipients.length == 0) {
      log.debug("loaded message " + message.getMessageNumber() + " without rcpt");
      return;
    }
    log.info("loaded message " + message.getMessageNumber() + " rcpt=" + recipients[0].toString());

    // try to traverse until first PDF is found
    File f = traverse(message.getContent());
    if (f == null) {
      log.info("no file present, aborting");
      message.setFlag(Flags.Flag.DELETED, true);
      return;
    }

    // PDF is present, start OCR
    File out = File.createTempFile("scan-", ".pdf");
    ProcessBuilder pb = new ProcessBuilder("ocrmypdf", "-c", "-d", "--remove-background",
        "-l", "deu", f.getAbsolutePath(), out.getAbsolutePath());
    pb.inheritIO();
    Process process = pb.start();
    log.debug("Started OCR process");
    int waitFor = process.waitFor();
    log.debug("finished process " + waitFor + " and created file " + out.getAbsolutePath());

    if (out.length() == 0) {
      log.info("Empty file? use original instead");
      sendMail((InternetAddress) recipients[0], f);
    } else {
      sendMail((InternetAddress) recipients[0], out);
    }
    f.delete();
    out.delete();

    // PDF is sent, original mail could be deleted
    message.setFlag(Flags.Flag.DELETED, true);
  }

  /**
   * Send the file to the recipient associated to the given recipient.
   * @param recipient the recipient of the mail, before replacement from the property file
   * @param out PDF file
   * @throws MessagingException if message could not be sent.
   * @throws IOException if File could not be read.
   */
  private void sendMail(InternetAddress recipient, File out) throws MessagingException, IOException {
    String ts = MessageFormat.format("{0,date,yyyy.MM.dd-HHmmss}", new Date());
    String address = recipient.getAddress();
    String fwd = prop.getProperty("fwd." + address, prop.getProperty("fwd.default"));
    log.trace("forward pdf from " + address + " to " + fwd);


    Transport transport = session.getTransport("smtp");
    transport.connect(prop.getProperty("mail.smtp.user"), prop.getProperty("mail.smtp.pass"));

    // prepare message
    SMTPMessage msg = new SMTPMessage(session);

    // from/to
    InternetAddress fromAddress = new InternetAddress(prop.getProperty("mail.smtp.from"));
    msg.setFrom(fromAddress);
    msg.setSender(fromAddress);
    msg.setEnvelopeFrom(fromAddress.getAddress());
    msg.setRecipient(Message.RecipientType.TO, new InternetAddress(fwd));

    msg.setSubject(MimeUtility.encodeText("Scanned PDF " + ts, "utf-8", null));
    msg.setSentDate(new Date());

    // encapsulate PDF as mime message
    Multipart multipart = new MimeMultipart();
    MimeBodyPart messageBodyPart = new MimeBodyPart();
    String message = "See attached PDF " + ts + "\n";
    messageBodyPart.setText(message, "utf-8", "text");
    multipart.addBodyPart(messageBodyPart);
    MimeBodyPart attachmentBodyPart = new MimeBodyPart();
    attachmentBodyPart.attachFile(out, "application/pdf", null);
    attachmentBodyPart.setFileName("scan-" + ts + ".pdf");
    multipart.addBodyPart(attachmentBodyPart);
    msg.setContent(multipart);

    // send mail
    transport.sendMessage(msg, new Address[]{new InternetAddress(fwd)});
    log.info("forwarded pdf " + attachmentBodyPart.getFileName() + " from " + address + " to " + fwd);

    transport.close();
  }

  /**
   * Traverse the mail object.
   * @param content a mime body
   * @return a File, if PDF found.
   * @throws MessagingException if message could not be opened.
   * @throws IOException if message could not be read.
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
