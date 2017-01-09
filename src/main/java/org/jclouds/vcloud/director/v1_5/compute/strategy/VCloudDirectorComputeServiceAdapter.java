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
package org.jclouds.vcloud.director.v1_5.compute.strategy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.find;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.jclouds.util.Predicates2.retry;
import static org.jclouds.vcloud.director.v1_5.VCloudDirectorMediaType.VDC;
import static org.jclouds.vcloud.director.v1_5.compute.util.VCloudDirectorComputeUtils.name;
import static org.jclouds.vcloud.director.v1_5.compute.util.VCloudDirectorComputeUtils.tryFindNetworkInVDCWithFenceMode;

import java.math.BigInteger;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.namespace.QName;

import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.compute.reference.ComputeServiceConstants.Timeouts;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.logging.Logger;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.util.Predicates2;
import org.jclouds.vcloud.director.v1_5.VCloudDirectorApi;
import org.jclouds.vcloud.director.v1_5.VCloudDirectorException;
import org.jclouds.vcloud.director.v1_5.compute.options.VCloudDirectorTemplateOptions;
import org.jclouds.vcloud.director.v1_5.compute.util.VCloudDirectorComputeUtils;
import org.jclouds.vcloud.director.v1_5.domain.Link;
import org.jclouds.vcloud.director.v1_5.domain.Reference;
import org.jclouds.vcloud.director.v1_5.domain.ResourceEntity;
import org.jclouds.vcloud.director.v1_5.domain.Session;
import org.jclouds.vcloud.director.v1_5.domain.Task;
import org.jclouds.vcloud.director.v1_5.domain.VApp;
import org.jclouds.vcloud.director.v1_5.domain.VAppChildren;
import org.jclouds.vcloud.director.v1_5.domain.VAppTemplate;
import org.jclouds.vcloud.director.v1_5.domain.Vdc;
import org.jclouds.vcloud.director.v1_5.domain.Vm;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.cim.CimString;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.cim.ResourceAllocationSettingData;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.ovf.MsgType;
import org.jclouds.vcloud.director.v1_5.domain.network.Network;
import org.jclouds.vcloud.director.v1_5.domain.network.NetworkAssignment;
import org.jclouds.vcloud.director.v1_5.domain.network.NetworkConfiguration;
import org.jclouds.vcloud.director.v1_5.domain.network.NetworkConnection;
import org.jclouds.vcloud.director.v1_5.domain.network.VAppNetworkConfiguration;
import org.jclouds.vcloud.director.v1_5.domain.org.Org;
import org.jclouds.vcloud.director.v1_5.domain.params.ComposeVAppParams;
import org.jclouds.vcloud.director.v1_5.domain.params.DeployVAppParams;
import org.jclouds.vcloud.director.v1_5.domain.params.InstantiationParams;
import org.jclouds.vcloud.director.v1_5.domain.params.SourcedCompositionItemParam;
import org.jclouds.vcloud.director.v1_5.domain.params.UndeployVAppParams;
import org.jclouds.vcloud.director.v1_5.domain.query.QueryResultRecordType;
import org.jclouds.vcloud.director.v1_5.domain.query.QueryResultRecords;
import org.jclouds.vcloud.director.v1_5.domain.query.QueryResultVAppTemplateRecord;
import org.jclouds.vcloud.director.v1_5.domain.section.GuestCustomizationSection;
import org.jclouds.vcloud.director.v1_5.domain.section.NetworkConfigSection;
import org.jclouds.vcloud.director.v1_5.domain.section.NetworkConnectionSection;
import org.jclouds.vcloud.director.v1_5.domain.section.VirtualHardwareSection;
import org.jclouds.vcloud.director.v1_5.predicates.ReferencePredicates;
import org.jclouds.vcloud.director.v1_5.predicates.TaskSuccess;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * defines the connection between the {@link VCloudDirectorApi} implementation and
 * the jclouds {@link org.jclouds.compute.ComputeService}
 */
