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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

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
        bl.add("60eb02d00ee3bdd0c46d9c6a360037882a9137902f3533a78fa73aae2ec9dbe2", "unit-test"); // unit-test-not-on-list@example.com
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
        bl.add("bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d", "unit-test"); // unit-test@xmpp.org
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
        bl.add("bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d", "unit-test"); // unit-test@xmpp.org
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
        bl.add("bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d", "unit-test"); // unit-test@xmpp.org
        bl.remove("bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d");
        final JID target = new JID("unit-test@xmpp.org/resource");


        // Execute system under test
        final boolean result = bl.contains(target);

        // Verify results;
        assertFalse(result);
    }

    /**
     * Verifies that a JID is detected 'on the block list' if the target is a bare JID matching a domain JID that is on the list.
     */
    @Test
    public void testTargetDomainOnBlockList() throws Exception
    {
        // Setup test fixture.
        final BlockList bl = new BlockList();
        bl.add("bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b5", "unit-test"); // example.org
        final JID target = new JID("unit-test@example.org");

        // Execute system under test
        final boolean result = bl.contains(target);

        // Verify results;
        assertTrue(result);
    }

    /**
     * Verifies that a JID is detected 'on the block list' if the target is a bare JID does not match a block list that
     * only contains a domain JID (for a different domain).
     */
    @Test
    public void testTargetDomainNotOnBlockList() throws Exception
    {
        // Setup test fixture.
        final BlockList bl = new BlockList();
        bl.add("bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b5", "unit-test"); // example.org
        final JID target = new JID("unit-test@example.com");

        // Execute system under test
        final boolean result = bl.contains(target);

        // Verify results;
        assertFalse(result);
    }

    /**
     * Verifies that {@link BlockList#filterBlocked(Collection)} does not identify any JID if the block list is empty.
     */
    @Test
    public void testFilterBlockedEmptyBlocklist() throws Exception
    {
        final BlockList bl = new BlockList();
        final Collection<JID> input = Arrays.asList(new JID("unit-test@example.com"));

        // Execute system under test
        final Collection<JID> result = bl.filterBlocked(input);

        // Verify results;
        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that {@link BlockList#filterBlocked(Collection)} does not identify any JID if the provided collection
     * of JIDs to check is empty.
     */
    @Test
    public void testFilterBlockedEmptyInput() throws Exception
    {
        final BlockList bl = new BlockList();
        bl.add("7d8fb65cd03bbb40033ff79454b2ef8c95d654e8eff8fa5e2770492d9aa31e56", "unit-test"); // unit-test@example.org
        final Collection<JID> input = Collections.emptyList();

        // Execute system under test
        final Collection<JID> result = bl.filterBlocked(input);

        // Verify results;
        assertTrue(result.isEmpty());
    }

    /**
     * Verifies that {@link BlockList#filterBlocked(Collection)} only returns provided JIDs when they're on the block list.
     */
    @Test
    public void testFilterBlocked() throws Exception
    {
        final BlockList bl = new BlockList();
        bl.add("7d8fb65cd03bbb40033ff79454b2ef8c95d654e8eff8fa5e2770492d9aa31e56", "unit-test"); // unit-test@example.org
        final Collection<JID> input = Arrays.asList(new JID("unit-test@example.org"), new JID("test-unit@example.com"));

        // Execute system under test
        final Collection<JID> result = bl.filterBlocked(input);

        // Verify results;
        final Collection<JID> expected = Arrays.asList(new JID("unit-test@example.org"));
        assertEquals(expected.size(), result.size());
        assertTrue(result.containsAll(expected));
    }

    /**
     * Verifies that {@link BlockList#filterBlocked(Collection)} returns provided JIDs when their domain-part is on the block list.
     */
    @Test
    public void testFilterBlockedDomain() throws Exception
    {
        final BlockList bl = new BlockList();
        bl.add("bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b5", "unit-test"); // example.org
        final Collection<JID> input = Arrays.asList(new JID("unit-test@example.org"), new JID("test-unit@example.com"), new JID("foo-bar@example.org"));

        // Execute system under test
        final Collection<JID> result = bl.filterBlocked(input);

        // Verify results;
        final Collection<JID> expected = Arrays.asList(new JID("foo-bar@example.org"), new JID("unit-test@example.org"));
        assertEquals(expected.size(), result.size());
        assertTrue(result.containsAll(expected));
    }
}
