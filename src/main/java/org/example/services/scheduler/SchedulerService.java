package org.example.services.scheduler;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * SchedulerService is a Vert.x service interface used to manage
 * background polling tasks at regular intervals.
 */
@ProxyGen
public interface SchedulerService
{
    /**
     * Creates a new instance of {@link SchedulerService}.
     *
     * @param vertx the Vert.x instance.
     * @return an instance of SchedulerService.
     */
    static SchedulerService create(Vertx vertx)
    {
        return new SchedulerServiceImpl(vertx);
    }

    /**
     * Starts periodic polling at the specified interval. If polling is already running,
     * this method will fail without starting a new timer.
     *
     * Polling performs the following:
     * <ul>
     *     <li>Fetches provisioned device data from the database</li>
     *     <li>Decrypts credentials</li>
     *     <li>Performs ping and port checks</li>
     *     <li>Logs device availability to the database</li>
     *     <li>Runs SSH metric collection plugin for reachable devices</li>
     *     <li>Stores polling results in the database</li>
     * </ul>
     *
     * @param interval the polling interval in milliseconds
     * @return a succeeded Future if polling started, or a failed Future if already running
     */
    Future<String> startPolling(int interval);
}
