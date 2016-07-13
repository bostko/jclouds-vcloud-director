/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.vcloud.director.v1_5.handlers;

import static org.jclouds.http.HttpUtils.closeClientButKeepContentStream;

import java.util.Set;

import org.jclouds.http.HttpCommand;
import org.jclouds.http.HttpResponse;
import org.jclouds.http.annotation.ClientError;
import org.jclouds.http.handlers.BackoffLimitedRetryHandler;
import org.jclouds.logging.Logger;
import org.jclouds.vcloud.director.v1_5.domain.VcloudDirectorError;

import com.google.inject.Inject;

import javax.annotation.Resource;

/**
 * Handles Retryable responses with error codes in the 4xx range
 */
public class VcloudDirectorClientErrorRetryHandler extends BackoffLimitedRetryHandler {

   private final VcloudDirectorUtils utils;
   private final Set<String> retryableCodes;

   @Resource
   protected Logger logger = Logger.NULL;

   @Inject
   protected VcloudDirectorClientErrorRetryHandler(VcloudDirectorUtils utils, 
         @ClientError Set<String> retryableCodes) {
      this.utils = utils;
      this.retryableCodes = retryableCodes;
   }

   @Override
   public boolean shouldRetryRequest(HttpCommand command, HttpResponse response) {
      if (response.getStatusCode() == 400 || response.getStatusCode() == 403 || response.getStatusCode() == 409) {
         // Content can be null in the case of HEAD requests
         if (response.getPayload() != null) {
            closeClientButKeepContentStream(response);
            VcloudDirectorError error = utils.parseVcloudDirectorErrorFromContent(command.getCurrentRequest(), response);
            if (error != null) {

               return shouldRetryRequestOnError(command, response, error);
            }
         }
      }
      return false;
   }

   protected boolean shouldRetryRequestOnError(HttpCommand command, HttpResponse response, VcloudDirectorError error) {
      if (retryableCodes.contains(error.getMinorErrorCode())) {
         // Since nowhere in org.jclouds.http.internal.BaseHttpCommandExecutorService retries are logged. I added this message.
         logger.debug("VCD API returned an error that should be retried: {}", error);
         return super.shouldRetryRequest(command, response);
      }
      return false;
   }

   @Override
   public void imposeBackoffExponentialDelay(long period, int pow, int failureCount, int max, String commandDescription) {
      imposeBackoffExponentialDelay(period, period * 100l, pow, failureCount, max, commandDescription);
   }
}
