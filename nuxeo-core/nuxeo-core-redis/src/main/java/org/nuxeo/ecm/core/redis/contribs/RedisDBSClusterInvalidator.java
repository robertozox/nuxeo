/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Benoit Delbosc
 */
package org.nuxeo.ecm.core.redis.contribs;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.redis.RedisAdmin;
import org.nuxeo.ecm.core.redis.RedisExecutor;
import org.nuxeo.ecm.core.storage.dbs.ClusterInvalidator;
import org.nuxeo.ecm.core.storage.dbs.Invalidations;
import org.nuxeo.runtime.api.Framework;

import redis.clients.jedis.JedisPubSub;

/**
 * Redis implementation of {@link ClusterInvalidator}.
 *
 * Use a single channel pubsub to send invalidations.
 * Use an HSET to register nodes, only for debug purpose so far.
 *
 * @since 8.4
 */
public class RedisDBSClusterInvalidator implements ClusterInvalidator {

    protected static final String PREFIX = "inval";

    // PubSub channel: nuxeo:inval:<repositoryName>:channel
    protected static final String INVALIDATION_CHANNEL = "channel";

    // Node HSET key: nuxeo:inval:<repositoryName>:nodes:<nodeId>
    protected static final String CLUSTER_NODES_KEY = "nodes";

    // Keep info about a cluster node for one day
    protected static final int TIMEOUT_REGISTER_SECOND = 24 * 3600;

    // Max delay to wait for a channel subscription
    protected static final long TIMEOUT_SUBSCRIBE_SECOND = 10;

    protected static final String STARTED_FIELD = "started";

    protected static final String LAST_INVAL_FIELD = "lastInvalSent";

    protected String nodeId;

    protected String repositoryName;

    protected RedisExecutor redisExecutor;

    protected Invalidations receivedInvals;

    protected Thread subscriberThread;

    protected String namespace;

    private static final Log log = LogFactory.getLog(RedisDBSClusterInvalidator.class);

    private CountDownLatch subscribeLatch;

    private String registerSha;
    private String sendSha;

    @Override
    public void initialize(String nodeId, Repository repository) {
        this.nodeId = nodeId;
        this.repositoryName = repository.getName();
        redisExecutor = Framework.getLocalService(RedisExecutor.class);
        RedisAdmin redisAdmin = Framework.getService(RedisAdmin.class);
        namespace = redisAdmin.namespace(PREFIX, repositoryName);
        try {
            registerSha = redisAdmin.load("org.nuxeo.ecm.core.redis", "register-node-inval");
            sendSha = redisAdmin.load("org.nuxeo.ecm.core.redis", "send-inval");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        receivedInvals = new Invalidations();
        createSubscriberThread();
        registerNode();
    }

    protected void createSubscriberThread() {
        subscribeLatch = new CountDownLatch(1);
        String name = "RedisDBSClusterInvalidatorSubscriber:" + repositoryName + ":" + nodeId;
        subscriberThread = new Thread(this::subscribeToInvalidationChannel, name);
        subscriberThread.setUncaughtExceptionHandler((t, e) -> log.error("Uncaught error on thread " + t.getName(), e));
        subscriberThread.setPriority(Thread.NORM_PRIORITY);
        subscriberThread.start();
        try {
            if (!subscribeLatch.await(TIMEOUT_SUBSCRIBE_SECOND, TimeUnit.SECONDS)) {
                log.error("Redis channel subscripion timeout after " + TIMEOUT_SUBSCRIBE_SECOND +
                        "s, continuing but this node may not receive cluster invalidations");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    protected void subscribeToInvalidationChannel() {
        log.info("Subscribing to channel: " + getChannelName());
        redisExecutor.execute(jedis -> {
            jedis.subscribe(new JedisPubSub() {
                @Override
                public void onSubscribe(String channel, int subscribedChannels) {
                    super.onSubscribe(channel, subscribedChannels);
                    if (subscribeLatch != null) {
                        subscribeLatch.countDown();
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Subscribed to channel: " + getChannelName());
                    }
                }

                @Override
                public void onMessage(String channel, String message) {
                    try {
                        RedisDBSInvalidations rInvals = new RedisDBSInvalidations(nodeId, message);
                        if (log.isTraceEnabled()) {
                            log.trace("Receive invalidations: " + rInvals);
                        }
                        Invalidations invals = rInvals.getInvalidations();
                        synchronized (RedisDBSClusterInvalidator.this) {
                            receivedInvals.add(invals);
                        }
                    } catch (IllegalArgumentException e) {
                        log.error("Fail to read message: " + message, e);
                    }
                }
            }, getChannelName());
            return null;
        });
    }

    protected String getChannelName() {
        return namespace + INVALIDATION_CHANNEL;
    }

    protected void registerNode() {
        final String startedDateTime = getCurrentDateTime();
        final List<String> keys = Collections.singletonList(getNodeKey());
        final List<String> args = Arrays.asList(STARTED_FIELD, startedDateTime,
                Integer.valueOf(TIMEOUT_REGISTER_SECOND).toString());
        log.debug("Registering node: " + nodeId);

        redisExecutor.execute(jedis -> {
            jedis.evalsha(registerSha, keys, args);
            log.info("Node registered: " + nodeId);
            return null;
        });
    }

    protected String getNodeKey() {
        return namespace + CLUSTER_NODES_KEY + ":" + nodeId;
    }

    @Override
    public void close() {
        log.debug("Closing");
        unsubscribeToInvalidationChannel();
        // The Jedis pool is already closed when the repository is shutdowned
        receivedInvals.clear();
    }

    protected void unsubscribeToInvalidationChannel() {
        subscriberThread.interrupt();
    }


    @Override
    public Invalidations receiveInvalidations() {
        Invalidations newInvals = new Invalidations();
        Invalidations ret;
        synchronized (this) {
            ret = receivedInvals;
            receivedInvals = newInvals;
        }
        return ret;
    }

    @Override
    public void sendInvalidations(Invalidations invals) {
        final String startedDateTime = getCurrentDateTime();
        RedisDBSInvalidations rInvals = new RedisDBSInvalidations(nodeId, invals);
        if (log.isTraceEnabled()) {
            log.trace("Sending invalidations: " + rInvals);
        }
        final List<String> keys = Arrays.asList(getChannelName(), getNodeKey());
        final List<String> args;
        try {
            args = Arrays.asList(rInvals.serialize(), STARTED_FIELD, startedDateTime,
                    LAST_INVAL_FIELD, getCurrentDateTime(), Integer.valueOf(TIMEOUT_REGISTER_SECOND).toString());
        } catch (IOException e) {
            throw new NuxeoException(e);
        }

        redisExecutor.execute(jedis -> {
            jedis.evalsha(sendSha, keys, args);
            if (log.isTraceEnabled()) {
                log.trace("invals sent");
            }
            return null;
        });
    }

    protected String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }
}
