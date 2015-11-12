/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugin.ingest.transport.simulate;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.ingest.Data;
import org.elasticsearch.ingest.Pipeline;
import org.elasticsearch.ingest.processor.Processor;
import org.elasticsearch.plugin.ingest.transport.TransportData;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.ThreadPool;
import org.junit.After;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

public class SimulateExecutionServiceTests extends ESTestCase {

    private ThreadPool threadPool;
    private SimulateExecutionService executionService;
    private Pipeline pipeline;
    private Processor processor;
    private Data data;
    private TransportData transportData;
    private ActionListener<SimulatePipelineResponse> listener;

    @Before
    public void setup() {
        threadPool = new ThreadPool(
                Settings.builder()
                        .put("name", getClass().getName())
                        .build()
        );
        executionService = new SimulateExecutionService(threadPool);
        processor = mock(Processor.class);
        when(processor.getType()).thenReturn("mock");
        pipeline = new Pipeline("_id", "_description", Arrays.asList(processor, processor));
        data = new Data("_index", "_type", "_id", Collections.singletonMap("foo", "bar"));
        transportData = new TransportData(data);
        listener = mock(ActionListener.class);
    }

    @After
    public void destroy() {
        threadPool.shutdown();
    }

    public void testExecuteVerboseItem() throws Exception {
        SimulateDocumentResult expectedItemResponse = new SimulateDocumentResult(
                Arrays.asList(new SimulateProcessorResult("processor[mock]-0", data), new SimulateProcessorResult("processor[mock]-1", data)));
        SimulateDocumentResult actualItemResponse = executionService.executeVerboseItem(pipeline, data);
        verify(processor, times(2)).execute(data);
        assertThat(actualItemResponse, equalTo(expectedItemResponse));
    }

    public void testExecuteItem() throws Exception {
        SimulateDocumentResult expectedItemResponse = new SimulateDocumentResult(data);
        SimulateDocumentResult actualItemResponse = executionService.executeItem(pipeline, data);
        verify(processor, times(2)).execute(data);
        assertThat(actualItemResponse, equalTo(expectedItemResponse));
    }

    public void testExecuteVerboseItemWithFailure() throws Exception {
        Exception e = new RuntimeException("processor failed");
        SimulateDocumentResult expectedItemResponse = new SimulateDocumentResult(
                Arrays.asList(new SimulateProcessorResult("processor[mock]-0", e), new SimulateProcessorResult("processor[mock]-1", data))
        );
        doThrow(e).doNothing().when(processor).execute(data);
        SimulateDocumentResult actualItemResponse = executionService.executeVerboseItem(pipeline, data);
        verify(processor, times(2)).execute(data);
        assertThat(actualItemResponse, equalTo(expectedItemResponse));
    }

    public void testExecuteItemWithFailure() throws Exception {
        Exception e = new RuntimeException("processor failed");
        SimulateDocumentResult expectedItemResponse = new SimulateDocumentResult(e);
        doThrow(e).when(processor).execute(data);
        SimulateDocumentResult actualItemResponse = executionService.executeItem(pipeline, data);
        verify(processor, times(1)).execute(data);
        assertThat(actualItemResponse, equalTo(expectedItemResponse));
    }

    public void testExecute() throws Exception {
        SimulateDocumentResult itemResponse = new SimulateDocumentResult(data);
        ParsedSimulateRequest request = new ParsedSimulateRequest(pipeline, Collections.singletonList(data), false);
        executionService.execute(request, listener);
        SimulatePipelineResponse response = new SimulatePipelineResponse("_id", Collections.singletonList(itemResponse));
        assertBusy(new Runnable() {
            @Override
            public void run() {
                verify(processor, times(2)).execute(data);
                verify(listener).onResponse(response);
            }
        });
    }

    public void testExecuteWithVerbose() throws Exception {
        ParsedSimulateRequest request = new ParsedSimulateRequest(pipeline, Collections.singletonList(data), true);
        SimulateDocumentResult itemResponse = new SimulateDocumentResult(
                Arrays.asList(new SimulateProcessorResult("processor[mock]-0", data), new SimulateProcessorResult("processor[mock]-1", data)));
        executionService.execute(request, listener);
        SimulatePipelineResponse response = new SimulatePipelineResponse("_id", Collections.singletonList(itemResponse));
        assertBusy(new Runnable() {
            @Override
            public void run() {
                verify(processor, times(2)).execute(data);
                verify(listener).onResponse(response);
            }
        });
    }
}
