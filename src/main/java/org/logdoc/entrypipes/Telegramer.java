package org.logdoc.entrypipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import org.logdoc.sdk.PipePlugin;
import org.logdoc.sdk.WatcherMetrics;
import org.logdoc.structs.LogEntry;
import org.logdoc.utils.Httper;
import org.logdoc.utils.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.Json;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.logdoc.utils.Tools.getBoolean;
import static org.logdoc.utils.Tools.getLong;

public class Telegramer implements PipePlugin {
    private static final Logger logger = LoggerFactory.getLogger(Telegramer.class);
    private static final Pattern md = Pattern.compile("([!=#\\.\\(\\)\\*\\[\\]\"`'~_\\-+])");

    public static final String
            UID_NAME = "telegramUids",
            BOD_NAME = "telegramBody",
            ATC_NAME = "telegramReport";

    private URL apiUrl;

    @Override
    public void configure(final Config config) throws Exception {
        apiUrl = new URL(config.getString("api_url"));

        final JsonNode reply = Json.parse(new Httper().exec(new URL(apiUrl.toString().replace("sendMessage", "getWebhookInfo")), "GET", 1500, null, true).responseMessage);

        if (!reply.get("ok").asBoolean())
            throw new Exception("API URL configuration failed: " + apiUrl);
    }

    @Override
    public void fire(final String watcherId, final LogEntry entry, final WatcherMetrics metrics, final Map<String, ?> ctx) {
        final Collection<Long> recipients = asLongList(ctx.get(UID_NAME));

        if (recipients.isEmpty()) {
            logger.warn("Recipients are not defined, skip run.");
            return;
        }

        String b = Tools.notNull(ctx.get(BOD_NAME), "Watcher fired");

        if (getBoolean(ctx.get(ATC_NAME))) {
            if (metrics.entryCountable) {
                b += "\nEvents total: " + metrics.totalEntryCounter;
                b += "\nEvents in current cycle: " + metrics.cycleEntryCounter + "/" + metrics.cycleEntryLimit;
            }
            if (metrics.cycleRepeatable)
                b += "\nRepeats: " + metrics.cycleCounter + "/" + metrics.cycleLimit;
            b += "\nServer time: " + ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
            b += "\nLast entry: " + Json.toJson(entry);
        }

        final ObjectNode node = Json.newObject();
        //node.put("parse_mode", "MarkdownV2");
        node.put("disable_web_page_preview", true);
        node.put("text", escapeMd(b));

        final Httper httper = new Httper();
        httper.addHeader("Content-type", "application/json");

        for (final long id : recipients) {
            node.put("chat_id", id);
            Httper.Action action = null;
            try {
                action = httper.exec(apiUrl, "POST", 3000L, os -> {
                    try {
                        os.write(node.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (final IOException e) {
                        logger.error(id + " :: " + e.getMessage(), e);
                    }
                }, true);
            } catch (Exception e) {
                logger.error(id + " :: " + e.getMessage(), e);
            } finally {
                logger.debug("curl -X POST " + apiUrl + " -H 'Content-Type: application/json' -d '" + node + (action == null ? "\n>> NIL" :
                        "'\n>> [" + action.responseCode + "]\n" + action.responseMessage));
            }
        }
    }

    private Collection<Long> asLongList(final Object o) {
        if (o != null)
            try {
                if (Collection.class.isAssignableFrom(o.getClass()))
                    return ((Collection<?>) o).stream()
                            .map(Tools::getLong)
                            .filter(l -> l > 0)
                            .collect(Collectors.toList());

                return Collections.singletonList(getLong(o));
            } catch (final Exception ignore) {}

        return Collections.emptyList();
    }

    public static String escapeMd(final String s) {
        return md.matcher(s).replaceAll("\\\\$1");
    }

}
