package de.ideasinlogic.tools.imappdf

import com.sun.mail.smtp.SMTPMessage
import jakarta.activation.CommandMap
import jakarta.activation.MailcapCommandMap
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.text.MessageFormat
import java.util.*


/**
 * send a single PDF file as attachment to the given email-address.
 */
class SMTPSender internal constructor(private val session: Session, private val prop: Properties) {
	init {
		val mc: MailcapCommandMap = CommandMap.getDefaultCommandMap() as MailcapCommandMap
		mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html")
		mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml")
		mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
		mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed")
		mc.addMailcap("message/rfc822;; x-java-content- handler=com.sun.mail.handlers.message_rfc822")
	}

	/**
	 * Send the file to the recipient associated to the given recipient.
	 *
	 * @param recipient   the recipient of the mail
	 * @param scannedPDFs PDF files
	 * @param subject     Subject of the mail.
	 * @throws MessagingException if message could not be sent.
	 * @throws IOException        if File could not be read.
	 */
	@Throws(MessagingException::class, IOException::class)
	fun sendMail(recipient: InternetAddress, scannedPDFs: Map<String, File>, subject: String) {
		val tsFile = MessageFormat.format("{0,date,yyyy.MM.dd-HHmmss}", Date())
		val tsHeader = MessageFormat.format("[OCR {0,date,yyyy-MM-dd HH:mm:ss}]", Date())
		val transport = session.getTransport("smtp")
		transport.connect(prop.getProperty("mail.smtp.user"), prop.getProperty("mail.smtp.pass"))

		// prepare message
		val msg = SMTPMessage(session)

		// from/to
		val fromAddress = InternetAddress(prop.getProperty("mail.smtp.from"))
		msg.setFrom(fromAddress)
		msg.sender = fromAddress
		msg.envelopeFrom = fromAddress.address
		msg.setRecipient(Message.RecipientType.TO, recipient)
		msg.subject = MimeUtility.encodeText("$tsHeader $subject", "utf-8", null)
		msg.sentDate = Date()

		// encapsulate PDF as mime message
		val multipart: Multipart = MimeMultipart()
		val messageBodyPart = MimeBodyPart()
		val message = "See attached PDFs $tsFile\n"
		messageBodyPart.setText(message, "utf-8", "text")
		multipart.addBodyPart(messageBodyPart)
		scannedPDFs.forEach { (key: String, value: File) ->
			try {
				val attachmentBodyPart = MimeBodyPart()
				attachmentBodyPart.attachFile(value, "application/pdf", null)
				attachmentBodyPart.fileName = "scan-$tsFile-$key"
				multipart.addBodyPart(attachmentBodyPart)
			} catch (e: MessagingException) {
				log.warn("Can't add file: $value")
			} catch (e: IOException) {
				log.warn("Can't add file: $value")
			}
		}
		msg.setContent(multipart)

		// send mail
		transport.sendMessage(msg, arrayOf<Address>(recipient))
		log.info { "forwarded pdf with attachments: " + scannedPDFs.keys + " to " + recipient }
		transport.close()
	}

	companion object {
		private val log = KotlinLogging.logger { }
	}
}