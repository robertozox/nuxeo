/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.nuxeo.ecm.core.schema.types.CompositeTypeImpl;
import org.nuxeo.ecm.core.schema.types.Schema;

/**
 * Implementation of a document type.
 */
public class DocumentTypeImpl extends CompositeTypeImpl implements DocumentType {

    private static final long serialVersionUID = 1L;

    protected Set<String> facets;

    protected PrefetchInfo prefetchInfo;

    protected Set<String> subtypes;

    protected Set<String> blacklistedSubtypes;

    protected Set<String> allowedSubtypes;

    /**
     * Constructs a document type. Schemas and facets must include those from the super type.
     */
    public DocumentTypeImpl(String name, DocumentType superType, List<Schema> schemas, Collection<String> facets,
            PrefetchInfo prefetchInfo, Collection<String> subtypes, Collection<String> blacklistedSubtypes) {
        super(superType, SchemaNames.DOCTYPES, name, schemas);
        if (facets == null) {
            this.facets = Collections.emptySet();
        } else {
            this.facets = new HashSet<String>(facets);
        }
        this.prefetchInfo = prefetchInfo;
        if (subtypes == null) {
            this.subtypes = Collections.emptySet();
        } else {
            this.subtypes = new HashSet<>(subtypes);
        }
        if (blacklistedSubtypes == null) {
            this.blacklistedSubtypes = Collections.emptySet();
        } else {
            this.blacklistedSubtypes = new HashSet<>(blacklistedSubtypes);
        }
        allowedSubtypes = new HashSet<>(this.subtypes);
        allowedSubtypes.removeAll(this.blacklistedSubtypes);
    }

    public DocumentTypeImpl(String name) {
        this(name, null, Collections.<Schema> emptyList(), Collections.<String> emptySet(), null, null, null);
    }

    public void setPrefetchInfo(PrefetchInfo prefetchInfo) {
        this.prefetchInfo = prefetchInfo;
    }

    @Override
    public PrefetchInfo getPrefetchInfo() {
        return prefetchInfo;
    }

    @Override
    public boolean isFile() {
        return !facets.contains(FacetNames.FOLDERISH);
    }

    @Override
    public boolean isFolder() {
        return facets.contains(FacetNames.FOLDERISH);
    }

    @Override
    public boolean isOrdered() {
        return facets.contains(FacetNames.ORDERABLE);
    }

    @Override
    public Set<String> getFacets() {
        return facets;
    }

    @Override
    public boolean hasFacet(String facetName) {
        return facets.contains(facetName);
    }

    @Override
    public Set<String> getSubtypes() {
        return subtypes;
    }

    @Override
    public boolean hasSubtype(String subtype) {
        return subtype.contains(subtype);
    }

    @Override
    public Set<String> getBlacklistedSubtypes() {
        return blacklistedSubtypes;
    }

    @Override
    public boolean hasBlacklistedSubtype(String subtype) {
        return blacklistedSubtypes.contains(subtype);
    }

    @Override
    public Set<String> getAllowedSubtypes() {
        return allowedSubtypes;
    }

    @Override
    public boolean hasAllowedSubtype(String subtype) {
        return allowedSubtypes.contains(subtype);
    }

}
