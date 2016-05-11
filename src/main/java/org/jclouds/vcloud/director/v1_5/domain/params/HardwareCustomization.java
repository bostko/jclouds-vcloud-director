package org.jclouds.vcloud.director.v1_5.domain.params;

import org.jclouds.vcloud.director.v1_5.domain.Reference;
import org.jclouds.vcloud.director.v1_5.domain.params.Disk;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "HardwareCustomization")
public class HardwareCustomization {
    @XmlElement(name = "Disk", required = true)
    private Disk disk;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private HardwareCustomization hardwareCustomization;

        private Builder() {
            hardwareCustomization = new HardwareCustomization();
        }

        /**
         * @param diskSize in megabytes
         */
        public Builder disk(String instanceId, Integer diskSize) {
            hardwareCustomization.disk = Disk.builder().instanceId(instanceId).size(diskSize).build();
            return this;
        }

        public HardwareCustomization build() {
            return hardwareCustomization;
        }
    }
}
