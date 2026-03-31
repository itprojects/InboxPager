# InboxPager

An E-mail client for the Android (Java) platform. Supports IMAP, POP, and SMTP protocols through SSL/TLS.

![screenshot](https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/9.png)

# Download

You can download InboxPager from the free and open-source F-Droid app store.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/net.inbox.pager/)

# Usage

In order to use this application, you have to enable SSL/TLS application-e-mail-checking through your e-mail account's web interface. Some e-mail servers may have this feature already turned on (NOT GMail!, see below). If your account's server does not support this feature, this app won't work.

You can set up an account in Inbox Pager by going to `Settings > Add Account`. There you have to enter your account's credentials and server parameters. If you don't know what your e-mail account's server parameters are, use the automatic tools provided to find out, or enter and test your own configuration. Optionally, if you'd like to receive a textual, sound or vibration notification, tick those settings. If you wish to manually refresh a specific account, untick the "Allow in all-accounts-refresh" in the account's settings.

Inbox Pager DOES **NOT** KEEP FULL MESSAGE copies of e-mails, by default. It just downloads the main textual contents of the message. If you wish to change this policy, you can do this in the settings for an individual account. **Keeping full messages can consume your device's internal memory very quickly.** In order to save an e-mail or it's attachment, the full message is first downloaded into (RAM) memory, and then saved to file. The downloaded message won't remain, unless policy is set in account settings, or if downloaded through `Download ➠ Database`. If the full message is already in database, then no internet is necessary to save either message or attachments. If an e-mail message has many attachments, it is better to download it to internal database, and then save them.

# Features

The app can:

- Animate a smooth transition between visual contexts.

- Automatically convert texts from their declared character encoding to UTF-8. 

- Download your full e-mail messages (with attachments inside).

- Download an individual attachment.

- Display server certificates used in the last connection.

- Keep track of your unread messages.

- Notify of new messages by text and sound (per user choice).

- Notify of new messages by vibrating device (per user choice).

- Work with OpenPGP messages.

- Emergency wipe password.

- Verify hostnames.

# Permissions

Inbox Pager uses Android permissions on the local device for the following reasons.

**ACCESS_NETWORK_STATE, INTERNET**

Communication with user-defined message servers. Downloading/Uploading messages.

**FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC**

Keeps application alive in the foreground, to prevent loss of network connection, when communicating with user-defined servers.

**MANAGE_EXTERNAL_STORAGE**

The application may, or may not, work without this permission, but cannot save e-mails and cannot save (or send) attachments. The user must manually give permission in system settings, because it is not asked for. This permission is the new way to deliver the **READ** and **WRITE** device permissions.

**WRITE_EXTERNAL_STORAGE**

To save messages, or attachments, to the device.

**READ_EXTERNAL_STORAGE**

To send messages with attachments.

**POST_NOTIFICATIONS**

To show notifications. Can be disabled, see internal (in-application) and external (system) application settings.

**VIBRATE**

Device can be made to vibrate, if new messages have arrived. Users can disable this setting from inside the application.

## Requirements

In general - a network connection that does not implement (small) download quotas.

If using a POP (Post Office Protocol 3) e-mail server:

- Support for the extensions TOP, SASL, and UIDL.

- LOTS of internal phone memory.

## Known Issues

If there are any errors in the application, you should be able to see those in the internal application `Errors / Exceptions` event log. For data leak prevention, this log is automatically deleted if the app is closed or crashes. The crashes should be visible in Android's `logcat`.

- If you experience errors while downloading a large attachment, close some other applications to get more RAM.

- Message and attachment sizes are always only an approximation.

- POP has no way to know if a message has been previously seen.

- Seen messages can not become unseen twice.

- Sent messages are not saved locally inside the app; most servers will save those automatically on the server.

- Device orientation is frozen during sending and downloading to prevent crashing. This is restored when finished.

- IF YOU DOWNLOAD TOO MANY ATTACHMENTS TOO QUICKLY, your **server may ban you**. Download the full message to database first, and then save them afterwards. You may wish to remove the full message from database after that to save memory. 

