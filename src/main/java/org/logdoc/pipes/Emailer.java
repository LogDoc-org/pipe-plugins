package org.logdoc.pipes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.mail.smtp.SMTPMessage;
import com.typesafe.config.Config;
import org.logdoc.sdk.PipePlugin;
import org.logdoc.sdk.WatchdogFire;
import org.logdoc.structs.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.net.ssl.SSLSocketFactory;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.getBoolean;
import static org.logdoc.helpers.Texts.notNull;

/**
 * SMTP sender
 * Hoster is responsible of providing valid configuration
 */
public class Emailer implements PipePlugin {
    private static final Logger logger = LoggerFactory.getLogger(Emailer.class);
    private final ObjectMapper objectMapper = new JsonMapper();

    private static final String RCPT_NAME = "emailRecipients", BODY_NAME = "emailBody", SUBJ_NAME = "emailSubject", ATTC_NAME = "emailReport";

    private final Properties props;
    private InternetAddress addressFrom;
    private String smtpUser, smtpPassword, subject, body;
    private boolean auth;

    public Emailer() {
        props = new Properties();
        subject = "";
        body = "";
    }

    @Override
    public boolean configure(final Config cfg) throws Exception {
        if (cfg == null || cfg.isEmpty()) return false;

        if (!cfg.hasPath("smtp")) throw new IllegalStateException("No smtp config provided");

        final Config smtpConf = cfg.getConfig("smtp");
        if (!smtpConf.hasPath("host") || !smtpConf.hasPath("port")) throw new IllegalStateException("No smtp host config provided");

        props.put("mail.smtp.host", smtpConf.getString("host"));
        props.put("mail.smtp.port", smtpConf.getString("port"));

        int timeout = 3000;
        if (smtpConf.hasPath("timeout")) timeout = smtpConf.getInt("timeout");

        props.put("mail.smtp.timeout", String.valueOf(timeout));
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeout));

        if (smtpConf.hasPath("ssl_enabled") && smtpConf.getBoolean("ssl_enabled")) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.fallback", "false");
            props.put("mail.smtps.timeout", String.valueOf(timeout));
            props.put("mail.smtps.connectiontimeout", String.valueOf(timeout));
            props.put("mail.smtp.socketFactory.port", smtpConf.getString("port"));
            props.put("mail.smtp.ssl.trust", smtpConf.getString("host"));
            props.put("mail.smtps.ssl.trust", smtpConf.getString("host"));

            String factory = SSLSocketFactory.class.getName();

            if (smtpConf.hasPath("ssl.factory")) factory = smtpConf.getString("ssl.factory");

            props.put("mail.smtp.socketFactory.class", factory);

        } else props.put("mail.smtp.ssl.enable", "true");

        if (smtpConf.hasPath("ssl.tls_enabled") && smtpConf.getBoolean("ssl.tls_enabled")) {
            props.put("mail.smtp.starttls.enable", "true");

            String protocols = "TLSv1.2";

            if (smtpConf.hasPath("ssl.tls.protocols")) protocols = smtpConf.getString("ssl.tls.protocols");

            props.put("mail.smtp.ssl.protocols", protocols);
        } else props.put("mail.smtp.starttls.enable", "false");


        final Config authConf = smtpConf.hasPath("auth") ? smtpConf.getConfig("auth") : null;

        if ((auth = (authConf != null))) {
            smtpUser = authConf.getString("user");
            smtpPassword = authConf.hasPath("password") ? authConf.getString("password") : "";

            props.put("mail.smtp.auth", "true");
        }


        subject = cfg.hasPath("default_subject") ? cfg.getString("default_subject") : "Logdoc notification";
        body = cfg.hasPath("default_body") ? cfg.getString("default_body") : "Logdoc notification";

        if (!cfg.hasPath("sender.email") || !cfg.hasPath("sender.name")) return false;

        addressFrom = new InternetAddress(cfg.getString("sender.email"), cfg.getString("sender.name"));

        try {
            final Transport transport = Session.getInstance(props, null).getTransport("smtp");
            transport.connect(String.valueOf(props.get("mail.smtp.host")), getInt(props.get("mail.smtp.port")), smtpUser, smtpPassword);
            transport.close();
        } catch (final Exception e) {
            logger.debug(e.getMessage(), e);

            return false;
        }

        return true;
    }

    @Override
    public void fire(final WatchdogFire fire, final Map<String, String> ctx) {
        try {
            final Session session = auth ? Session.getDefaultInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPassword);
                }
            }) : Session.getDefaultInstance(props);

            session.setDebug(false);

            final Message msg = new SMTPMessage(session);
            msg.setFrom(addressFrom);
            msg.setRecipients(Message.RecipientType.TO, asEmails(ctx.get(RCPT_NAME)));
            msg.setSubject(notNull(ctx.get(SUBJ_NAME), subject));

            final StringBuilder b = new StringBuilder(notNull(ctx.get(BODY_NAME), body));

            if (getBoolean(ctx.get(ATTC_NAME))) {
                b.append("\nServer time: ").append(ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
                b.append("\nWatchdog: ").append(fire.watchdogName).append("\nMatched entries:\n");
                for (final LogEntry entry : fire.matchedEntries)
                    try {b.append("- ").append(objectMapper.writeValueAsString(entry)).append("\n");} catch (final Exception ignore) {}
            }

            msg.setContent(b.toString(), "text/plain; charset=UTF-8");
            msg.setSentDate(new Date());

            Transport.send(msg);
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private InternetAddress[] asEmails(final String v) throws AddressException {
        if (v.indexOf(',') != -1) return Arrays.stream(v.split(Pattern.quote(","))).map(String::valueOf).map(s -> {
            try {
                return new InternetAddress(s);
            } catch (final Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).toArray(InternetAddress[]::new);

        return new InternetAddress[]{new InternetAddress(v)};
    }
}
