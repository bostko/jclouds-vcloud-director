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
package org.jclouds.vcloud.director.v1_5;


import static org.jclouds.Constants.PROPERTY_MAX_RETRIES;
import static org.jclouds.Constants.PROPERTY_RETRY_DELAY_START;
import static org.jclouds.Constants.PROPERTY_SESSION_INTERVAL;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_NODE_SUSPENDED;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_NODE_TERMINATED;
import static org.jclouds.reflect.Reflection2.typeToken;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorConstants.PROPERTY_VCLOUD_DIRECTOR_HARDWARE_MAX_CPU;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorConstants.PROPERTY_VCLOUD_DIRECTOR_HARDWARE_MAX_RAM;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorConstants.PROPERTY_VCLOUD_DIRECTOR_HARDWARE_MIN_RAM;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorConstants.PROPERTY_VCLOUD_DIRECTOR_PREDEFINED_HARDWARE_PROFILES;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorConstants.PROPERTY_VCLOUD_DIRECTOR_TIMEOUT_TASK_COMPLETED;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorConstants.PROPERTY_VCLOUD_DIRECTOR_VERSION_SCHEMA;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorConstants.PROPERTY_VCLOUD_DIRECTOR_XML_NAMESPACE;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorConstants.PROPERTY_VCLOUD_DIRECTOR_XML_SCHEMA;

import java.net.URI;
import java.util.Properties;

import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.http.okhttp.config.OkHttpCommandExecutorServiceModule;
import org.jclouds.rest.internal.BaseHttpApiMetadata;
import org.jclouds.vcloud.director.v1_5.compute.config.VCloudDirectorComputeServiceContextModule;
import org.jclouds.vcloud.director.v1_5.config.VCloudDirectorHttpApiModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import org.jclouds.vcloud.director.v1_5.http.okhttp.config.OkHttpCommandExecutorServiceVcloudModule;

/**
 * Implementation of {@link org.jclouds.apis.ApiMetadata} for VCloudDirector 1.5 API
 */
public class VCloudDirectorApiMetadata extends BaseHttpApiMetadata<VCloudDirectorApi> {

   @Override
   public Builder toBuilder() {
      return new Builder().fromApiMetadata(this);
   }

   public VCloudDirectorApiMetadata() {
      this(new Builder());
   }

   public VCloudDirectorApiMetadata(Builder builder) {
      super(builder);
   }

   public static Properties defaultProperties() {
      Properties properties = BaseHttpApiMetadata.defaultProperties();
      /** FIXME this should not be the default */
      properties.setProperty(PROPERTY_SESSION_INTERVAL, Integer.toString(30 * 60));

      properties.setProperty(PROPERTY_VCLOUD_DIRECTOR_XML_NAMESPACE,
              String.format("http://www.vmware.com/vcloud/v${%s}", PROPERTY_VCLOUD_DIRECTOR_VERSION_SCHEMA));
      properties.setProperty(PROPERTY_SESSION_INTERVAL, Integer.toString(8 * 60));
      properties.setProperty(PROPERTY_VCLOUD_DIRECTOR_XML_SCHEMA, "${jclouds.endpoint}/v1.5/schema/master.xsd");

      // TODO integrate these with the {@link ComputeTimeouts} instead of having a single timeout for everything.
      properties.setProperty(PROPERTY_SESSION_INTERVAL, Integer.toString(300));
      properties.setProperty(PROPERTY_VCLOUD_DIRECTOR_TIMEOUT_TASK_COMPLETED, Long.toString(1200l * 1000l));

      properties.setProperty(PROPERTY_VCLOUD_DIRECTOR_HARDWARE_MAX_CPU, "" + 8);
      properties.setProperty(PROPERTY_VCLOUD_DIRECTOR_HARDWARE_MIN_RAM, "" + 512);
      properties.setProperty(PROPERTY_VCLOUD_DIRECTOR_HARDWARE_MAX_RAM, "" + 1024 * 32);
      properties.setProperty(PROPERTY_VCLOUD_DIRECTOR_PREDEFINED_HARDWARE_PROFILES, "");

      properties.setProperty(TIMEOUT_NODE_TERMINATED, "" + 5 * 60 * 1000);
      properties.setProperty(TIMEOUT_NODE_SUSPENDED, "" + 5 * 60 * 1000);

      // Be more conservative about exponential backoff with vCloudDirector - have seen throttling
      // far more with vCD than other clouds like AWS.
      // This will backoff each time for a delay of 100 * failureCount^2, to a max of 100*100 = 10 seconds.
      // The exponential backoff times will be: 100ms, 400ms, 900ms, 1600ms, 2500ms, 5000ms
      properties.setProperty(PROPERTY_MAX_RETRIES, "" + 6); // jclouds default is 5
      properties.setProperty(PROPERTY_RETRY_DELAY_START, "" + 100); // jclouds default is 50

      return properties;
   }

   public static class Builder extends BaseHttpApiMetadata.Builder<VCloudDirectorApi, Builder> {

      protected Builder() {
         id("vcloud-director")
                 .name("vCloud Director 1.5 API")
                 .identityName("User at Organization (user@org)")
                 .credentialName("Password")
                 .documentation(URI.create("http://www.vmware.com/support/pubs/vcd_pubs.html"))
                 .version("1.5")
                 .defaultProperties(VCloudDirectorApiMetadata.defaultProperties())
                 .view(typeToken(ComputeServiceContext.class))
                 .defaultModules(ImmutableSet.<Class<? extends Module>>of(
                         VCloudDirectorHttpApiModule.class,
                         OkHttpCommandExecutorServiceVcloudModule.class,
                         VCloudDirectorComputeServiceContextModule.class));
      }

      @Override
      public VCloudDirectorApiMetadata build() {
         return new VCloudDirectorApiMetadata(this);
      }

      @Override
      protected Builder self() {
         return this;
      }
   }
}
