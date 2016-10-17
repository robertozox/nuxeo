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
 *     Kevin Leturc
 */
package org.nuxeo.ecm.core.storage.dbs;

import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_NAME;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PARENT_ID;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.model.LockManager;
import org.nuxeo.ecm.core.model.Session;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.storage.FulltextConfiguration;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.StateDiff;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

/**
 * The DBS Cache layer used to cache some method call of real repository
 *
 * @since 8.4
 */
public class DBSCachingRepository implements DBSRepository {

    // TODO make it configurable
    private static final long CACHE_TTL = 10;

    // TODO make it configurable
    private static final long CACHE_MAX_SIZE = 1000;

    // TODO make it configurable
    private static final int CACHE_CONCURRENCY_LEVEL = 10;

    private final DBSRepository repository;

    private final DBSRepositoryDescriptor repositoryDescriptor;

    private final Cache<String, State> cache;

    private final Cache<String, String> childCache;

    public DBSCachingRepository(DBSRepository repository, DBSRepositoryDescriptor repositoryDescriptor) {
        this.repository = repository;
        this.repositoryDescriptor = repositoryDescriptor;
        // Init caches
        cache = newCache();
        childCache = newCache();
    }

    protected <T> Cache<String, T> newCache() {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
        builder = builder.expireAfterWrite(CACHE_TTL, TimeUnit.MINUTES);
        // if (desc.options.containsKey("concurrencyLevel")) {
        builder = builder.concurrencyLevel(CACHE_CONCURRENCY_LEVEL);
        // }
        // if (desc.options.containsKey("maxSize")) {
        builder = builder.maximumSize(CACHE_MAX_SIZE);
        // }
        return builder.build();
    }

    @Override
    public State readState(String id) {
        State state = cache.getIfPresent(id);
        if (state == null) {
            state = repository.readState(id);
            if (state != null) {
                putInCache(state);
            }
        }
        return state;
    }

    @Override
    public List<State> readStates(List<String> ids) {
        ImmutableMap<String, State> statesMap = cache.getAllPresent(ids);
        List<String> idsToRetrieve = new ArrayList<>(ids);
        idsToRetrieve.removeAll(statesMap.keySet());
        // Read missing states from repository
        List<State> states = repository.readStates(idsToRetrieve);
        // Cache them
        states.forEach(this::putInCache);
        // Add previous cached one
        states.addAll(statesMap.values());
        // Sort them
        states.sort(Comparator.comparing(state -> state.get(KEY_ID).toString(), Ordering.explicit(ids)));
        return states;
    }

    @Override
    public void createState(State state) {
        repository.createState(state);
        // TODO check if it's relevant
        putInCache(state);
    }

    @Override
    public void createStates(List<State> states) {
        repository.createStates(states);
        // TODO check if it's relevant
        states.forEach(this::putInCache);
    }

    @Override
    public void updateState(String id, StateDiff diff) {
        repository.updateState(id, diff);
        // TODO check if we want to update the state into the cache
        cache.invalidate(id);
    }

    @Override
    public void deleteStates(Set<String> ids) {
        repository.deleteStates(ids);
        cache.invalidateAll(ids);
    }

    @Override
    public State readChildState(String parentId, String name, Set<String> ignored) {
        String childCacheKey = computeChildCacheKey(parentId, name);
        String stateId = childCache.getIfPresent(childCacheKey);
        if (stateId != null) {
            State state = cache.getIfPresent(stateId);
            if (state != null) {
                return state;
            }
        }
        State state = repository.readChildState(parentId, name, ignored);
        putInCache(state);
        return state;
    }

    private void putInCache(State state) {
        String stateId = state.get(KEY_ID).toString();
        Object stateParentId = state.get(KEY_PARENT_ID);
        cache.put(stateId, state);
        if (stateParentId != null) {
            childCache.put(computeChildCacheKey(stateParentId.toString(), state.get(KEY_NAME).toString()), stateId);
        }
    }

    private String computeChildCacheKey(String parentId, String name) {
        return parentId + '_' + name;
    }

    @Override
    public BlobManager getBlobManager() {
        return repository.getBlobManager();
    }

    @Override
    public FulltextConfiguration getFulltextConfiguration() {
        return repository.getFulltextConfiguration();
    }

    @Override
    public boolean isFulltextDisabled() {
        return repository.isFulltextDisabled();
    }

    @Override
    public String getRootId() {
        return repository.getRootId();
    }

    @Override
    public String generateNewId() {
        return repository.generateNewId();
    }

    @Override
    public boolean hasChild(String parentId, String name, Set<String> ignored) {
        return repository.hasChild(parentId, name, ignored);
    }

    @Override
    public List<State> queryKeyValue(String key, Object value, Set<String> ignored) {
        return repository.queryKeyValue(key, value, ignored);
    }

    @Override
    public List<State> queryKeyValue(String key1, Object value1, String key2, Object value2, Set<String> ignored) {
        return repository.queryKeyValue(key1, value1, key2, value2, ignored);
    }

    @Override
    public void queryKeyValueArray(String key, Object value, Set<String> ids, Map<String, String> proxyTargets,
            Map<String, Object[]> targetProxies) {
        repository.queryKeyValueArray(key, value, ids, proxyTargets, targetProxies);
    }

    @Override
    public boolean queryKeyValuePresence(String key, String value, Set<String> ignored) {
        return repository.queryKeyValuePresence(key, value, ignored);
    }

    @Override
    public PartialList<Map<String, Serializable>> queryAndFetch(DBSExpressionEvaluator evaluator,
            OrderByClause orderByClause, boolean distinctDocuments, int limit, int offset, int countUpTo) {
        return repository.queryAndFetch(evaluator, orderByClause, distinctDocuments, limit, offset, countUpTo);
    }

    @Override
    public LockManager getLockManager() {
        return repository.getLockManager();
    }

    @Override
    public ScrollResult scroll(DBSExpressionEvaluator evaluator, int batchSize, int keepAliveSeconds) {
        return repository.scroll(evaluator, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult scroll(String scrollId) {
        return repository.scroll(scrollId);
    }

    @Override
    public Lock getLock(String id) {
        return repository.getLock(id);
    }

    @Override
    public Lock setLock(String id, Lock lock) {
        return repository.setLock(id, lock);
    }

    @Override
    public Lock removeLock(String id, String owner) {
        return repository.removeLock(id, owner);
    }

    @Override
    public void closeLockManager() {
        repository.closeLockManager();
    }

    @Override
    public void clearLockManagerCaches() {
        repository.clearLockManagerCaches();
    }

    @Override
    public String getName() {
        return repository.getName();
    }

    @Override
    public Session getSession() {
        return repository.getSession();
    }

    @Override
    public void shutdown() {
        repository.shutdown();
        cache.invalidateAll();
        childCache.invalidateAll();
    }

    @Override
    public int getActiveSessionsCount() {
        return repository.getActiveSessionsCount();
    }

    @Override
    public void markReferencedBinaries() {
        repository.markReferencedBinaries();
    }

}
