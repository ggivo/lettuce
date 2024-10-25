package io.lettuce.core;

import static io.lettuce.TestTags.UNIT_TEST;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.metrics.CommandLatencyCollector;
import io.lettuce.core.output.ValueListOutput;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandHandler;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.Endpoint;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.Tracing;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.util.concurrent.ImmediateEventExecutor;

/**
 * @author Mark Paluch
 */
@Tag(UNIT_TEST)
@ExtendWith(MockitoExtension.class)
class ReactiveBackpressurePropagationUnitTests {

    private CommandHandler commandHandler;

    private EmbeddedChannel embeddedChannel;

    @Mock
    private Endpoint endpoint;

    @Mock
    private ClientResources clientResources;

    @Mock
    private CommandLatencyCollector latencyCollector;

    @Mock
    private StatefulConnection statefulConnection;

    @BeforeEach
    void before() {

        when(clientResources.commandLatencyRecorder()).thenReturn(latencyCollector);
        when(clientResources.tracing()).thenReturn(Tracing.disabled());
        when(statefulConnection.dispatch(any(RedisCommand.class))).thenAnswer(invocation -> {

            RedisCommand command = (RedisCommand) invocation.getArguments()[0];
            embeddedChannel.writeOutbound(command);
            return command;
        });

        commandHandler = new CommandHandler(ClientOptions.create(), clientResources, endpoint);

        embeddedChannel = new EmbeddedChannel(commandHandler);
        embeddedChannel.connect(new LocalAddress("remote"));
    }

    @Test
    void writeCommand() throws Exception {

        Command<String, String, List<String>> lrange = new Command<>(CommandType.LRANGE,
                new ValueListOutput<>(StringCodec.UTF8));
        RedisPublisher<String, String, String> publisher = new RedisPublisher<>((Command) lrange, statefulConnection, true,
                ImmediateEventExecutor.INSTANCE);

        CountDownLatch pressureArrived = new CountDownLatch(1);
        CountDownLatch buildPressure = new CountDownLatch(1);
        CountDownLatch waitForPressureReduced = new CountDownLatch(2);
        CountDownLatch waitForWorkCompleted = new CountDownLatch(4);

        Flux.from(publisher).limitRate(2).publishOn(Schedulers.single()).doOnNext(s -> {

            try {
                pressureArrived.countDown();
                buildPressure.await();
            } catch (InterruptedException e) {
            }

            waitForPressureReduced.countDown();
            waitForWorkCompleted.countDown();

        }).subscribe();

        assertThat(embeddedChannel.config().isAutoRead()).isTrue();

        // produce some back pressure
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.arrayHeader(4)));
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.bulkString("one")));
        pressureArrived.await();
        assertThat(embeddedChannel.config().isAutoRead()).isTrue();

        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.bulkString("two")));
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.bulkString("three")));
        assertThat(embeddedChannel.config().isAutoRead()).isFalse();

        // allow processing
        buildPressure.countDown();

        // wait until processing caught up
        waitForPressureReduced.await();
        assertThat(embeddedChannel.config().isAutoRead()).isTrue();

        // emit the last item
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.bulkString("four")));

        // done
        waitForWorkCompleted.await();
        assertThat(embeddedChannel.config().isAutoRead()).isTrue();
    }

    @Test
    void writeCommandAndCancelInTheMiddle() throws Exception {

        Command<String, String, List<String>> lrange = new Command<>(CommandType.LRANGE,
                new ValueListOutput<>(StringCodec.UTF8));
        RedisPublisher<String, String, String> publisher = new RedisPublisher<>(lrange, statefulConnection, true,
                ImmediateEventExecutor.INSTANCE);

        CountDownLatch pressureArrived = new CountDownLatch(1);
        CountDownLatch buildPressure = new CountDownLatch(1);
        CountDownLatch waitForPressureReduced = new CountDownLatch(2);

        Disposable cancellation = Flux.from(publisher).limitRate(2).publishOn(Schedulers.single()).doOnNext(s -> {

            try {
                pressureArrived.countDown();
                buildPressure.await();
            } catch (InterruptedException e) {
            }

            waitForPressureReduced.countDown();

        }).subscribe();

        assertThat(embeddedChannel.config().isAutoRead()).isTrue();

        // produce some back pressure
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.arrayHeader(4)));
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.bulkString("one")));
        pressureArrived.await();
        assertThat(embeddedChannel.config().isAutoRead()).isTrue();

        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.bulkString("two")));
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.bulkString("three")));
        assertThat(embeddedChannel.config().isAutoRead()).isFalse();

        cancellation.dispose();

        assertThat(embeddedChannel.config().isAutoRead()).isTrue();

        // allow processing
        buildPressure.countDown();

        // emit the last item
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(RESP.bulkString("four")));

        // done
        assertThat(embeddedChannel.config().isAutoRead()).isTrue();
    }

    static class RESP {

        static byte[] arrayHeader(int count) {
            return String.format("*%d\r\n", count).getBytes();
        }

        static byte[] bulkString(String string) {
            return String.format("$%d\r\n%s\r\n", string.getBytes().length, string).getBytes();
        }

    }

}
