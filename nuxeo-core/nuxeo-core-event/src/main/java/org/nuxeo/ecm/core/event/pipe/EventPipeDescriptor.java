/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     tiry
 */
package org.nuxeo.ecm.core.event.pipe;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;

import java.util.HashMap;
import java.util.Map;

/**
 * XMap Descriptor for contributing a new {@link EventBundlePipe}
 *
 * @since 8.4
 */
@XObject("eventPipe")
public class EventPipeDescriptor {

    public static final Log log = LogFactory.getLog(EventPipeDescriptor.class);

    @XNode("@name")
    protected String name;

    @XNode("@priority")
    protected Integer priority = 100;

    public EventPipeDescriptor() {
    }

    public EventPipeDescriptor(String name, Class<? extends EventBundlePipe> clazz) {
        this.name = name;
        this.clazz = clazz;
    }

    @XNodeMap(value = "parameters/parameter", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> parameters = new HashMap<>();

    /**
     * The implementation class.
     */
    @XNode("@class")
    protected Class<? extends EventBundlePipe> clazz;

    public String getName() {
        return name;
    }

    public Integer getPriority() {
        return priority;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public EventBundlePipe getInstance() {
        try {
            return clazz.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public void merge(EventPipeDescriptor other) {
        if (other.priority!=null) {
            priority = other.priority;
        }
        if (other.clazz!=null) {
            clazz = other.clazz;
        }
        parameters.putAll(other.getParameters());
    }

    @Override
    public EventPipeDescriptor clone() {
        EventPipeDescriptor copy = new EventPipeDescriptor(name, clazz);
        copy.priority=priority;
        copy.parameters = parameters;
        return copy;
    }
}
