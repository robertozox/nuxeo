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
 *     Gabriel Barata <gbarata@nuxeo.com>
 */

package org.nuxeo.ecm.core.io.marshallers.json.enrichers;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import javax.inject.Inject;

/**
 * @since 8.4
 */
@LocalDeploy("org.nuxeo.ecm.core.io:OSGI-INF/doc-type-contrib.xml")
public class SubtypesJsonEnricherTest extends AbstractJsonWriterTest.Local<DocumentModelJsonWriter, DocumentModel> {

    public SubtypesJsonEnricherTest() {
        super(DocumentModelJsonWriter.class, DocumentModel.class);
    }

    @Inject
    private CoreSession session;

    @Before
    public void setup() {
        DocumentModel document = session.createDocumentModel("/", "doc1", "MyFolder");
        document = session.createDocument(document);
        document = session.createDocumentModel("/", "doc2", "MyFolder2");
        document = session.createDocument(document);
    }

    @Test
    public void test() throws Exception {
        DocumentModel doc1 = session.getDocument(new PathRef("/doc1"));
        JsonAssert json = jsonAssert(doc1, RenderingContext.CtxBuilder.enrichDoc("subtypes").get());
        json = json.has("contextParameters").isObject();
        json.properties(1);
        json = json.has("subtypes").isArray();
        json = json.length(3);
        json.childrenContains("id", "RefDoc", "File", "CSDoc");


        DocumentModel doc2 = session.getDocument(new PathRef("/doc2"));
        json = jsonAssert(doc2, RenderingContext.CtxBuilder.enrichDoc("subtypes").get());
        json = json.has("contextParameters").isObject();
        json.properties(1);
        json = json.has("subtypes").isArray();
        json = json.length(3);
        json.childrenContains("id", "DummyDoc", "File", "CSDoc");
    }

}