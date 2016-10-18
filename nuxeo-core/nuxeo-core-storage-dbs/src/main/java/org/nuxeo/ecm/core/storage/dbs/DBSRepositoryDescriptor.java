/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.dbs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.ecm.core.storage.FulltextDescriptor;
import org.nuxeo.ecm.core.storage.FulltextDescriptor.FulltextIndexDescriptor;

/**
 * DBS Repository Descriptor.
 *
 * @since 7.10-HF04, 8.1
 */
public class DBSRepositoryDescriptor implements Cloneable {

    public DBSRepositoryDescriptor() {
    }

    @XNode("@name")
    public String name;

    @XNode("@label")
    public String label;

    @XNode("@isDefault")
    protected Boolean isDefault;

    public Boolean isDefault() {
        return isDefault;
    }

    @XNode("idType")
    public String idType; // "varchar", "uuid", "sequence"

    protected FulltextDescriptor fulltextDescriptor = new FulltextDescriptor();

    public FulltextDescriptor getFulltextDescriptor() {
        return fulltextDescriptor;
    }

    @XNode("fulltext@disabled")
    public void setFulltextDisabled(boolean disabled) {
        fulltextDescriptor.setFulltextDisabled(disabled);
    }

    @XNode("fulltext@searchDisabled")
    public void setFulltextSearchDisabled(boolean disabled) {
        fulltextDescriptor.setFulltextSearchDisabled(disabled);
    }

    @XNode("fulltext@parser")
    public void setFulltextParser(String fulltextParser) {
        fulltextDescriptor.setFulltextParser(fulltextParser);
    }

    @XNodeList(value = "fulltext/index", type = ArrayList.class, componentType = FulltextIndexDescriptor.class)
    public void setFulltextIndexes(List<FulltextIndexDescriptor> fulltextIndexes) {
        fulltextDescriptor.setFulltextIndexes(fulltextIndexes);
    }

    @XNodeList(value = "fulltext/excludedTypes/type", type = HashSet.class, componentType = String.class)
    public void setFulltextExcludedTypes(Set<String> fulltextExcludedTypes) {
        fulltextDescriptor.setFulltextExcludedTypes(fulltextExcludedTypes);
    }

    @XNodeList(value = "fulltext/includedTypes/type", type = HashSet.class, componentType = String.class)
    public void setFulltextIncludedTypes(Set<String> fulltextIncludedTypes) {
        fulltextDescriptor.setFulltextIncludedTypes(fulltextIncludedTypes);
    }

    @XNode("cache@enabled")
    private Boolean cacheEnabled;

    public boolean isCacheEnabled() {
        return defaultFalse(cacheEnabled);
    }

    protected void setCacheEnabled(boolean enabled) {
        cacheEnabled = Boolean.valueOf(enabled);
    }

    @XNode("cache@ttl")
    public Long cacheTtl;

    @XNode("cache@maxSize")
    public Long cacheMaxSize;

    @XNode("cache@concurrencyLevel")
    public Integer cacheConcurrencyLevel;

    @XNode("clustering@id")
    public String clusterNodeId;

    @XNode("clustering@enabled")
    private Boolean clusteringEnabled;

    @XNode("clustering/invalidatorClass")
    public Class<? extends ClusterInvalidator> clusterInvalidatorClass;

    public boolean isClusteringEnabled() {
        return defaultFalse(clusteringEnabled);
    }

    protected void setClusteringEnabled(boolean enabled) {
        clusteringEnabled = Boolean.valueOf(enabled);
    }

    @Override
    public DBSRepositoryDescriptor clone() {
        try {
            DBSRepositoryDescriptor clone = (DBSRepositoryDescriptor) super.clone();
            clone.fulltextDescriptor = new FulltextDescriptor(fulltextDescriptor);
            return clone;
        } catch (CloneNotSupportedException e) { // cannot happen
            throw new RuntimeException(e);
        }
    }

    public void merge(DBSRepositoryDescriptor other) {
        if (other.name != null) {
            name = other.name;
        }
        if (other.label != null) {
            label = other.label;
        }
        if (other.isDefault != null) {
            isDefault = other.isDefault;
        }
        if (other.idType != null) {
            idType = other.idType;
        }
        fulltextDescriptor.merge(other.fulltextDescriptor);
        if (other.cacheEnabled != null) {
            cacheEnabled = other.cacheEnabled;
        }
        if (other.cacheTtl != null) {
            cacheTtl = other.cacheTtl;
        }
        if (other.cacheMaxSize != null) {
            cacheMaxSize = other.cacheMaxSize;
        }
        if (other.cacheConcurrencyLevel != null) {
            cacheConcurrencyLevel = other.cacheConcurrencyLevel;
        }
        if (other.clusterNodeId != null) {
            clusterNodeId = other.clusterNodeId;
        }
        if (other.clusteringEnabled != null) {
            clusteringEnabled = other.clusteringEnabled;
        }
        if (other.clusterInvalidatorClass != null) {
            clusterInvalidatorClass = other.clusterInvalidatorClass;
        }
    }

    private static boolean defaultFalse(Boolean bool) {
        return Boolean.TRUE.equals(bool);
    }

}
