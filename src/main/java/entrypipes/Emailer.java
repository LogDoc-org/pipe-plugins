package entrypipes;

import com.sun.mail.smtp.SMTPMessage;
import com.typesafe.config.Config;
import org.logdoc.sdk.PipePlugin;
import org.logdoc.sdk.WatcherMetrics;
import org.logdoc.structs.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.Json;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.logdoc.utils.Tools.getBoolean;
import static org.logdoc.utils.Tools.getInt;
import static org.logdoc.utils.Tools.notNull;


public class Emailer implements PipePlugin {
    private static final Logger logger = LoggerFactory.getLogger(Emailer.class);

    private static final String
            RCPT_NAME = "emailRecipients",
            BODY_NAME = "emailBody",
            SUBJ_NAME = "emailSubject",
            ATTC_NAME = "emailReport";

    private final Properties props;
    private final AtomicBoolean configured;
    private InternetAddress addressFrom;
    private String smtpUser, smtpPassword, subject, body;

    public Emailer() {
        props = new Properties();
        configured = new AtomicBoolean(false);
        subject = "";
        body = "";
    }

    @Override
    public void configure(final Config config) throws Exception {
        if (configured.get() || config == null || config.isEmpty())
            return;

        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", config.getString("smtp.host"));
        props.put("mail.smtp.port", config.getString("smtp.port"));
        props.put("mail.smtp.timeout", "1500");
        props.put("mail.smtp.connectiontimeout", "1500");
        if (config.getBoolean("smtp.ssl")) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtps.timeout", "1500");
            props.put("mail.smtps.connectiontimeout", "1500");
            props.put("mail.smtp.socketFactory.port", config.getString("smtp.port"));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        }

        if (config.getBoolean("smtp.tls")) {
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        }

        smtpUser = config.getString("smtp.auth.user");
        smtpPassword = config.getString("smtp.auth.password");
        subject = config.hasPath("default_subject") ? config.getString("default_subject") : "Logdoc notification";
        body = config.hasPath("default_body") ? config.getString("default_body") : "Logdoc notification";

        addressFrom = new InternetAddress(config.getString("sender.email"), config.getString("sender.name"));

        try {
            final Transport transport = Session.getInstance(props, null).getTransport("smtp");
            transport.connect(String.valueOf(props.get("mail.smtp.host")), getInt(props.get("mail.smtp.port")), smtpUser, smtpPassword);
            transport.close();
        } catch (final Exception e) {
            if (config.getBoolean("smtp.debug")) {
                throw new Exception("SMTP is not configured or credentials are wrong: " + props + " :: " + smtpUser + " :: " + e.getMessage(), e);
            } else
                throw new Exception("SMTP is not configured or credentials are wrong: " + props + " :: " + smtpUser + " :: " + e.getMessage());
        }

        configured.set(true);
    }

    @Override
    public void fire(final String watcherId, final LogEntry entry, final WatcherMetrics metrics, final Map<String, ?> ctx) throws Exception {
        if (!configured.get())
            throw new Exception("Plugin is not configured");

        try {
            final Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPassword);
                }
            });

            session.setDebug(false);
            final Message msg = new SMTPMessage(session);
            msg.setFrom(addressFrom);
            msg.setRecipients(Message.RecipientType.TO, asEmails(ctx.get(RCPT_NAME)));
            msg.setSubject(notNull(ctx.get(SUBJ_NAME), subject));

            String b = notNull(ctx.get(BODY_NAME), body);

            if (getBoolean(ctx.get(ATTC_NAME))) {
                if (metrics.entryCountable) {
                    b += "\nEvents total: " + metrics.totalEntryCounter;
                    b += "\nEvents in current cycle: " + metrics.cycleEntryCounter + "/" + metrics.cycleEntryLimit;
                }
                if (metrics.cycleRepeatable)
                    b += "\nRepeats: " + metrics.cycleCounter + "/" + metrics.cycleLimit;
                b += "\nServer time: " + ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
                b += "\nLast entry: " + Json.toJson(entry);
            }

            msg.setContent(b, "text/plain; charset=UTF-8");
            msg.setSentDate(new Date());

            Transport.send(msg);
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private InternetAddress[] asEmails(final Object v) throws AddressException {
        if (v instanceof String)
            return new InternetAddress[]{new InternetAddress(String.valueOf(v))};

        if (Collection.class.isAssignableFrom(v.getClass())) {
            return ((Collection<?>) v).stream()
                    .map(String::valueOf)
                    .map(s -> {
                        try {
                            return new InternetAddress(s);
                        } catch (final Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull).toArray(InternetAddress[]::new);
        }

        throw new AddressException("Unknown param as list of addresses: " + v);
    }
}
