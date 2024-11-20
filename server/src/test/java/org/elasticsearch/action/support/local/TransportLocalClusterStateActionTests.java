/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.support.local;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.ActionTestUtils;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlock;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.block.ClusterBlocks;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.transport.CapturingTransport;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.test.ClusterServiceUtils.createClusterService;
import static org.elasticsearch.test.ClusterServiceUtils.setState;

public class TransportLocalClusterStateActionTests extends ESTestCase {

    private static ThreadPool threadPool;

    private ClusterService clusterService;
    private TransportService transportService;
    private CapturingTransport transport;

    @BeforeClass
    public static void beforeClass() {
        threadPool = new TestThreadPool(getTestClass().getName());
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        transport = new CapturingTransport();
        clusterService = createClusterService(threadPool);
        transportService = transport.createTransportService(
            clusterService.getSettings(),
            threadPool,
            TransportService.NOOP_TRANSPORT_INTERCEPTOR,
            x -> clusterService.localNode(),
            null,
            Collections.emptySet()
        );
        transportService.start();
        transportService.acceptIncomingRequests();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        clusterService.close();
        transportService.close();
    }

    @AfterClass
    public static void afterClass() {
        ThreadPool.terminate(threadPool, 30, TimeUnit.SECONDS);
        threadPool = null;
    }

    public void testRetryAfterBlock() throws ExecutionException, InterruptedException {
        var request = new Request();
        ClusterBlock block = new ClusterBlock(1, "", true, true, false, randomFrom(RestStatus.values()), ClusterBlockLevel.ALL);
        var state = ClusterState.builder(clusterService.state()).blocks(ClusterBlocks.builder().addGlobalBlock(block)).build();
        setState(clusterService, state);

        PlainActionFuture<Response> listener = new PlainActionFuture<>();
        ActionTestUtils.execute(new Action(transportService, clusterService) {
            @Override
            protected ClusterBlockException checkBlock(Request request, ClusterState state) {
                Set<ClusterBlock> blocks = state.blocks().global();
                return blocks.isEmpty() ? null : new ClusterBlockException(blocks);
            }
        }, null, request, listener);

        assertFalse(listener.isDone());
        setState(clusterService, ClusterState.builder(state).blocks(ClusterBlocks.EMPTY_CLUSTER_BLOCK).build());
        assertTrue(listener.isDone());
        listener.get();
    }

    private static class Request extends LocalClusterStateRequest {

        protected Request() {
            super(TEST_REQUEST_TIMEOUT);
        }

        @Override
        public ActionRequestValidationException validate() {
            return null;
        }
    }

    private static class Response extends ActionResponse {

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            TransportAction.localOnly();
        }
    }

    private static class Action extends TransportLocalClusterStateAction<Request, Response> {
        static final String ACTION_NAME = "internal:testAction";

        Action(TransportService transportService, ClusterService clusterService) {
            super(
                ACTION_NAME,
                new ActionFilters(Set.of()),
                transportService.getTaskManager(),
                clusterService,
                EsExecutors.DIRECT_EXECUTOR_SERVICE
            );
        }

        @Override
        protected void localClusterStateOperation(Task task, Request request, ClusterState state, ActionListener<Response> listener)
            throws Exception {
            listener.onResponse(new Response()); // default implementation, overridden in specific tests
        }

        @Override
        protected ClusterBlockException checkBlock(Request request, ClusterState state) {
            return null; // default implementation, overridden in specific tests
        }
    }
}
