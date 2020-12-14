package de.ideasinlogic.tools.imappdf

import kotlin.Throws
import com.sun.mail.imap.IMAPMessage
import java.io.IOException
import org.slf4j.MDC
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart
import com.sun.mail.imap.IMAPBodyPart
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*
import java.util.function.Consumer
import javax.mail.*

/**
 * Read a single mail with attachments, convert the first PDF via ocrmypdf and send to new mail address.
 * Configuration via `docker/home/java/conf/config.properties`.
 */
class IMAPMessageReader internal constructor(session: Session, private val prop: Properties) {
  private val ocr = OCRWrapper()
  private val smtpSender: SMTPSender
  private val deleteAfterProcessing: Boolean

  /**
   * Check, if the message can be processed.
   *
   * @param message an IMAPMessage
   * @return true, if all preconditions are fulfilled
   * @throws MessagingException on message errors
   */
  @Throws(MessagingException::class)
  private fun shouldHandleMessage(message: IMAPMessage): Boolean {
    // ignore deleted messages
    if (message.flags.contains(Flags.Flag.DELETED)) {
      log.trace("ignore deleted message")
      return false
    }
    if (message.flags.contains(Flags.Flag.SEEN)) {
      // log.trace("ignore seen message");
      return false
    }
    if (message.from == null || message.from.size == 0) {
      log.trace("ignore message without sender")
      return false
    }

    // messages should have at least one recipient
    val recipients = message.getRecipients(Message.RecipientType.TO)
    if (recipients.size == 0) {
      log.debug("ignore message " + message.messageNumber + " without rcpt")
      return false
    }
    log.info("loaded message " + message.messageNumber + " rcpt=" + recipients[0].toString())
    message.setFlag(Flags.Flag.SEEN, true)
    return true
  }

  /**
   * Handle a single IMAP message.
   *
   * @param message a message to process.
   * @throws MessagingException   if message could not be read or sent.
   * @throws IOException          if data could not be read
   */
  @Throws(MessagingException::class, IOException::class)
  fun handleMessage(message: IMAPMessage) {
    try {
      MDC.put(MESSAGE_ID, message.messageID)

      // sender, recipient given?
      if (!shouldHandleMessage(message)) {
        message.setFlag(Flags.Flag.DELETED, deleteAfterProcessing)
        return
      }
      val subject = message.subject

      // try to traverse all PDFs
      val inputFiles = traverse(message.content)
      if (inputFiles.isEmpty()) {
        log.info("no inputFiles present, aborting")
        message.setFlag(Flags.Flag.DELETED, deleteAfterProcessing)
        return
      }
      val lang = prop.getProperty("ocr.lang", "deu+eng")

      // PDF is present, start OCR
      val outFiles = inputFiles
        .map { it.key to ocr.runOCR(it.value, lang) }
        .filter { it.second != null }
        .map { it.first to it.second!! }
        .toMap()

      // send to target
      val fwd = getTargetRecipient(message)
      if (outFiles.isEmpty()) {
        log.info("Empty inputFile? Using original instead")
        smtpSender.sendMail(fwd, inputFiles, subject)
      } else {
        smtpSender.sendMail(fwd, outFiles, subject)
        outFiles.values.forEach(Consumer { obj: File -> obj.delete() })
      }
      inputFiles.values.forEach(Consumer { obj: File -> obj.delete() })
      message.setFlag(Flags.Flag.DELETED, deleteAfterProcessing)
    } finally {
      MDC.remove(MESSAGE_ID)
    }
  }

  /**
   * Find the recipient for this message: Either the sender or – if configured – a replacement from the config file.
   *
   * @param message an IMAPMessage
   * @return the calculated recipients of the message.
   * @throws MessagingException if IMAP connection is broken.
   */
  @Throws(MessagingException::class)
  private fun getTargetRecipient(message: IMAPMessage): InternetAddress {
    val from = message.from[0] as InternetAddress
    val recipients = message.getRecipients(Message.RecipientType.TO)
    // if sender is given -> use it
    var fwd = from
    // if target recipient is in config file
    val address = (recipients[0] as InternetAddress).address
    if (prop.containsKey("fwd.$address")) {
      fwd = InternetAddress(prop.getProperty("fwd.$address"))
    }
    log.trace("forward pdf from $address to $fwd")
    return fwd
  }

  /**
   * Traverse the mail object.
   *
   * @param content a mime body.
   * @return a File, if PDF found.
   * @throws MessagingException if message could not be opened.
   * @throws IOException        if message could not be read.
   */
  @Throws(MessagingException::class, IOException::class)
  private fun traverse(content: Any): Map<String, File> {
    log.trace("got content=" + content.javaClass)
    // original mail must be multipart
    if (content !is MimeMultipart) {
      return emptyMap()
    }
    val fileMap: MutableMap<String, File> = HashMap()
    // check every part.
    val part = content
    for (i in 0 until part.count) {
      val body = part.getBodyPart(i)
      log.trace("got body part " + body.javaClass + " with content=" + body.contentType + " reader=" + body.content.javaClass)
      // parts should be IMAPBodyPart
      if (body !is IMAPBodyPart) {
        continue
      }
      val attachment = body
      val partContent = attachment.content

      if (partContent is MimeMultipart) {
        fileMap.putAll(traverse(partContent))
        continue
      }

      // attachment should be PDF
      if (attachment.fileName == null) {
        continue
      }
      val extension = attachment.fileName.toLowerCase().replace("^.*\\.([a-z]*?)$".toRegex(), "$1")
      if (extension != "pdf" &&
        extension != "png" &&
        extension != "jpg" &&
        extension != "jpeg"
      ) {
        log.debug("unknown extension: $extension")
        continue
      }
      log.trace("got pdf=" + attachment.fileName + " content=" + partContent.javaClass)
      when (partContent) {
        is InputStream -> partContent.use { inputStream ->
          val file = File.createTempFile("scan-", ".pdf")
          FileOutputStream(file).use { fos ->
            inputStream.copyTo(fos)
            log.trace("wrote pdf=" + attachment.fileName + " to file " + file)
            fileMap.put(attachment.fileName, file)
          }
        }
        else -> continue
      }
    }
    return fileMap
  }

  companion object {
    private const val MESSAGE_ID = "messageID"
    private val log = LoggerFactory.getLogger(IMAPMessageReader::class.java)
  }

  init {
    smtpSender = SMTPSender(session, prop)
    deleteAfterProcessing = java.lang.Boolean.valueOf(prop.getProperty("deleteAfterProcessing", "false"))
  }
}