@Singleton
public class VCloudDirectorComputeServiceAdapter implements
        ComputeServiceAdapter<Vm, Hardware, QueryResultVAppTemplateRecord, Vdc> {

   @Resource
   @Named(ComputeServiceConstants.COMPUTE_LOGGER)
   protected Logger logger = Logger.NULL;

   private final VCloudDirectorApi api;
   private final Supplier<Set<Hardware>> hardwareProfileSupplier;
   private final Timeouts timeouts;

   @Inject
   public VCloudDirectorComputeServiceAdapter(VCloudDirectorApi api, Supplier<Set<Hardware>> hardwareProfileSupplier, Timeouts timeouts) {
      this.api = checkNotNull(api, "api");
      this.hardwareProfileSupplier = hardwareProfileSupplier;
      this.timeouts = timeouts;
   }

   protected boolean waitForTask(Task task, long timeoutMillis) {
      return retry(new TaskSuccess(api.getTaskApi()), timeoutMillis,
              Predicates2.DEFAULT_PERIOD * 5, Predicates2.DEFAULT_MAX_PERIOD * 5, MILLISECONDS).apply(task);
   }
   
   @Override
   public NodeAndInitialCredentials<Vm> createNodeWithGroupEncodedIntoName(String group, String name, Template template) {
      checkNotNull(template, "template was null");
      checkNotNull(template.getOptions(), "template options was null");

      String imageId = checkNotNull(template.getImage().getId(), "template image id must not be null");
      String locationId = checkNotNull(template.getLocation().getId(), "template location id must not be null");
      final String hardwareId = checkNotNull(template.getHardware().getId(), "template image id must not be null");

      VCloudDirectorTemplateOptions templateOptions = VCloudDirectorTemplateOptions.class.cast(template.getOptions());

      Vdc vdc = getVdc(locationId);

      final Reference networkReference;

      if (template.getOptions().getNetworks().isEmpty()) {
         Org org = getOrgForSession();
         Network.FenceMode fenceMode = Network.FenceMode.NAT_ROUTED;
         Optional<Network> optionalNetwork = tryFindNetworkInVDCWithFenceMode(api, vdc, fenceMode);
         if (!optionalNetwork.isPresent()) {
            throw new IllegalStateException("Can't find a network with fence mode: " + fenceMode + "in org " + org.getFullName());
         }
         networkReference = Reference.builder().href(optionalNetwork.get().getHref()).name(optionalNetwork.get().getName()).build();
      } else {
         String networkName = Iterables.getOnlyElement(template.getOptions().getNetworks());
         networkReference = tryFindNetworkInVDC(vdc, networkName);
      }

      VAppTemplate vAppTemplate = api.getVAppTemplateApi().get(imageId);
      Set<Vm> vms = getAvailableVMsFromVAppTemplate(vAppTemplate);
      // TODO now get the first vm to be added to vApp, what if more?
      Vm toAddVm = Iterables.get(vms, 0);

      // customize toAddVm
      GuestCustomizationSection guestCustomizationSection = api.getVmApi().getGuestCustomizationSection(toAddVm.getHref());
      guestCustomizationSection = guestCustomizationSection.toBuilder()
              .resetPasswordRequired(false)
              .build();

      Statement guestCustomizationScript = ((VCloudDirectorTemplateOptions)(template.getOptions())).getGuestCustomizationScript();
      if (guestCustomizationScript != null) {
         guestCustomizationSection = guestCustomizationSection.toBuilder()
                 // TODO differentiate on guestOS
                 .customizationScript(guestCustomizationScript.render(OsFamily.WINDOWS)).build();
      }

      SourcedCompositionItemParam vmItem = createVmItem(toAddVm, networkReference.getName(), guestCustomizationSection);
      ComposeVAppParams compositionParams = ComposeVAppParams.builder()
              .name(name)
              .instantiationParams(instantiationParams(vdc, networkReference))
              .sourcedItems(ImmutableList.of(vmItem))
              .build();
      VApp vApp = api.getVdcApi().composeVApp(vdc.getId(), compositionParams);
      Task compositionTask = Iterables.getFirst(vApp.getTasks(), null);

      logger.debug(">> awaiting vApp(%s) deployment", vApp.getId());
      boolean vAppDeployed = waitForTask(compositionTask, timeouts.nodeRunning);
      logger.trace("<< vApp(%s) deployment completed(%s)", vApp.getId(), vAppDeployed);
      if (!vAppDeployed) {
         // TODO Destroy node? But don't have VM id yet.
         final String message = format("vApp(%s, %s) not composed within %d ms (task %s).", 
                  name, vApp.getId(), timeouts.nodeRunning, compositionTask.getHref());
         logger.warn(message);
         throw new IllegalStateException(message);
      }

      if (!vApp.getTasks().isEmpty()) {
         for (Task task : vApp.getTasks()) {
            logger.debug(">> awaiting vApp(%s) composition", vApp.getId());
            boolean vAppReady = waitForTask(task, timeouts.nodeRunning);
            logger.trace("<< vApp(%s) composition completed(%s)", vApp.getId(), vAppReady);
            if (!vAppReady) {
               // TODO Destroy node? But don't have VM id yet.
               final String message = format("vApp(%s, %s) post-compose not ready within %d ms (task %s).", 
                        name, vApp.getId(), timeouts.nodeRunning, task.getHref());
               logger.warn(message);
               throw new IllegalStateException(message);
            }
         }
      }
      URI vAppHref = checkNotNull(vApp.getHref(), format("vApp %s must not have a null href", vApp.getId()));
      VApp composedVApp = api.getVAppApi().get(vAppHref);
      VAppChildren children = checkNotNull(composedVApp.getChildren(), format("composedVApp %s must not have null children", composedVApp.getId()));
      Vm vm = Iterables.getOnlyElement(children.getVms());

      if (!vm.getTasks().isEmpty()) {
         for (Task task : vm.getTasks()) {
            logger.debug(">> awaiting vm(%s) deployment", vApp.getId());
            boolean vmReady = waitForTask(task, timeouts.nodeRunning);
            logger.trace("<< vApp(%s) deployment completed(%s)", vApp.getId(), vmReady);
            if (!vmReady) {
               final String message = format("vApp(%s, %s) post-compose VM(%s) not ready within %d ms (task %s), so it will be destroyed", 
                        name, vApp.getId(), vm.getHref(), timeouts.nodeRunning, task.getHref());
               logger.warn(message);
               destroyNode(vm.getId());
               throw new IllegalStateException(message);
            }
         }
      }

      // Configure VirtualHardware on a VM
      Optional<Hardware> hardwareOptional = Iterables.tryFind(listHardwareProfiles(), new Predicate<Hardware>() {
         @Override
         public boolean apply(Hardware input) {
            return input.getId().equals(hardwareId);
         }
      });

      // virtualCpus and memory templateOptions get the precedence over the default values given by hardwareId
      Integer virtualCpus = templateOptions.getVirtualCpus() == null ? getCoresFromHardware(hardwareOptional) : templateOptions.getVirtualCpus();
      Integer ram = templateOptions.getMemory() == null ? getRamFromHardware(hardwareOptional) : templateOptions.getMemory();
      Integer disk = templateOptions.getDisk();

      if (virtualCpus == null || ram == null) {
         String msg;
         if (hardwareOptional.isPresent()) {
            msg = format("vCPUs and RAM stats not available in hardware %s, and not configured in template options", hardwareId);
         } else {
            msg = format("vCPUs and RAM stats not available - no hardware matching id %s, and not configured in template options; destroying VM", hardwareId);
         }
         logger.error(msg + "; destroying VM and failing");
         destroyNode(vm.getId());
         throw new IllegalStateException(msg);
      }

      VirtualHardwareSection virtualHardwareSection = api.getVmApi().getVirtualHardwareSection(vm.getHref());

      Predicate<ResourceAllocationSettingData> processorPredicate = resourceTypeEquals(ResourceAllocationSettingData.ResourceType.PROCESSOR);
      virtualHardwareSection = updateVirtualHardwareSection(virtualHardwareSection, processorPredicate, virtualCpus + " virtual CPU(s)", BigInteger.valueOf(virtualCpus.intValue()));
      Predicate<ResourceAllocationSettingData> memoryPredicate = resourceTypeEquals(ResourceAllocationSettingData.ResourceType.MEMORY);
      virtualHardwareSection = updateVirtualHardwareSection(virtualHardwareSection, memoryPredicate, ram + " MB of memory", BigInteger.valueOf(ram.intValue()));
      if (disk != null) {
         Predicate<ResourceAllocationSettingData> diskPredicate = resourceTypeEquals(ResourceAllocationSettingData.ResourceType.DISK_DRIVE);
         Predicate<ResourceAllocationSettingData> elementPredicate = elementNameEquals("Hard disk 1");
         virtualHardwareSection = updateVirtualHardwareSectionDisk(virtualHardwareSection, Predicates.and(diskPredicate, elementPredicate), BigInteger.valueOf(disk.intValue()));
      }
      // NOTE this is not efficient but the vCD API v1.5 don't support editing hardware sections during provisioning
      Task editVirtualHardwareSectionTask = api.getVmApi().editVirtualHardwareSection(vm.getHref(), virtualHardwareSection);
      logger.debug(">> awaiting vm(%s) to be edited", vm.getId());
      boolean vmEdited = waitForTask(editVirtualHardwareSectionTask, timeouts.nodeRunning);
      logger.trace("<< vApp(%s) to be edited completed(%s)", vm.getId(), vmEdited);
      if (!vmEdited) {
         final String message = format("vApp(%s, %s) VM(%s) edit not completed within %d ms (task %s); destroying VM", 
                  name, vApp.getId(), vm.getHref(), timeouts.nodeRunning, editVirtualHardwareSectionTask.getHref());
         logger.warn(message);
         destroyNode(vm.getId());
         throw new IllegalStateException(message);
      }

      Task deployTask = api.getVAppApi().deploy(vApp.getHref(), DeployVAppParams.builder()
              .powerOn()
              .build());
      logger.debug(">> awaiting vApp(%s) to be powered on", vApp.getId());
      boolean vAppPoweredOn = waitForTask(deployTask, timeouts.nodeRunning);
      logger.trace("<< vApp(%s) to be powered on completed(%s)", vApp.getId(), vAppPoweredOn);
      if (!vAppPoweredOn) {
         final String message = format("vApp(%s, %s) power-on not completed within %d ms (task %s); destroying VM", 
                  name, vApp.getId(), timeouts.nodeRunning, deployTask.getHref());
         logger.warn(message);
         destroyNode(vm.getId());
         throw new IllegalStateException(message);
      }

      // Reload the VM; the act of "deploy" can change things like the password in the guest customization
      composedVApp = api.getVAppApi().get(vAppHref);
      children = checkNotNull(composedVApp.getChildren(), format("composedVApp %s must not have null children", composedVApp.getId()));
      vm = Iterables.getOnlyElement(children.getVms());

      // Infer the login credentials from the VM, defaulting to "root" user
      LoginCredentials defaultCredentials = VCloudDirectorComputeUtils.getCredentialsFrom(vm);
      LoginCredentials.Builder credsBuilder;
      if (defaultCredentials == null) {
         credsBuilder = LoginCredentials.builder().user("root");
      } else {
         credsBuilder = defaultCredentials.toBuilder();
         if (defaultCredentials.getUser() == null) {
            credsBuilder.user("root");
         }
      }
      // If login overrides are supplied in TemplateOptions, always prefer those.
      String overriddenLoginUser = template.getOptions().getLoginUser();
      String overriddenLoginPassword = template.getOptions().getLoginPassword();
      String overriddenLoginPrivateKey = template.getOptions().getLoginPrivateKey();
      if (overriddenLoginUser != null) {
         credsBuilder.user(overriddenLoginUser);
      }
      if (overriddenLoginPassword != null) {
         credsBuilder.password(overriddenLoginPassword);
      }
      if (overriddenLoginPrivateKey != null) {
         credsBuilder.privateKey(overriddenLoginPrivateKey);
      }
      return new NodeAndInitialCredentials<Vm>(vm, vm.getId(), credsBuilder.build());
   }

   private VirtualHardwareSection updateVirtualHardwareSection(VirtualHardwareSection virtualHardwareSection, 
            Predicate<ResourceAllocationSettingData> predicate, final String elementName, final BigInteger virtualQuantity) {
      return updateVirtualHardwareSection(virtualHardwareSection, predicate, new Function<ResourceAllocationSettingData, ResourceAllocationSettingData>() {
         @Override
         public ResourceAllocationSettingData apply(ResourceAllocationSettingData input) {
            return input.toBuilder().elementName(elementName).virtualQuantity(virtualQuantity).build();
         }
         @Override
         public String toString() {
            return elementName + " = " + virtualQuantity;
         }
      });
   }

   private VirtualHardwareSection updateVirtualHardwareSectionDisk(VirtualHardwareSection virtualHardwareSection, 
            Predicate<ResourceAllocationSettingData> predicate, final BigInteger capacity) {
      return updateVirtualHardwareSection(virtualHardwareSection, predicate, new Function<ResourceAllocationSettingData, ResourceAllocationSettingData>() {
         @Override
         public ResourceAllocationSettingData apply(ResourceAllocationSettingData input) {
            Set<CimString> oldHostResources = input.getHostResources();
            CimString oldHostResource = (oldHostResources != null) ? Iterables.getFirst(oldHostResources, null) : null;
            if (oldHostResource != null) {
               boolean overriddenCapacity = false;
               Map<QName, String> oldHostResourceAttribs = oldHostResource.getOtherAttributes();
               Map<QName, String> newHostResourceAttribs = Maps.newLinkedHashMap();
               for (Map.Entry<QName, String> entry : oldHostResourceAttribs.entrySet()) {
                  QName key = entry.getKey();
                  String val = entry.getValue();
                  if ("capacity".equals(key.getLocalPart())) {
                     val = capacity.toString();
                     overriddenCapacity = true;
                  }
                  newHostResourceAttribs.put(key, val);
               }
               if (overriddenCapacity) {
                  CimString newHostResource = new CimString(oldHostResource.getValue(), newHostResourceAttribs);
                  Iterable<CimString> newHostResources = Iterables.concat(ImmutableList.of(newHostResource), Iterables.skip(oldHostResources, 1));
                  return input.toBuilder().hostResources(newHostResources).build();
               } else {
                  logger.warn("Unable to find capacity in Host Resource for disk %s in hardware section; cannot resize disk to %s", input, capacity);
               }
            } else {
               logger.warn("Unable to find Host Resource for disk %s in hardware section; cannot resize disk to %s", input, capacity);
            }
            return input;
         }
         @Override
         public String toString() {
            return "disk = " + capacity;
         }
      });
   }

   private VirtualHardwareSection updateVirtualHardwareSection(VirtualHardwareSection virtualHardwareSection, Predicate<ResourceAllocationSettingData>
            predicate, Function<ResourceAllocationSettingData, ResourceAllocationSettingData> modifier) {
      Set<? extends ResourceAllocationSettingData> oldItems = virtualHardwareSection.getItems();
      Set<ResourceAllocationSettingData> newItems = Sets.<ResourceAllocationSettingData>newLinkedHashSet(oldItems);
      Optional<? extends ResourceAllocationSettingData> oldResourceAllocationSettingData = Iterables.tryFind(oldItems, predicate);
      if (oldResourceAllocationSettingData.isPresent()) {
         ResourceAllocationSettingData newResourceAllocationSettingData = modifier.apply(oldResourceAllocationSettingData.get());
         newItems.remove(oldResourceAllocationSettingData.get());
         newItems.add(newResourceAllocationSettingData);
         return virtualHardwareSection.toBuilder().items(newItems).build();
      } else {
         logger.warn("Unable to find hardware section matching %s; cannot apply %s", predicate, modifier);
         return virtualHardwareSection;
      }
   }

   private Predicate<ResourceAllocationSettingData> resourceTypeEquals(final ResourceAllocationSettingData.ResourceType resourceType) {
      return new Predicate<ResourceAllocationSettingData>() {
         @Override
         public boolean apply(ResourceAllocationSettingData rasd) {
            return rasd.getResourceType() == resourceType;
         }
         @Override
         public String toString() {
            return "resourceTypeEquals(" + resourceType + ")";
         }
      };
   }

   private Predicate<ResourceAllocationSettingData> elementNameEquals(final String name) {
      return new Predicate<ResourceAllocationSettingData>() {
         @Override
         public boolean apply(ResourceAllocationSettingData rasd) {
            return Objects.equal(rasd.getElementName(), name);
         }
         @Override
         public String toString() {
            return "elementNameEquals(" + name + ")";
         }
      };
   }

   private Integer getCoresFromHardware(Optional<Hardware> hardwareOptional) {
      if (!hardwareOptional.isPresent()) return null;
      List<? extends Processor> processors = hardwareOptional.get().getProcessors();
      if (processors == null) return null;
      return Integer.valueOf((int) Iterables.getOnlyElement(processors).getCores());
   }

   private Integer getRamFromHardware(Optional<Hardware> hardwareOptional) {
      if (!hardwareOptional.isPresent()) return null;
      return hardwareOptional.get().getRam();
   }

   private Reference tryFindNetworkInVDC(Vdc vdc, String networkName) {
      Optional<Reference> referenceOptional = Iterables.tryFind(vdc.getAvailableNetworks(), ReferencePredicates.nameEquals(networkName));
      if (!referenceOptional.isPresent()) {
         throw new IllegalStateException("Can't find a network named: " + networkName + "in vDC " + vdc.getName());
      }
      return referenceOptional.get();
   }

   public SourcedCompositionItemParam createVmItem(Vm vm, String networkName, GuestCustomizationSection guestCustomizationSection) {
      // creating an item element. this item will contain the vm which should be added to the vapp.
      final String name = name("vm-");
      Reference reference = Reference.builder().name(name).href(vm.getHref()).type(vm.getType()).build();

      InstantiationParams vmInstantiationParams;
      Set<NetworkAssignment> networkAssignments = Sets.newLinkedHashSet();

      NetworkConnection networkConnection = NetworkConnection.builder()
              .network(networkName)
              .ipAddressAllocationMode(NetworkConnection.IpAddressAllocationMode.POOL)
              .isConnected(true)
              .build();

      NetworkConnectionSection networkConnectionSection = NetworkConnectionSection.builder()
              .info(MsgType.builder().value("networkInfo").build())
              .primaryNetworkConnectionIndex(0).networkConnection(networkConnection).build();

      vmInstantiationParams = InstantiationParams.builder()
              .sections(ImmutableSet.of(networkConnectionSection, guestCustomizationSection))
              .build();

      SourcedCompositionItemParam.Builder vmItemBuilder = SourcedCompositionItemParam.builder().source(reference);

      if (vmInstantiationParams != null)
         vmItemBuilder.instantiationParams(vmInstantiationParams);

      if (networkAssignments != null)
         vmItemBuilder.networkAssignment(networkAssignments);

      return vmItemBuilder.build();
   }

   protected InstantiationParams instantiationParams(Vdc vdc, Reference network) {
      NetworkConfiguration networkConfiguration = networkConfiguration(vdc, network);

      InstantiationParams instantiationParams = InstantiationParams.builder()
              .sections(ImmutableSet.of(
                      networkConfigSection(network.getName(), networkConfiguration))
              )
              .build();

      return instantiationParams;
   }

   /** Build a {@link NetworkConfigSection} object. */
   private NetworkConfigSection networkConfigSection(String networkName, NetworkConfiguration networkConfiguration) {
      NetworkConfigSection networkConfigSection = NetworkConfigSection
              .builder()
              .info(MsgType.builder().value("Configuration parameters for logical networks").build())
              .networkConfigs(
                      ImmutableSet.of(VAppNetworkConfiguration.builder()
                              .networkName(networkName)
                              .configuration(networkConfiguration)
                              .build()))
              .build();

      return networkConfigSection;
   }

   private NetworkConfiguration networkConfiguration(Vdc vdc, final Reference network) {
      Set<Reference> networks = vdc.getAvailableNetworks();
      Optional<Reference> parentNetwork = Iterables.tryFind(networks, new Predicate<Reference>() {
         @Override
         public boolean apply(Reference reference) {
            return reference.getHref().equals(network.getHref());
         }
      });

      if (!parentNetwork.isPresent()) {
         throw new IllegalStateException("Cannot find a parent network: " + network.getName() + " given ");
      }
      return NetworkConfiguration.builder()
              .parentNetwork(parentNetwork.get())
              .fenceMode(Network.FenceMode.BRIDGED)
              .retainNetInfoAcrossDeployments(false)
              .build();
   }

   @Override
   public Iterable<Hardware> listHardwareProfiles() {
      return hardwareProfileSupplier.get();
   }

   @Override
   public Set<QueryResultVAppTemplateRecord> listImages() {
      return FluentIterable.from(getAllQueryResultRecords("vAppTemplate")).transform(new Function<QueryResultRecordType, QueryResultVAppTemplateRecord>() {
         @Override
         public QueryResultVAppTemplateRecord apply(QueryResultRecordType input) {
            return (QueryResultVAppTemplateRecord) input;
         }
      }).toSet();
   }

   @Override
   public QueryResultVAppTemplateRecord getImage(final String imageId) {
      return null;
   }

   @Override
   public Iterable<Vm> listNodes() {
      return FluentIterable.from(getAllQueryResultRecords("vApp"))
              .transform(new Function<QueryResultRecordType, VApp>() {
                 @Override
                 public VApp apply(QueryResultRecordType input) {
                    try {
                       return api.getVAppApi().get(input.getHref());
                    } catch (VCloudDirectorException e) {
                       // If the VApp is in an inconsistent state, the server will return at 500 error and an
                       // exception will be thrown, in which case we can only skip this VApp
                       logger.debug(String.format("Cannot get details for vApp %s, ignoring. Exception was: %s", input.getHref(), e));
                       return null;
                    }
                 }
              })
              .filter(Predicates.notNull())
              .filter(new Predicate<VApp>() {
                 @Override
                 public boolean apply(VApp input) {
                    return input.getTasks().isEmpty();
                 }
              })
              .transformAndConcat(new Function<VApp, Iterable<Vm>>() {
                 @Override
                 public Iterable<Vm> apply(VApp input) {
                    return input.getChildren() != null ? input.getChildren().getVms() : ImmutableList.<Vm>of();
                 }
              })
              // TODO we want also POWERED_OFF?
              .filter(new Predicate<Vm>() {
                 @Override
                 public boolean apply(Vm input) {
                    return input.getStatus() == ResourceEntity.Status.POWERED_ON;
                 }
              })
              .toSet();
   }

   @Override
   public Iterable<Vm> listNodesByIds(final Iterable<String> ids) {
      return null;
   }

   @Override
   public Iterable<Vdc> listLocations() {
      Org org = getOrgForSession();
      return FluentIterable.from(org.getLinks())
              .filter(ReferencePredicates.<Link>typeEquals(VDC))
              .transform(new Function<Link, Vdc>() {
                 @Override
                 public Vdc apply(Link input) {
                    return api.getVdcApi().get(input.getHref());
                 }
              }).toSet();
   }

   @Override
   public Vm getNode(String id) {
      return api.getVmApi().get(id);
   }

   @Override
   public void destroyNode(String id) {
      Vm vm = api.getVmApi().get(id);
      if (vm == null) {
         // See https://github.com/cloudsoft/jclouds-vcloud-director/issues/29
         // Happens if the VM has been deleted behind-our-back
         logger.info("Failed to destroy VM %s; not found (presumably already deleted?)", id);
      }
      URI vAppRef = VCloudDirectorComputeUtils.getVAppParent(vm);
      VApp vApp = api.getVAppApi().get(vAppRef);
      if (vApp == null) {
         // See https://github.com/cloudsoft/jclouds-vcloud-director/issues/29
         // Happens if the VM has been deleted behind-our-back (between us retrieving VM above, and retrieving vApp)!
         logger.info("Failed to destroy VM %s; vApp %s not found (presumably already deleted?)", id, vAppRef);
      }

      logger.debug("Deleting vApp(%s) that contains VM(%s) ...", vApp.getName(), vm.getName());
      if (!vApp.getTasks().isEmpty()) {
         for (Task task : vApp.getTasks()) {
            logger.debug(">> awaiting vApp(%s) tasks completion", vApp.getId());
            boolean vAppDeployed = waitForTask(task, timeouts.nodeTerminated);
            logger.trace("<< vApp(%s) tasks completions(%s)", vApp.getId(), vAppDeployed);
            if (!vAppDeployed) {
               final String message = format("vApp(%s) prior to destroy, pre-existing task not completed within %d ms (task %s); continuing.", 
                        vApp.getId(), timeouts.nodeTerminated, task.getHref());
               logger.warn(message);
            }
         }
      }
      UndeployVAppParams params = UndeployVAppParams.builder()
              .undeployPowerAction(UndeployVAppParams.PowerAction.POWER_OFF)
              .build();
      Task undeployTask = api.getVAppApi().undeploy(vAppRef, params);
      logger.debug(">> awaiting vApp(%s) undeploy completion", vApp.getId());
      boolean vAppUndeployed = waitForTask(undeployTask, timeouts.nodeTerminated);
      logger.trace("<< vApp(%s) undeploy completions(%s)", vApp.getId(), vAppUndeployed);
      if (!vAppUndeployed) {
         final String message = format("vApp(%s) undeploy not completed within %d ms (task %s); continuing", 
                  vApp.getId(), timeouts.nodeTerminated, undeployTask.getHref());
         throw new IllegalStateException(message);
      }

      Task removeTask = api.getVAppApi().remove(vAppRef);
      logger.debug(">> awaiting vApp(%s) remove completion", vApp.getId());
      boolean vAppRemoved = waitForTask(removeTask, timeouts.nodeTerminated);
      logger.trace("<< vApp(%s) remove completions(%s)", vApp.getId(), vAppRemoved);
      if (!vAppRemoved) {
         final String message = format("vApp(%s) removal not completed within %d ms (task %s).", 
                  vApp.getId(), timeouts.nodeTerminated, removeTask.getHref());
         logger.warn(message);
         throw new IllegalStateException(message);
      }
      
      logger.debug("vApp(%s) deleted", vApp.getName());
   }

   @Override
   public void rebootNode(String id) {
      api.getVmApi().reboot(id);
   }

   @Override
   public void resumeNode(String id) {
      throw new UnsupportedOperationException("resume not supported");
   }

   @Override
   public void suspendNode(String id) {
      api.getVmApi().suspend(id);
   }

   private Vdc getVdc(String locationId) {
      return api.getVdcApi().get(URI.create(locationId));
   }

   private Set<Vm> getAvailableVMsFromVAppTemplate(VAppTemplate vAppTemplate) {
      return ImmutableSet.copyOf(vAppTemplate.getChildren());
   }

   private Org getOrgForSession() {
      Session session = api.getCurrentSession();
      return api.getOrgApi().get(find(api.getOrgApi().list(), ReferencePredicates.nameEquals(session.get())).getHref());
   }


   private Set<QueryResultRecordType> getAllQueryResultRecords(String type) {
      QueryResultRecords queryResultRecords = api.getQueryApi().queryAll(type);
      Set<QueryResultRecordType> result = Sets.newHashSet(queryResultRecords.getRecords());
      QueryResultRecords currentRecords = queryResultRecords;
      Map<String, String> splittedQuery = getQueryMapFromRel(currentRecords, Link.Rel.LAST_PAGE);
      int lastPage = splittedQuery.isEmpty() ? 1 : Integer.valueOf(splittedQuery.get("page"));

      while (currentRecords.getPage() < lastPage) {
         for (Link link : currentRecords.getLinks()) {
            if (link.getRel() == Link.Rel.NEXT_PAGE) {
               splittedQuery = getQueryMapFromRel(currentRecords, Link.Rel.NEXT_PAGE);
               currentRecords = api.getQueryApi().query(type, splittedQuery.get("page"),
                       splittedQuery.get("pageSize"), splittedQuery.get("format"));
               result.addAll(currentRecords.getRecords());
               break;
            }
         }
      }
      return result;
   }

   private Map<String, String> getQueryMapFromRel(QueryResultRecords records, Link.Rel rel) {
      for (Link link : records.getLinks()) {
         if (link.getRel() == rel) {
            return Splitter.on("&")
                    .omitEmptyStrings()
                    .trimResults()
                    .withKeyValueSeparator("=")
                    .split(link.getHref().getQuery());
         }
      }
      return Maps.newHashMap();
   }


}
