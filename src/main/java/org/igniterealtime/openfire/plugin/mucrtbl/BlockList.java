/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
 */
package org.igniterealtime.openfire.plugin.mucrtbl;

import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

public class BlockList
{
    private static final Logger Log = LoggerFactory.getLogger(BlockList.class);

    private static final String CACHE_MUTEX = "mutex-for-blocklist-cache";

    /**
     * Collection of hashes of bare JIDs or domain-part-only JIDs that are blocked.
     */
    private final Cache<String, String> blockedHashes;

    private final Set<BlockListEventListener> eventListeners = new HashSet<>();

    public BlockList() {
        blockedHashes = CacheFactory.createCache("MUC RealTime Block List");
        blockedHashes.setMaxCacheSize(25L * 1024 * 1024);
        blockedHashes.setMaxLifetime(-1L);
    }

    /**
     * Checks if a JID is on the block list.
     *
     * This method will verify if the SHA-256 hash of the bare JID exists on the block list, returning 'true' when that
     * is the case.
     *
     * @param jid The JID for which to check the block list
     * @return true if the JID is on the block list, otherwise false.
     */
    public boolean contains(final JID jid) {
        final String bareJidHash = StringUtils.hash(jid.toBareJID(), "SHA-256");
        final String domainHash = StringUtils.hash(jid.getDomain(), "SHA-256");

        final Lock lock = blockedHashes.getLock(CACHE_MUTEX);
        try {
            lock.lock();
            return blockedHashes.containsKey(bareJidHash) || blockedHashes.containsKey(domainHash);
        } finally {
            lock.unlock();
        }
    }

    /**
     * From a collection of JIDs, return only those that are on the block list.
     *
     * This method will verify if the SHA-256 hash of the bare JID or the domain-part of the JID exists on the block
     * list, returning the original JID in the result when that is the case.
     *
     * @param jids The JIDs for which to check the block list
     * @return A collection with JIDs that are on the block list. Possibly empty, never null.
     */
    public Set<JID> filterBlocked(final Collection<JID> jids) {
        // First calculate all hashes, then check the cache. This is aimed to reduce the amount and total duration of
        // cache locks that are held.
        final ConcurrentMap<String, Set<JID>> hashes = new ConcurrentHashMap<>();
        for (final JID jid : jids) {
            final String bareJidHash = StringUtils.hash(jid.toBareJID(), "SHA-256");
            final String domainHash = StringUtils.hash(jid.getDomain(), "SHA-256");
            hashes.computeIfAbsent(bareJidHash, k -> new HashSet<>()).add(jid);
            hashes.computeIfAbsent(domainHash, k -> new HashSet<>()).add(jid);
        }

        final Set<JID> result = new HashSet<>();
        if (hashes.isEmpty()) {
            // No need to obtain a lock.
            return result;
        }

        final Lock lock = blockedHashes.getLock(CACHE_MUTEX);
        try {
            lock.lock();
            for (final Map.Entry<String, Set<JID>> entry : hashes.entrySet()) {
                if (blockedHashes.containsKey(entry.getKey())) {
                    result.addAll(entry.getValue());
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a collection of hashes of a JIDs to the block list, with an optional human-readable reason for why the entry
     * was added to the block list.
     *
     * Each of the hashes that is provided is expected to be of a normalized JID that is either a bare, or consist of only a
     * domain-part.
     *
     * @param hashes A map of hashes to be added, mapped to optional human-readible reasons for the hashes to be added.
     */
    public void addAll(final Map<String, String> hashes) {
        final Map<String, String> toAdd = new HashMap<>();
        for (final Map.Entry<String, String> hash : hashes.entrySet()) {
            if (hash.getKey() != null && hash.getKey().matches("^[a-fA-F0-9]{64}$")) {
                toAdd.put(hash.getKey(), hash.getValue());
            }
        }

        final Lock lock = blockedHashes.getLock(CACHE_MUTEX);
        try {
            lock.lock();
            blockedHashes.keySet().forEach(toAdd::remove);
            if (!toAdd.isEmpty()) {
                blockedHashes.putAll(toAdd);
            }
        } finally {
            lock.unlock();
        }

        if (!toAdd.isEmpty()) {
            // Invoke event listeners
            for (final BlockListEventListener listener : eventListeners) {
                try {
                    listener.added(toAdd.keySet());
                } catch (Throwable t) {
                    Log.warn("After adding entries to the block list, an event listener threw the following.", t);
                }
            }
        }
    }

    /**
     * Adds the hash of a JID to the block list, with an optional human-readable reason for why the entry was added to
     * the block list.
     *
     * The hash that is provided is expected to be of a normalized JID that is either a bare, or consist of only a
     * domain-part.
     *
     * @param hash A hash
     * @param reason An optional reason for the hash to exist on the block list.
     */
    public void add(final String hash, final String reason)
    {
        addAll(Collections.singletonMap(hash, reason));
    }

    public void removeAll(final Collection<String> hashes) {
        final Set<String> removed = new HashSet<>();
        final Lock lock = blockedHashes.getLock(CACHE_MUTEX);
        try {
            lock.lock();
            for (final String hash : hashes) {
                if (blockedHashes.containsKey(hash)) {
                    blockedHashes.remove(hash);
                    removed.add(hash);
                }
            }
        } finally {
            lock.unlock();
        }

        if (!removed.isEmpty()) {
            // Invoke event listeners
            for (final BlockListEventListener listener : eventListeners) {
                try {
                    listener.removed(removed);
                } catch (Throwable t) {
                    Log.warn("After removing entries to the block list, an event listener threw the following.", t);
                }
            }
        }
    }

    public void remove(final String hash)
    {
        removeAll(Collections.singletonList(hash));
    }

    /**
     * Gets a defensive copy of all hashes on the block list, mapped to an optional human reason for the entry to exist
     * on the block list.
     *
     * The hashes are expected to be those of normalized JIDs that are either bare, or consist of a domain-part only.
     * When comparing, JIDs should always be normalized.
     */
    public Map<String, String> getAll() {
        final Lock lock = blockedHashes.getLock(CACHE_MUTEX);
        try {
            lock.lock();
            return new HashMap<>(blockedHashes);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Registers an event listener that will be invoked when changes occur.
     *
     * @param listener The event listener to register.
     * @return true if this event listener was not already registered.
     */
    public boolean register(final BlockListEventListener listener) {
        return eventListeners.add(listener);
    }

    /**
     * Removes an event listener.
     *
     * @param listener The event listener to remove.
     * @return true if this event listener was registered.
     */
    public boolean unregister(final BlockListEventListener listener) {
        return eventListeners.remove(listener);
    }
}
