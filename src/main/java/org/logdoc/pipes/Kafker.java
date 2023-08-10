package org.logdoc.pipes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.logdoc.sdk.PipePlugin;
import org.logdoc.sdk.WatchdogFire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.logdoc.helpers.Texts.getBoolean;


public class Kafker implements PipePlugin {
    private static final Logger logger = LoggerFactory.getLogger(Kafker.class);

    private static final String BOOT_NAME = "kafkaBootstrap", CHANNEL_NAME = "kafkaChannel", ATTC_NAME = "kafkaReport";

    private final ObjectMapper mapper;
    private final AtomicBoolean configured;
    private final Properties protoProps;

    public Kafker() {
        protoProps = new Properties();
        configured = new AtomicBoolean(false);
        mapper = new ObjectMapper();
    }

    @Override
    public void fire(final WatchdogFire fire, final Map<String, String> ctx) throws Exception {
        if (!configured.get())
            throw new Exception("Plugin is not configured");

        final Properties properties = new Properties(protoProps);
        properties.put("bootstrap.servers", ctx.get(BOOT_NAME));

        try (final Producer<String, String> kafkaProducer = new KafkaProducer<>(properties)) {
            final String topic = ctx.get(CHANNEL_NAME);
            fire.matchedEntries
                    .forEach(entry -> {
                        final ObjectNode node = mapper.createObjectNode();
                        node.set("entry", mapper.valueToTree(fire.matchedEntries));

                        if (getBoolean(ctx.get(ATTC_NAME))) {
                            node.put("server_time", ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
                            node.put("watchdog", fire.watchdogName);
                        }

                        kafkaProducer.send(new ProducerRecord<>(topic, node.toString()));
                    });
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean configure(final Config config) {
        if (configured.compareAndSet(false, true)) {
            protoProps.put("key.serializer", StringSerializer.class.getName());
            protoProps.put("value.serializer", StringSerializer.class.getName());
            protoProps.put("key.deserializer", StringDeserializer.class.getName());
            protoProps.put("value.deserializer", StringDeserializer.class.getName());
        }

        return true;
    }
}
