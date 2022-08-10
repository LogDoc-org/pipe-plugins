package entrypipes;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import org.logdoc.sdk.PipePlugin;
import org.logdoc.sdk.WatcherMetrics;
import org.logdoc.structs.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.Json;
import utils.Httper;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.logdoc.utils.Tools.getBoolean;
import static org.logdoc.utils.Tools.getLong;
import static org.logdoc.utils.Tools.isEmpty;
import static org.logdoc.utils.Tools.notNull;

public class HttpCallback implements PipePlugin {
    private static final Logger logger = LoggerFactory.getLogger(HttpCallback.class);
    public static final String
            URL_NAME = "httpUrl",
            TMT_NAME = "httpTimeoutMs",
            MET_NAME = "httpMethod",
            ATC_NAME = "httpReport",
            CNS_NAME = "httpConstants",
            HDR_NAME = "httpHeaders";

    public HttpCallback() {
    }

    @Override
    public void configure(final Config config) {}

    @Override
    public void fire(final String watcherId, final LogEntry entry, final WatcherMetrics metrics, final Map<String, ?> ctx) throws Exception {
        final URL url = new URL(notNull(ctx.get(URL_NAME)));
        final String method = notNull(ctx.get(MET_NAME), "GET").toUpperCase();
        final boolean attachReport = getBoolean(ctx.get(ATC_NAME));
        final long timeout = getLong(ctx.get(TMT_NAME));
        final Collection<StringNameValuePair> headers = asNVPList(ctx.get(HDR_NAME));
        final Collection<StringNameValuePair> constants = asNVPList(ctx.get(CNS_NAME));


        final Httper httper = new Httper();
        if (!isEmpty(headers))
            headers.forEach(h -> httper.addHeader(h.name, h.value));

        URL url0 = url;
        Consumer<OutputStream> feeder = null;

        if (attachReport) {
            final ObjectNode node = (ObjectNode) Json.toJson(metrics);
            node.put("server_time", ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME));

            if (!isEmpty(constants))
                constants.forEach(c -> node.put(c.name, c.value));

            if (method.equalsIgnoreCase("get"))
                url0 = new URL(url0.toExternalForm() + (url0.toExternalForm().contains("?") ? "&" : "?") + "report=" + URLEncoder.encode(node.toString(), "UTF-8"));
            else
                feeder = os -> {
                    try {
                        os.write(node.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (final Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                };
        }

        final URL finalUrl = url0;
        final Consumer<OutputStream> finalFeeder = feeder;
        CompletableFuture.runAsync(() -> {
            try {
                httper.exec(finalUrl, method, timeout, finalFeeder, false);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    private Collection<StringNameValuePair> asNVPList(final Object v) {
        if (v != null)
            try {
                if (Collection.class.isAssignableFrom(v.getClass()))
                    return ((Collection<?>) v).stream()
                            .map(o -> notNull(v))
                            .filter(s -> !isEmpty(s))
                            .filter(s -> s.indexOf('=') != -1)
                            .map(s -> {
                                final int idx = s.indexOf('=');
                                return new StringNameValuePair(s.substring(0, idx), s.substring(idx + 1));
                            })
                            .collect(Collectors.toList());

                final String s = notNull(v);
                final int idx = s.indexOf('=');

                if (idx > -1)
                    return Collections.singletonList(new StringNameValuePair(s.substring(0, idx), s.substring(idx + 1)));
            } catch (final Exception ignore) {}

        return Collections.emptyList();
    }

    public static class StringNameValuePair {
        public String name;
        public String value;

        public StringNameValuePair(final String name, final String value) {
            this.name = notNull(name);
            this.value = notNull(value);
        }
    }
}
