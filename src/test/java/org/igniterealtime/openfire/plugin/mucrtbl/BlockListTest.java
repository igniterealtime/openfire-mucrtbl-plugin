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

import org.jivesoftware.util.cache.CacheFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmpp.packet.JID;

import static org.junit.Assert.*;

/**
 * Unit tests that check the deadlock-detection functionality of {@link BlockList}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class BlockListTest
{
    @Before
    @After
    public void resetCache() throws Exception
    {
        CacheFactory.clearCaches("MUC RealTime Block List");
    }

    /**
     * Verifies that a JID isn't 'on the block list' if the list is empty.
     */
    @Test
    public void testTargetNotOnEmptyBlockList() throws Exception
    {
        // Setup test fixture.
        final BlockList bl = new BlockList();
        final JID target = new JID("unit-test@xmpp.org/resource");

        // Execute system under test
        final boolean result = bl.contains(target);

        // Verify results;
        assertFalse(result);
    }

    /**
     * Verifies that a JID isn't 'on the block list' if the list has content, not including the target.
     */
    @Test
    public void testTargetNotOnBlockList() throws Exception
    {
        // Setup test fixture.
        final BlockList bl = new BlockList();
        bl.add("60eb02d00ee3bdd0c46d9c6a360037882a9137902f3533a78fa73aae2ec9dbe2"); // unit-test-not-on-list@example.com
        final JID target = new JID("unit-test@xmpp.org/resource");

        // Execute system under test
        final boolean result = bl.contains(target);

        // Verify results;
        assertFalse(result);
    }

    /**
     * Verifies that a JID is detected 'on the block list' if the target is a bare JID matching a bare JID that is on the list.
     */
    @Test
    public void testTargetOnBlockList() throws Exception
    {
        // Setup test fixture.
        final BlockList bl = new BlockList();
        bl.add("bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"); // unit-test@xmpp.org
        final JID target = new JID("unit-test@xmpp.org");

        // Execute system under test
        final boolean result = bl.contains(target);

        // Verify results;
        assertTrue(result);
    }

    /**
     * Verifies that a JID is detected 'on the block list' if the target is a full JID matching a bare JID that is on the list
     */
    @Test
    public void testTargetOnBlockListFullJid() throws Exception
    {
        // Setup test fixture.
        final BlockList bl = new BlockList();
        bl.add("bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"); // unit-test@xmpp.org
        final JID target = new JID("unit-test@xmpp.org/resource");

        // Execute system under test
        final boolean result = bl.contains(target);

        // Verify results;
        assertTrue(result);
    }

    /**
     * Verifies that a JID is no longer 'on the block list' after the target is removed from the list.
     */
    @Test
    public void testTargetNoLongerOnBlockListAfterRemove() throws Exception
    {
        // Setup test fixture.
        final BlockList bl = new BlockList();
        bl.add("bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"); // unit-test@xmpp.org
        bl.remove("bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d");
        final JID target = new JID("unit-test@xmpp.org/resource");


        // Execute system under test
        final boolean result = bl.contains(target);

        // Verify results;
        assertFalse(result);
    }

}
