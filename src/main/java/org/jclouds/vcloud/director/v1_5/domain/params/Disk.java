package org.jclouds.vcloud.director.v1_5.domain.params;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Disk")
public class Disk {
    @XmlAttribute(name = "instanceId")
    private String instanceId;

    @XmlElement(name = "Size", required = true)
    private Integer size;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Disk disk;

        private Builder() {
            disk = new Disk();
        }

        public Builder instanceId(String instanceId) {
            disk.instanceId = instanceId;
            return this;
        }

        public Builder size(Integer size) {
            disk.size = size;
            return this;
        }

        public Disk build() {
            return disk;
        }
    }
}
