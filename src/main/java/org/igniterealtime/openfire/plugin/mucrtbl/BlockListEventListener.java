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

import java.util.Set;

/**
 * A listener of events that relate to adding or removing entries on a block list.
 *
 * Note that the hashes that are provided as the arguments to the event listeners are expected to be of JIDs that are
 * either bare, or consist of only a domain-part. Implementations should check both the bare JID (e.g. user@example.com)
 * and the domain (e.g. example.com) against the list, to support domain-wide blocks of rogue servers. JIDs should
 * always be normalized before comparison or hashing.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public interface BlockListEventListener
{
    /**
     * Invoked when hashes are added to the block list.
     *
     * @param hashes Hashes that have been added to the block list
     */
    void added(final Set<String> hashes);

    /**
     * Invoked when hashes are removed from the block list.
     *
     * @param hashes Hashes that have been added to the block list
     */
    void removed(final Set<String> hashes);
}
