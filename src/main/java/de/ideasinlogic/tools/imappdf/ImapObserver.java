package de.ideasinlogic.tools.imappdf;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.IdleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Read IMAP inbox, fetch mail with attachments, convert via ocrmypdf and send to new mail address.
 * Configuration via <code>docker/home/java/conf/config.properties</code>.
 */
public class ImapObserver {
  private static Logger log = LoggerFactory.getLogger(ImapObserver.class);
  private final Properties prop;
  private final Session session;
  private final IMAPMessageReader imapMessageReader;
  private Executor imapListenerExecutor = Executors.newCachedThreadPool();

  private ImapObserver() throws IOException {
    prop = new Properties();
    try (InputStream fis = new FileInputStream("conf/config.properties")) {
      prop.load(fis);
    }
    prop.put("mail.event.executor", imapListenerExecutor);
    session = Session.getInstance(prop);
    imapMessageReader = new IMAPMessageReader(session, prop);
  }

  public static void main(String[] args) throws Exception {
    ImapObserver o = new ImapObserver();
    while (true) {
      try {
        o.run();
      } catch (InterruptedException ex) {
        log.info("Interrupted, exiting");
        System.exit(0);
      }
    }
  }

  /**
   * Main process, looped from {@link #main(String[])}.
   *
   * @throws MessagingException   if message could not be read or sent.
   * @throws IOException          if data could not be read
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
      imapMessageReader.handleMessage((IMAPMessage) inbox.getMessage(i));
    }

    // listen on new messages
    final IdleManager idleManager = new IdleManager(session, imapListenerExecutor);
    inbox.addMessageCountListener(new MessageCountAdapter() {
      @Override
      public void messagesAdded(MessageCountEvent mce) {
        log.trace("new messages arrived");
        for (Message m : mce.getMessages()) {
          try {
            imapMessageReader.handleMessage((IMAPMessage) m);
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

}
