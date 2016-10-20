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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import org.jclouds.vcloud.director.v1_5.domain.RasdItemsList;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.RasdItem;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.cim.ResourceAllocationSettingData;

import javax.inject.Singleton;

/**
 * Creates the next unique HDD RasdItem from RasdItemList.
 */
@Singleton
public final class NextHDD implements Function<RasdItemsList, RasdItem> {
    static final Ordering<RasdItem> BY_ADDRESS_ON_PARENT_ORDERING = new Ordering<RasdItem>() {
        public int compare(RasdItem left, RasdItem right) {
            if (left.getAddressOnParent() == null) {
                return -1;
            }
            if (right.getAddressOnParent() == null) {
                return 1;
            }
            Integer leftParent = Integer.parseInt(left.getAddressOnParent());
            Integer rightParent = Integer.parseInt(right.getAddressOnParent());
            return leftParent.compareTo(rightParent);
        }
    };
   @Override
   public RasdItem apply(RasdItemsList disks) {
       Iterable<RasdItem> existingDisks = Iterables.filter(disks, new Predicate<RasdItem>() {
           @Override public boolean apply(RasdItem rasdItem) {
               return ResourceAllocationSettingData.ResourceType.DISK_DRIVE.equals(rasdItem.getResourceType());
           }
       });
       Preconditions.checkArgument(existingDisks.iterator().hasNext(), "Adding new disk is implemented only for machines which have at least one disk.");
       RasdItem lastDisk = BY_ADDRESS_ON_PARENT_ORDERING.max(existingDisks);
       Integer addressOnParent = Integer.parseInt(lastDisk.getAddressOnParent()) + 1;
       Integer instanceId = Integer.parseInt(lastDisk.getInstanceID()) + 1;

       return RasdItem.builder()
               .fromRasdItem(lastDisk) // The same AddressOnParent (SCSI Controller)
               // and Description
               // and ResourceType
               // and HostResource
               // and not needed parent
               .addressOnParent("" + addressOnParent)
               .instanceID("" + instanceId)
//               .hostResource(hostResource)
               .elementName("Hard Disk " + (addressOnParent + 1))
               .build();
   }
}
