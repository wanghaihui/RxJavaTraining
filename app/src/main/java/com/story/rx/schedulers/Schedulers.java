/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.story.rx.schedulers;

import rx.Scheduler;
import rx.internal.schedulers.*;
import rx.internal.util.RxRingBuffer;
import rx.plugins.RxJavaPlugins;

import java.util.concurrent.Executor;

/**
 * Static factory methods for creating Schedulers.
 */
public final class Schedulers {

    private final Scheduler computationScheduler;
    private final Scheduler ioScheduler;
    private final Scheduler newThreadScheduler;

    private static final Schedulers INSTANCE = new Schedulers();

    private Schedulers() {
        Scheduler c = RxJavaPlugins.getInstance().getSchedulersHook().getComputationScheduler();
        if (c != null) {
            computationScheduler = c;
        } else {
            computationScheduler = new EventLoopsScheduler();
        }

        Scheduler io = RxJavaPlugins.getInstance().getSchedulersHook().getIOScheduler();
        if (io != null) {
            ioScheduler = io;
        } else {
            ioScheduler = new CachedThreadScheduler();
        }

        Scheduler nt = RxJavaPlugins.getInstance().getSchedulersHook().getNewThreadScheduler();
        if (nt != null) {
            newThreadScheduler = nt;
        } else {
            newThreadScheduler = NewThreadScheduler.instance();
        }
    }

    /**
     * Creates and returns a {@link Scheduler} that executes work immediately on the current thread.
     *
     * 直接在当前线程运行，相当于不指定线程 -- 这是默认的 Scheduler
     * 
     * @return an {@link ImmediateScheduler} instance
     */
    public static Scheduler immediate() {
        return ImmediateScheduler.instance();
    }

    /**
     * Creates and returns a {@link Scheduler} that queues work on the current thread to be executed after the
     * current work completes.
     * 
     * @return a {@link TrampolineScheduler} instance
     */
    public static Scheduler trampoline() {
        return TrampolineScheduler.instance();
    }

    /**
     * 总是启用新线程，并在新线程执行操作
     * Creates and returns a {@link Scheduler} that creates a new {@link Thread} for each unit of work.
     * <p>
     * Unhandled errors will be delivered to the scheduler Thread's {@link Thread.UncaughtExceptionHandler}.
     *
     * @return a {@link NewThreadScheduler} instance
     */
    public static Scheduler newThread() {
        return INSTANCE.newThreadScheduler;
    }

    /**
     * 计算所使用的 Scheduler
     * 这个计算指的是 CPU 密集型计算，即不会被 I/O 等操作限制性能的操作，例如图形的计算
     * 这个 Scheduler 使用的固定的线程池，大小为 CPU 核数。不要把 I/O 操作放在 computation() 中，否则 I/O 操作的等待时间会浪费 CPU
     * Creates and returns a {@link Scheduler} intended for computational work.
     * <p>
     * This can be used for event-loops, processing callbacks and other computational work.
     * <p>
     * Do not perform IO-bound work on this scheduler. Use {@link #io()} instead.
     * <p>
     * Unhandled errors will be delivered to the scheduler Thread's {@link Thread.UncaughtExceptionHandler}.
     *
     * @return a {@link Scheduler} meant for computation-bound work
     */
    public static Scheduler computation() {
        return INSTANCE.computationScheduler;
    }

    /**
     *  I/O 操作（读写文件、读写数据库、网络信息交互等）所使用的 Scheduler
     *  内部实现是是用一个无数量上限的线程池，可以重用空闲的线程，因此多数情况下 io() 比 newThread() 更有效率
     *  不要把计算工作放在 io() 中，可以避免创建不必要的线程
     *
     *
     * Creates and returns a {@link Scheduler} intended for IO-bound work.
     * <p>
     * The implementation is backed by an {@link Executor} thread-pool that will grow as needed.
     * <p>
     * This can be used for asynchronously performing blocking IO.
     * <p>
     * Do not perform computational work on this scheduler. Use {@link #computation()} instead.
     * <p>
     * Unhandled errors will be delivered to the scheduler Thread's {@link Thread.UncaughtExceptionHandler}.
     *
     * @return a {@link Scheduler} meant for IO-bound work
     */
    public static Scheduler io() {
        return INSTANCE.ioScheduler;
    }

    /**
     * Creates and returns a {@code TestScheduler}, which is useful for debugging. It allows you to test
     * schedules of events by manually advancing the clock at whatever pace you choose.
     *
     * @return a {@code TestScheduler} meant for debugging
     */
    public static TestScheduler test() {
        return new TestScheduler();
    }

    /**
     * Converts an {@link Executor} into a new Scheduler instance.
     *
     * @param executor
     *          the executor to wrap
     * @return the new Scheduler wrapping the Executor
     */
    public static Scheduler from(Executor executor) {
        return new ExecutorScheduler(executor);
    }
    
    /**
     * Starts those standard Schedulers which support the SchedulerLifecycle interface.
     * <p>The operation is idempotent and threadsafe.
     */
    /* public testonly */ static void start() {
        Schedulers s = INSTANCE;
        synchronized (s) {
            if (s.computationScheduler instanceof SchedulerLifecycle) {
                ((SchedulerLifecycle) s.computationScheduler).start();
            }
            if (s.ioScheduler instanceof SchedulerLifecycle) {
                ((SchedulerLifecycle) s.ioScheduler).start();
            }
            if (s.newThreadScheduler instanceof SchedulerLifecycle) {
                ((SchedulerLifecycle) s.newThreadScheduler).start();
            }
            GenericScheduledExecutorService.INSTANCE.start();
            
            RxRingBuffer.SPSC_POOL.start();
            
            RxRingBuffer.SPMC_POOL.start();
        }
    }
    /**
     * Shuts down those standard Schedulers which support the SchedulerLifecycle interface.
     * <p>The operation is idempotent and threadsafe.
     */
    public static void shutdown() {
        Schedulers s = INSTANCE;
        synchronized (s) {
            if (s.computationScheduler instanceof SchedulerLifecycle) {
                ((SchedulerLifecycle) s.computationScheduler).shutdown();
            }
            if (s.ioScheduler instanceof SchedulerLifecycle) {
                ((SchedulerLifecycle) s.ioScheduler).shutdown();
            }
            if (s.newThreadScheduler instanceof SchedulerLifecycle) {
                ((SchedulerLifecycle) s.newThreadScheduler).shutdown();
            }
            
            GenericScheduledExecutorService.INSTANCE.shutdown();
            
            RxRingBuffer.SPSC_POOL.shutdown();
            
            RxRingBuffer.SPMC_POOL.shutdown();
        }
    }
}