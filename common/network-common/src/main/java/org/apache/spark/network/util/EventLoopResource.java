package org.apache.spark.network.util;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoop;
import org.crac.Context;
import org.crac.Resource;

import java.util.Arrays;
import java.util.concurrent.Phaser;
import java.util.stream.StreamSupport;

public class EventLoopResource implements Resource {
    private final EpollEventLoop[] eventLoops;
    private final Phaser phaser;

    public EventLoopResource(EventLoopGroup... eventLoopGroups) {
        eventLoops = Arrays.stream(eventLoopGroups)
                .flatMap(group -> StreamSupport.stream(group.spliterator(), false))
                .filter(EpollEventLoop.class::isInstance).map(EpollEventLoop.class::cast)
                .toArray(EpollEventLoop[]::new);
        if (eventLoops.length > 0) {
            phaser = new Phaser(eventLoops.length + 1);
        } else {
            phaser = null;
        }
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) {
        if (eventLoops.length > 0) {
            for (EpollEventLoop eventLoop : eventLoops) {
                eventLoop.execute(() -> {
                    eventLoop.closeFileDescriptors();
                    phaser.arriveAndAwaitAdvance();
                    phaser.arriveAndAwaitAdvance();
                    eventLoop.openFileDescriptors();
                });
            }
            phaser.arriveAndAwaitAdvance();
        }
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) {
        if (eventLoops.length > 0) {
            phaser.arriveAndAwaitAdvance();
        }
    }
}
