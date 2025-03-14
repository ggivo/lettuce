package io.lettuce.core;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.metrics.MicrometerConnectionMonitor;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.test.LettuceExtension;
import io.lettuce.test.Wait;
import io.lettuce.test.resource.TestClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.lettuce.TestTags.INTEGRATION_TEST;
import static io.lettuce.core.metrics.MicrometerConnectionMonitor.METRIC_CONNECTION_INACTIVE_TIME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mark Paluch
 */
@Tag(INTEGRATION_TEST)
@ExtendWith(LettuceExtension.class)
class ConnectionMonitorIntegrationTests extends TestSupport {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final ClientResources clientResources = TestClientResources.get();

    @Test
    void metricConnectionInactiveTime() throws InterruptedException {

        MicrometerOptions options = MicrometerOptions.create();
        MicrometerConnectionMonitor monitor = new MicrometerConnectionMonitor(meterRegistry, options);
        ClientResources resources = clientResources.mutate().connectionMonitor(monitor).build();

        RedisClient client = RedisClient.create(resources, RedisURI.Builder.redis(host, port).build());
        client.getOptions().isAutoReconnect();
        StatefulRedisConnection<String, String> connection = client.connect();
        RedisCommands<String, String> redis = connection.sync();
        Wait.untilTrue(() -> !connection.isOpen()).during(Duration.ofMinutes(5)).waitOrTimeout();
        // redis.quit();

        // Thread.sleep(1000);

        // Wait.untilTrue(connection::isOpen).during(Duration.ofMinutes(5)).waitOrTimeout();

        connection.close();
        meterRegistry.find(METRIC_CONNECTION_INACTIVE_TIME).timers().forEach(timer -> {
            System.out.println(String.format("count: %s, total: %s", timer.count(), timer.totalTime(TimeUnit.MILLISECONDS)));
        });

        assertThat(meterRegistry.find(METRIC_CONNECTION_INACTIVE_TIME).timers()).isNotEmpty();

    }

}
