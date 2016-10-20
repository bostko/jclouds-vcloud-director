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
package org.jclouds.vcloud.director.v1_5.functions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.jclouds.vcloud.director.v1_5.domain.RasdItemsList;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.RasdItem;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.cim.CimString;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.cim.ResourceAllocationSettingData;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.xml.namespace.QName;

@Test(groups = "unit")
public class NextHDDTest {
    @Test
    public void testCreatingNewHDD() {
        RasdItem scsiController = RasdItem.builder()
                .address("0")
                .description("SCSI Controller")
                .elementName("SCSI Controller 0")
                .instanceID("2")
                .resourceSubType("lsilogic")
                .resourceType(ResourceAllocationSettingData.ResourceType.PARALLEL_SCSI_HBA)
                .build();
        final CimString hostResource = new CimString("HostResource");
        Preconditions.checkNotNull(hostResource, "HostResource for the existing disk should not be null");
        hostResource.getOtherAttributes().put(new QName("http://www.vmware.com/vcloud/v1.5", "capacity"), "" + 1024000);

        RasdItem existingDisk = RasdItem.builder()
                .addressOnParent("0")
                .description("Hard disk")
                .description("Hard disk 1")
                .hostResources(ImmutableList.of(hostResource))
                .instanceID("2000")
                .parent(scsiController.getParent())
                .resourceType(ResourceAllocationSettingData.ResourceType.DISK_DRIVE)
                .build();
        RasdItemsList rasdItems = RasdItemsList.builder()
                .item(scsiController)
                .item(existingDisk)
                .build();

        RasdItem newDisk = new NextHDD().apply(rasdItems);
        final CimString newDiskHostResource = new CimString("HostResource");
        Preconditions.checkNotNull(newDiskHostResource, "HostResource for the existing disk should not be null");
        newDiskHostResource.getOtherAttributes().put(new QName("http://www.vmware.com/vcloud/v1.5", "capacity"), "" + 1024000);
        newDisk = RasdItem.builder().fromRasdItem(newDisk).hostResources(ImmutableList.of(newDiskHostResource)).build();

        Assert.assertTrue(newDisk.getHostResources().size() == 1);
        Assert.assertEquals(Iterables.getFirst(newDisk.getHostResources(), null).getOtherAttributes().get(new QName("http://www.vmware.com/vcloud/v1.5", "capacity")),
                "" + 1024000);
        Assert.assertEquals(newDisk.getAddressOnParent(), "1");
    }
}
