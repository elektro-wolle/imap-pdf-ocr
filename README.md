# OCR of scanned PDFs via IMAP/SMTP

This small project scans an IMAP folder for new messages and converts every attached PDF via `ocrmypdf`.

For multiple users, the IMAP-Folder may be used with `user+sub`-addresses like:

* scanner+user1@example.org
* scanner+user2@example.org

in the configuration file `/app/resources/config.properties`, these can be used to define new target addresses for the
ocr-ed PDFs:

``` 
fwd.scanner+user1@example.org=real-user1@example.org
fwd.scanner+user2@example.org=real-user2@example.org
```

**WARNING**: This tool deletes **every** mail in the inbox after processing (if possible). So **please** use a dedicated
imap-account for this.

## Running:

The docker image is available via [docker.hub](https://hub.docker.com/r/wjung/imap-pdf-ocr/).

```
docker run --rm \
  -v PATH_TO_LOCAL_CONFIG/config.properties:/app/resources/config.properties \
  wjung/imap-pdf-ocr:latest
```

The optional environment variable `PDF_QUALITY` can be use to rescale the result pdf. (
See https://tex.stackexchange.com/questions/6121/presentation-option-screen-or-prepress-in-ps2pdf#6127). Defaults
to `/ebook`.

If set to `none`, no downsampling will occur.

## Sample config:

```
# see https://javaee.github.io/javamail/docs/api/com/sun/mail/imap/package-summary.html
mail.imap.user=scanner
mail.imap.host=imap.example.org
mail.imap.port=143
mail.imap.ssl.enable=false
mail.imap.starttls.enable=true
mail.imap.starttls.required=true

mail.event.scope=session
mail.imap.usesocketchannels=true
mail.imaps.usesocketchannels=true


# see https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html
mail.smtp.user=scanner
mail.smtp.host=smtp.example.org
mail.smtp.port=587
mail.smtp.from="Scanner" <scanner@example.org>
mail.smtp.auth=true
mail.smtp.ssl.enable=false
mail.smtp.starttls.enable=true
mail.smtp.starttls.required=true

# Passwords
mail.imap.pass=xxx
mail.smtp.pass=xxxx

# Forwards
fwd.default=user@example.org
fwd.scanner+user1@example.org=user1@example.org
fwd.scanner+user2@example.org=user2@example.org

# Language
ocr.lang=deu

# delete messages after processing
deleteAfterProcessing=true
```
