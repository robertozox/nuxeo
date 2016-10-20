/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ecm.core.event.pipe.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.pipe.dispatch.SimpleEventBundlePipeDispatcher;

import java.util.concurrent.atomic.AtomicInteger;

public class SimplePostCommitEventListener implements PostCommitEventListener {

    private static final Log log = LogFactory.getLog(SimpleEventBundlePipeDispatcher.class);

    private static AtomicInteger mReceivedEvents = new AtomicInteger();

    @Override
    public void handleEvent(EventBundle eventBundle) {
        for (Event ignored : eventBundle) {
            mReceivedEvents.incrementAndGet();
        }
    }

    public static Integer getReceivedEvents() {
        return mReceivedEvents.get();
    }
}