- Message dates (>=v5.1) are reformatted and converted to the LOCAL DEVICE TIME-ZONE. Press DATE, to see converted datetime header in a widget. Press the text of the date, to see the original raw datetime.

## WON'T FIX ISSUES

Inbox Pager:

- Does not allow backups and of internal database. If a backup of the local device is really necessary, close the app and copy/paste database and xml setting files from "/data/data/..." with root.

- Does not integrate with the rest of the Android OS.

- Does not forward messages (digests) from inside the app.

- Does not include the message being replied to in the new message, but the text may be visible for reference.

- Does not support all IMAP folder operations. The app is "lite".

- Does not support insecure (non-SSL/TLS) servers.

- Does not print messages on paper. You can save them to file and print them manually.

## Bug Reporting

https://github.com/itprojects/InboxPager/issues

# GMail Configuration (PLAIN)

To use a GMail inbox with PLAIN (simple password) authentication, you need to set an App Password.

1. Login to GMail.
2. Enable IMAP and SMTP access.
3. Enable App Passwords for you account.

    `GMail > Security > App Passwords`

4. [IMPORTANT STEP] Press Select app > Other (Custom name)
5. Give it a name. Done.
6. Enter the letters (without spaces!) as password.

App Password:

- ONLY 16 symbols.
- CHANGE regularly, IF you WANT any SECURITY at all!
- REVOKED (or disabled) when you CHANGE you GMail PASSWORD.

	Set it again in GMail > Security > App Passwords.

The other GMail account settings are: 

	Username: user.name@gmail.com

	Incoming Mail (IMAP) Server: imap.gmail.com

	Port: 993

	Outgoing Mail (SMTP) Server: smtp.gmail.com

	Port for SSL: 465

Sometimes checking GMail too often (<10 minutes) can cause blocked application access to an account.

If you are using POP with GMail, make sure to enable it from GMail's settings web interface via browser first.

After you have created an app password GMail will send you a "Mail Delivery Reports" message. If that message gives you the option to confirm that you created the app password, you must do so. Otherwise your e-mails will be blocked.

# GMail Configuration (OAUTH2)

⚠️ **<span style='color: red;'>This tutorial is long and difficult, READ IT ALL before trying.</span>**

⚠️ **Not all servers can use OAUTH2 with Inbox Pager**

⚠️ **Due to policy, Inbox Pager will never provide you with Client ID or Client Secret!**

To use a GMail inbox with OAUTH2 (modern) authentication, you need to configure OAUTH2.

The user must provide the necessary parameters to Inbox Pager, so that an application authorisation procedure is created and completed, on that server.

Use the tools provided (Try Incoming/Try Outgoing) to test the server. **When tested, the server must show XOAUTH2, or it isn't possible to use OAuth2.**

There are two ways of using Inbox Pager with OAuth2.

The **first way** is to **create an authorisation for Inbox Pager** in the e-mail server, which will uniquely identify Inbox Pager to the server. The **second** is to **use the authorisation of another application**, by using the publicly known parameters and one other secret parameter. In the **second case**, the user will **operate two applications**, for example, **one** on **mobile** device and **one** on **desktop** computer. 

Make sure that the browser you choose does not interpret opening an URL address as a search action. Fennec (Mozilla Firefox) browser, is the tested and recommended browser, known to work.

## Creating an Authorisation

**It is advised to enable two-factor authentication (2FA) for the account, in case there are problems with the registration process.**

- This step requires an Android device that can freely access the internet.

- The internet access must not be used with technology that changes the IP address between page refresh operations, because this will fail the operation.

- The IP address of the device must not be behind a firewall.

### Procedure

This step requires an **Android device** that can **freely access the internet**. The **IP** address of the device must **not be behind a firewall**. The **internet** access **must not** be used with technology that **changes the IP address** between page refresh operations, because this will fail the operation. It is very important with two-factor authentication (**2FA**) enabled for the account, in case there are problems with the registration process.

*Required parameters*

- **Client ID**, identifies the application, connecting to the e-mail server.

- **Client Secret**, verifies the application, connecting to the e-mail server.

- **Authorisation Endpoint (URL)**, URL on the e-mail server that produces refresh tokens, after authorisation. Refresh tokens can be exchanges for access tokens.

