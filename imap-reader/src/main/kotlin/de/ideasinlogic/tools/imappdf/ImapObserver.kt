package de.ideasinlogic.tools.imappdf

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPMessage
import com.sun.mail.imap.IMAPStore
import com.sun.mail.imap.IdleManager
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent

/**
 * Read IMAP inbox, fetch mail with attachments, convert via ocrmypdf and send to new mail address.
 * Configuration via `docker/home/java/conf/config.properties`.
 */
class ImapObserver private constructor() {
  private val prop: Properties
  private val session: Session
  private val imapMessageReader: IMAPMessageReader
  private val imapListenerExecutor: Executor = Executors.newCachedThreadPool()

  /**
   * Main process, looped from [.main].
   *
   * @throws MessagingException   if message could not be read or sent.
   * @throws IOException          if data could not be read
   * @throws InterruptedException if OCR is interrupted
   */
  @Throws(MessagingException::class, IOException::class, InterruptedException::class)
  private fun run() {
    // open the inbox
    val store = session.getStore("imap") as IMAPStore
    store.connect(prop.getProperty("mail.imap.user"), prop.getProperty("mail.imap.pass"))
    val inbox = store.getFolder("INBOX") as IMAPFolder
    inbox.open(IMAPFolder.READ_WRITE)
    log.debug("opened INBOX with " + inbox.messageCount + " messages")
    // handle all current messages
    for (i in 1..inbox.messageCount) {
      imapMessageReader.handleMessage((inbox.getMessage(i) as IMAPMessage))
    }

    // listen on new messages
    val idleManager = IdleManager(session, imapListenerExecutor)
    inbox.addMessageCountListener(object : MessageCountAdapter() {
      override fun messagesAdded(mce: MessageCountEvent) {
        log.trace("new messages arrived")
        for (m in mce.messages) {
          try {
            imapMessageReader.handleMessage((m as IMAPMessage))
          } catch (e: Exception) {
            log.warn("Failed to handle message: $e", e)
          }
        }
        try {
          // listen on new messages
          idleManager.watch(inbox)
        } catch (e: Exception) {
          log.warn("Failed to restart idle: $e", e)
        }
      }
    })
    idleManager.watch(inbox)
    // wait 30 minutes before reconnect
    Thread.sleep(TimeUnit.MINUTES.toMillis(30))
    idleManager.stop()
    inbox.close(true)
    store.close()
  }

  companion object {
    private val log = LoggerFactory.getLogger(ImapObserver::class.java)

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
      val o = ImapObserver()
      while (true) {
        try {
          o.run()
        } catch (ex: InterruptedException) {
          log.info("Interrupted, exiting")
          System.exit(0)
        }
      }
    }
  }

  init {
    prop = Properties()
    javaClass.classLoader.getResourceAsStream("config.properties").use { fis -> prop.load(fis) }
    prop["mail.event.executor"] = imapListenerExecutor
    session = Session.getInstance(prop)
    imapMessageReader = IMAPMessageReader(session, prop)
  }
}