# OCR of scanned PDFs via IMAP/SMTP

This small project scans an IMAP folder for new messages and converts
every attached PDF via `ocrmypdf`.

For multiple users, the IMAP-Folder may be used with `user+sub`-addresses like:

* scanner+user1@example.org
* scanner+user2@example.org

in the configuration file `conf/config.properties`, these can be used to define new 
target addresses for the ocr-ed PDFs:

```properties 
fwd.default=another-user@example.org
fwd.scanner+user1@example.org=real-user1@example.org
fwd.scanner+user2@example.org=real-user2@example.org
```