- **Token Endpoint (URL)**, URL on the e-mail server that produces access tokens by using refresh tokens.

- **Scopes (Permission URL's)**, URL's that limit the user's activity on the server.

1. Consult your server's documentation web page for OAuth2 configuration and necessary parameters.

	1. Client ID, Client Secret. Most server will let you create your own. Different servers have different creation procedures. You can use publicly known ones.

		For example, Mozilla Thunderbird has GMail parameters

		Client ID, `406964657835-aq8lmia8j95dhl1a2bvharmfk3t1hgqj.apps.googleusercontent.com`

		Client Secret, `kSmqreRr0qwBWJgbf5Y-PjSU`

		Taken from the `OAuth2Providers.sys.mjs` file, listing known server configurations.

	2. Look for a URL used for authorisation (codes).
	
		For GMail, `https://accounts.google.com/o/oauth2/auth`

	3. Look for a URL used for tokens.
	
		For GMail: `https://oauth2.googleapis.com/token`

	4. Look for URLs used for scopes.
	
		For GMail, `https://mail.gmail.com/`

		If more than one,  then separate with one empty space between URL's:

		`https://www.googleapis.com/auth/carddav https://mail.google.com/ https://www.googleapis.com/auth/calendar`

2. Place the parameters into their respective places in the `Device` OAuth2 section.

	Press on the `?` icon buttons for more information about each field.

<p align="center">
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/oauth2_1.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/oauth2_2.png" width="250"/>
</p>

3. Open a web browser, running on the same device.

4. Log into your e-mail account from the browser.

5. Return to Inbox Pager, **without closing the browser**, going to "Remote" section of OAuth2 configuraiton for the same account.

6. Click on button **Request Authorisation**. This will create a textual URL string. Inbox Pager will start a temporary server, that will wait for a response form the e-mail server (as client), to complete a PKCE (S256) authentication. Copy that URL to clipboard.

<p align="center">
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/oauth2_3.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/oauth2_3.png" width="250"/>
</p>

7. Go to **the browser again**, open a new tab with the copied URL for address. Load it and wait a few seconds to see the page. This page is the authorisation consent screen, where you are expected give your permission, for the Inbox Pager, to use your account.

<p align="center">
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/oauth2_5.png" width="250"/>
</p>

8. Once you give permission, the e-mail server sends back a response with private cryptographic information, which Inbox Pager captures, saves, and uses for access to your e-mail account.

9. If the procedure was successful, or failed, there should be a pop-up with an explanation. If there is an error you need to fix the errors. If there are no errors, then return to the account preferences.

10. Make sure that the authentication method is `XOAUHT2` in the account preferences. Click on button `Save`.

11. Set the server settings, if you have not done so already.

	For GMail,

	IMAP, imap.gmail.com, 993

	SMTP, smtp.gmail.com, 465

12. Return to main account window (activity) in Inbox Pager. Try to refresh your e-mail account. If the procedure was successful, you may see new messages. Some server, such as GMail, will send you a `Security Alert` e-mail message. You should log-in through a browser and confirm in the (html) e-mail message, that it was you, who initiated the activities.

<p align="center">
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/oauth2_6.png" width="250"/>
</p>

13. In the browser you should be able to see a new application in the 3rd-party account authorisations.

<p align="center">
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/oauth2_7.png" width="250"/>
</p>

14. Log out of your e-mail account from the browser. Delete browser history, because the browser may store important secret cryptographic information, that must not be shared!


## Using Shared Authorisation

If you know values of the parameters **Refresh Token, Client ID, Client Secret**, and **Scope URLs**, you do not need to re-authorise Inbox Pager when you move from one device to another. The authorisation is not deleted from the server, if Inbox Pager is deleted on the device. Inbox Pager can also be used with an existing authorisation from another application, if those parameters are provided. In that case just copy/paste and then refresh the inbox. 


# Cryptography / Privacy / Security

## Text Block Encryption

Parts of the plain text of the message you send and/or receive can be encrypted/decrypted with either AES or Twofish cryptography. Just select the text you want to encrypt or decrypt, and choose encrypt or decrypt padlock icon from the context menu. This WILL NOT ENCRYPT your message attachments. [For attachments encryption use OpenPGP.] Encrypting the texts with AES or Twofish makes it easy to send simple text message e-mails with minimal setup. All you need is a passphrase (a key), that both the sender and receiver already know. Although it should be technically possible to encrypt with an empty passphrase, it's a bad practice. The passphrase must be no longer than 32 symbols (=256bits). If the passphrase length is less than 32, then extra symbols (called padding) are automatically added. You can choose the type of padding to be PKCS7Padding or TBC. Additionally, you can also select the type of block encryption mode - CBC/CTR/ECB/ICM/OFB. The best privacy and security is when you encrypt your text with block encryption, and then also with OpenPGP.

The procedure for block text encryption/decryption is simple. Select some text, a context menu appears, press encrypt/decrypt as applicable. See images below.

## OpenPGP Usage

In order to use the OpenPGP cryptographic services, you need to install an app called OpenKeychain and make, or import, some pgp-keys. OpenKeychain applies OpenPGP privacy/security to a given message, and the process is described below, or (better) just look at the screenshots. Sending inline messages in non-pgp/mime standard is not supported, but third party apps exist that can do that through the system clipboard buffer.

IMPORTANT, for BCC messages:

Encrypted blocks may show the ids of the recipients' encryption keys. This may leak data if you're using the blind carbon copy (BCC) message property. For example: a message is encrypted, Alice sends to Bob and Carol; Carol is a BCC, but Carol may also see the key id of Bob.

### Signed Clear Text

Signing a clear, unencrypted message with a PGP key that will be sent using pgp/mime. This option is for those who want to be sure that a message was produced with a certain PGP key, but the message contents are not encrypted. It can include attachments.

1. Click the padlock icon, this starts the PGP implementation.

2. Choose `Sign clear text message` spinner.

3. Pick a signing key by pressing on the text button.

4. Ignore recipient keys.

5. Click `GPG`; that produces the pgp/mime.

6. Click `READY`; that returns you to the sending activity.

7. Press `SEND`.

Some e-mail clients may complain of a "bad signature" (i.e. Thunderbird with Enigmail). Manually checking the signature against the firts pgp/mime part with "gpg --verify signature.asc message.txt" is one solution.

### Encrypt Message

Encrypting a message, or signing and encrypting a message. This option is for those that desire more privacy for their content (for example: commercial organizations). Messages produced with this option will be encrypted, and they can optionally be signed. [Extra: If one wishes to be able to decrypt their own messages for posterity, use the option to add the signing key to the recipients. Can include attachments.]

1. Click the padlock icon; this starts the pgp implementation.

2. Choose `Encrypt` or `Sing and encrypt` from spinner.

3. Pick a signing key by pressing on the text button.

4. Pick the recipient keys by pressing on the text button.

5. Click `GPG`, that produces the pgp/mime.

6. Click `READY`, that returns you to the sending activity.

7. Press `SEND`.

### Decrypt or Verify Message

Decrypt a pgp/mime message and/or only verifying the signature validity. Can save attachments.

1. Click the padlock icon, this starts the pgp implementation.

2. Click `GPG`, that produces the pgp/mime.

3. Click `READY`, that returns you to the message activity.

4. Clicking on the icon near the padlock displays signature verification.

5. Clicking on the attachment image button from the top now produces decrypted attachments.

# LICENSES

Application Overall, GPL3

Artwork, CC BY-SA 4.0 License

Font, SIL OPEN FONT LICENSE 1.1

OpenKeychain(Java), Apache 2.0

SQLCipher, Permissive, see file

SQLCipher(Java), Apache 2.0

Apache Commons, Apache 2.0

Org.Json, Public Domain

Gnu Crypto, GPL2

# Translations

In Portuguese initially by Hanelore.

# Screenshots

<p align="center">
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/11.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/14.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/15.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/block_text_encryption.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/cleartext.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/encryption.png" width="250"/>
  <img src="https://github.com/itprojects/InboxPager/raw/master/fastlane/metadata/android/en-US/images/phoneScreenshots/verification.png" width="250"/>
</p>
