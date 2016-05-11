package org.jclouds.vcloud.director.v1_5.domain.params;

import org.jclouds.vcloud.director.v1_5.domain.Reference;
import org.jclouds.vcloud.director.v1_5.domain.section.GuestCustomizationSection;
import org.jclouds.vcloud.director.v1_5.domain.section.NetworkConfigSection;
import org.jclouds.vcloud.director.v1_5.domain.section.NetworkConnectionSection;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "SourcedVmInstantiationParams")
public class SourcedVmInstantiationParams {
    @XmlElement(name = "Source", required = true)
    protected Reference source;

    @XmlElement(name = "HardwareCustomization")
    protected HardwareCustomization hardwareCustomization;

    @XmlElement(name = "NetworkConfigSection")
    protected NetworkConfigSection networkConfigSection;

    @XmlElement(name = "GuestCustomizationSection")
    protected GuestCustomizationSection guestCustomizationSection;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SourcedVmInstantiationParams instantiationParams;

        public Builder source(Reference source) {
            instantiationParams.source = source;
            return this;
        }

        public Builder hardwareCustomization(HardwareCustomization hardwareCustomization) {
            instantiationParams.hardwareCustomization = hardwareCustomization;
            return this;
        }

        public Builder networkConfigSection(NetworkConfigSection networkConfigSection) {
            instantiationParams.networkConfigSection = networkConfigSection;
            return this;
        }

        public Builder guestCustomizationSection(GuestCustomizationSection guestCustomizationSection) {
            instantiationParams.guestCustomizationSection = guestCustomizationSection;
            return this;
        }

        private Builder() {
            instantiationParams = new SourcedVmInstantiationParams();
        }

        public SourcedVmInstantiationParams build() {
            return instantiationParams;
        }
    }
}
