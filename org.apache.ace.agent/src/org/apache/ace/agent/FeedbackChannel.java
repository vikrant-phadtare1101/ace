/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.agent;

import java.io.IOException;
import java.util.Map;


/**
 * 
 *
 */
public interface FeedbackChannel {

    /**
     * Synchronizes the feedback with the current server. Ensures the server has at least as much feedback data as we
     * do.
     */
    void sendFeedback() throws RetryAfterException, IOException;

    /**
     * Logs a new message to the channel.
     * 
     * @param type
     * @param properties
     */
    void write(int type, Map<String, String> properties);
}