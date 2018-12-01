# InboxPager

![screenshot](https://github.com/itprojects/InboxPager/raw/master/img/1.png)

An E-mail client for the Android (java) platform. Supports imap, pop, and smtp protocols, through SSL/TLS.

# Download

You can find [InboxPager](https://f-droid.org/repository/browse/?fdfilter=inbox&fdid=net.inbox.pager) in the free and open source F-droid app store.

# Usage

In order to use this application you have to enable SSL/TLS application-email-checking through your email account's web interface. Some email servers may have this feature already turned on (NOT GMAIL!, see below). If your account's server does not support this feature, the app won't work.

Setting up an account in InboxPager happens through "Settings" > "Add Account". There, you have to enter your account's credentials & server parameters. If you don't know what your email account's server parameters are - use the automatic tools provided to find out, otherwise enter and test your own configuration. Optionally, if you'd like to receive a sound or vibration notification tick those settings. If you wish to only refresh a specific account, when you individually choose to, then untick the "Allow in all-accounts-refresh" in that account's settings.

By default InboxPager DOES NOT KEEP "FULL MESSAGE" copies of emails, instead, it just downloads the main textual contents of the message. If you wish to change this policy you can do this in the settings for an individual account. Keeping full messages can easily consume your device's internal memory very quickly. In order to save email, the full message must first be downloaded into the internal database. Also, unless the full email message is already inside the internal database, downloading attachments will require access to the internet.

# Features

The app can:

- Animate a smooth transition between visual contexts.

- Automatically convert texts from their declared character encoding to UTF-8. 

- Download your full email messages (with attachments inside).

- Download an individual attachment.

- Display server certificates used in the last connection.

- Keep track of your unread messages.

- Notify with sound of new messages, per user choice.

- Notify with device vibration of new messages, per user choice.

- Work with OpenPGP messages.

- Verify hostnames, if not self-signed certificates.

## WON'T FIX

- Automatically, on a cloud server, Save/Restore interrnal database. If a backup of the local device is necessary, then close the app and copy/paste the database from "/data/data/...".

- Backend that runs in the background, as a constantly present service.

- Contacts integration with rest of Android OS.

- Forwarding messages (digests), from inside the app.

- Full IMAP folders. The app is "lite".

- Ordinary non-SSL/TLS. This includes (STARTTLS).

- Printing messages on paper. You can save them to file, and print them manually.

- Automatic inclusion of the message being replied to, in the new message.

# Permissions

InboxPager uses android permissions on the local device for the following reasons:

ACCESS_NETWORK_STATE, INTERNET:

Communication with user defined message servers. Downloading/Uploading messages.

VIBRATE:

Device can be made to vibrate, if new messages have arrived. Users can disable this setting from inside the application.

WRITE_EXTERNAL_STORAGE:

In order to save messages or attachments to the device.

READ_EXTERNAL_STORAGE:

In order to send messages with attachments.

## Requirements

In general - a network connection that does not implement (small) download quotas.

For a POP (Post Office Protocol 3) mail server:

- Support for the extensions TOP, SASL and UIDL.

- LOTS of internal phone memory.

## Known Items

If there are any errors in the application, you should be able to see those in the internal application Event Log. For data leak prevention this log is automatically deleted if the app is closed or crashes. The crashes should be visible in android's log (logcat).

- While downloading a large attachment, if you experience errors, then close some other applications to get more RAM.

- Message and attachment sizes are always only an approximation.

- POP has no way to know if a message has been previously seen.

- Seen messages can not become unseen twice.

- Sent messages are not saved locally inside the app, most servers will save those automatically on the server.

- Device orientation is frozen during sending and downloading, to prevent a crash; restored afterwards.

- IF YOU DOWNLOAD TOO MANY ATTACHMENTS TOO QUICKLY, your server may ban you. Download the full message and then save them.

## Bug Reporting

https://github.com/itprojects/InboxPager/issues

# GMAIL Configuration

To enable application access to your GMail inbox, go to:

https://www.google.com/settings/security/lesssecureapps

Then and only then, in the application:

Username: user.name@gmail.com

Incoming Mail (IMAP) Server: imap.gmail.com

Port: 993

Outgoing Mail (SMTP) Server: smtp.gmail.com

Port for SSL: 465

Sometimes checking GMAIL too ofter (<10 minutes) may or may not block application access to account.

If you are using POP with gmail, make sure to first enable it from gmail's settings web interface via browser.

# OpenPGP Usage

In order to use cryptographic services you need to install an app called OpenKeychain and make, or import, some pgp-keys. OpenKeychain applies OpenPGP privacy/security to a given message,  and the process is described below, or (better) just look at the screenshots. Sending inline messages in non-pgp/mime standard is not supported, but third party apps exist that can do that, through the system clipboard buffer.

IMPORTANT, for BCC messages:

Encrypted blocks may show the recipients' encryption keys' id's. This may leak data, if you're using the blind carbon copy (BCC) message property. For example: a message is encrypted, Alice sends to Bob and Carol; Carol is a BCC, but Carol may also see the key id of Bob.

## Signed Clear Text

Signing a clear, unencrypted message with a pgp key, that will be sent using pgp/mime. This option is for those want to be sure that a message was produced with a certain pgp key, the message contents are not encrypted. Can include attachments.

1. Click the padlock icon, this starts the pgp implementation.

2. Choose "Sign clear text message" spinner.

3. Pick a signing key by pressing on the text button.

4. Ignore recipient keys.

5. Click "START", that produces the pgp/mime.

6. Click "READY", that returns you to the sending activity.

7. Press "SEND".

Some email clients may complain of a "bad signature", i.e. Thunderbird with Enigmail. Manually checking the signature against the firts pgp/mime part with "gpg --verify signature.asc message.txt" is one solution.

## Encrypt Message

Encrypting a message, or signing and encrypting a message. This option is for those that desire more privacy for their content, for example commercial organizations. Messages produced with this option will be encrypted, and they can optionally be signed. [Extra: If one wishes to be able to decrypt their own messages for posterity, use the option to add the signing key to the recipients. Can include attachments.

1. Click the padlock icon, this starts the pgp implementation.

2. Choose "Encrypt" or "Sing and encrypt" from spinner.

3. Pick a signing key by pressing on the text button.

4. Pick the recipient keys by pressing on the text button.

5. Click "START", that produces the pgp/mime.

6. Click "READY", that returns you to the sending activity.

7. Press "SEND".

## Decrypt or Verify Message

Decrypt a pgp/mime message and/or only verifying the signature validity. Can save attachemnts.

1. Click the padlock icon, this starts the pgp implementation.

2. Click "START", that produces the pgp/mime.

3. Click "READY", that returns you to the message activity.

4. Clicking on the icon near the padlock displays signature verification.

5. Clicking on the attachment image button from the top now produces decrypted attachments.

# LICENSES

Application Overall, GPL3

Artwork, CC BY-SA 4.0 License

Font, SIL OPEN FONT LICENSE 1.1

OpenKeychain(Java), Apache 2.0

SQLCipher, Permissive, see file

SQLCipher(Java), Apache 2.0

# Translations

In Portuguese by Hanelore

# Screenshots

<p align="center">
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/17.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/18.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/19.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/2.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/3.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/4.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/5.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/6.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/7.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/8.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/9.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/10.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/11.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/12.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/13.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/14.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/15.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/16.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/cleartext.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/encryption.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/img/verification.png" width="250"/>
</p>
