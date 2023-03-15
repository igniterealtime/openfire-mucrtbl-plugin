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
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.xmpp.packet.JID;

import org.mockito.junit.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests that check the deadlock-detection functionality of {@link BlockListEventListener}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@RunWith(MockitoJUnitRunner.class)
public class BlockListEventListenerTest
{
    @Before
    @After
    public void resetCache() throws Exception
    {
        CacheFactory.clearCaches("MUC RealTime Block List");
    }

    /**
     * Verifies that an event is dispatched when an entry is added to a block list.
     */
    @Test
    public void testEntryAddEventDispatched() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList bl = new BlockList();
        bl.register(eventListener);
        final String needle = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org

        // Execute system under test
        bl.add(needle, "");

        // Verify results
        final ArgumentCaptor<Set<String>> argumentCaptor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(eventListener, Mockito.times(1)).added(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
        assertTrue(argumentCaptor.getValue().contains(needle));
        Mockito.verifyNoMoreInteractions(eventListener);
    }

    /**
     * Verifies that an event is dispatched when multiple entries are added to a block list.
     */
    @Test
    public void testEntriesAddEventDispatched() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList bl = new BlockList();
        bl.register(eventListener);
        final String needleA = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org
        final String needleB = "8f96e8e5f1f082c2d56d15db2a9f4888040a621c02bdd0b4375d1f51bc96991a"; // hash of: unit-test-too@example.org
        final Map<String, String> input = new HashMap<>();
        input.put(needleA, "test");
        input.put(needleB, "unit-test");

        // Execute system under test
        bl.addAll(input);

        // Verify results
        final ArgumentCaptor<Set<String>> argumentCaptor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(eventListener, Mockito.times(1)).added(argumentCaptor.capture());
        assertEquals(2, argumentCaptor.getValue().size());
        assertTrue(argumentCaptor.getValue().contains(needleA));
        assertTrue(argumentCaptor.getValue().contains(needleB));
        Mockito.verifyNoMoreInteractions(eventListener);
    }

    /**
     * Verifies that an event is dispatched when an entry is removed from a block list.
     */
    @Test
    public void testEntryRemoveEventDispatched() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList bl = new BlockList();
        bl.register(eventListener);
        final String needle = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org

        // Execute system under test
        bl.add(needle, "unit-test");
        bl.remove(needle);

        // Verify results
        final ArgumentCaptor<Set<String>> argumentCaptor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(eventListener, Mockito.atLeastOnce()).added(Mockito.anySet());
        Mockito.verify(eventListener, Mockito.times(1)).removed(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
        assertTrue(argumentCaptor.getValue().contains(needle));
        Mockito.verifyNoMoreInteractions(eventListener);
    }

    /**
     * Verifies that an event is dispatched when multipe entries are removed from a block list.
     */
    @Test
    public void testEntriesRemoveEventDispatched() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList bl = new BlockList();
        bl.register(eventListener);
        final String needleA = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org
        final String needleB = "8f96e8e5f1f082c2d56d15db2a9f4888040a621c02bdd0b4375d1f51bc96991a"; // hash of: unit-test-too@example.org
        final Map<String, String> input = new HashMap<>();
        input.put(needleA, "test");
        input.put(needleB, "unit-test");

        // Execute system under test
        bl.addAll(input);
        bl.removeAll(Arrays.asList(needleA, needleB));

        // Verify results
        final ArgumentCaptor<Set<String>> argumentCaptor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(eventListener, Mockito.atLeastOnce()).added(Mockito.anySet());
        Mockito.verify(eventListener, Mockito.times(1)).removed(argumentCaptor.capture());
        assertEquals(2, argumentCaptor.getValue().size());
        assertTrue(argumentCaptor.getValue().contains(needleA));
        assertTrue(argumentCaptor.getValue().contains(needleB));
        Mockito.verifyNoMoreInteractions(eventListener);
    }

    /**
     * Verifies that an event is _not_ dispatched (again) when an entry is added that's already on the block list.
     */
    @Test
    public void testPreExistingEntryAdded() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList bl = new BlockList();
        bl.register(eventListener);
        final String needle = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org

        // Execute system under test
        bl.add(needle, "unit-test");
        Mockito.reset(eventListener);
        bl.add(needle, "test");

        // Verify results
        Mockito.verify(eventListener, Mockito.never()).added(Mockito.anySet());
        Mockito.verifyNoMoreInteractions(eventListener);
    }

    /**
     * Verifies that an event is _not_ dispatched when an entry is removed that's not on the block list.
     */
    @Test
    public void testNonExistingEntryRemove() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList bl = new BlockList();
        bl.register(eventListener);
        final String needle = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org

        // Execute system under test
        //bl.add(needle); -- not added!
        bl.remove(needle);

        // Verify results
        Mockito.verify(eventListener, Mockito.never()).removed(Mockito.anySet());
        Mockito.verifyNoMoreInteractions(eventListener);
    }

    /**
     * Verifies that an event is dispatched when entries are added to the block list, but only contains the entities
     * that were added.
     */
    @Test
    public void testSomeEntriesAddEventDispatched() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList bl = new BlockList();
        bl.register(eventListener);
        final String needleA = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org
        final String needleB = "8f96e8e5f1f082c2d56d15db2a9f4888040a621c02bdd0b4375d1f51bc96991a"; // hash of: unit-test-too@example.org

        // Execute system under test
        bl.add(needleA, "unit-test");
        Mockito.reset(eventListener);
        bl.add(needleB,"test");

        // Verify results
        final ArgumentCaptor<Set<String>> argumentCaptor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(eventListener, Mockito.times(1)).added(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
        assertFalse(argumentCaptor.getValue().contains(needleA));
        assertTrue(argumentCaptor.getValue().contains(needleB));
        Mockito.verifyNoMoreInteractions(eventListener);
    }

    /**
     * Verifies that an event is dispatched when entries are removed from a block list, but only contains the entities
     * that were removed.
     */
    @Test
    public void testSomeEntryRemoveEventDispatched() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList bl = new BlockList();
        bl.register(eventListener);
        final String needleA = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org
        final String needleB = "8f96e8e5f1f082c2d56d15db2a9f4888040a621c02bdd0b4375d1f51bc96991a"; // hash of: unit-test-too@example.org

        // Execute system under test
        bl.add(needleB, "unit-test");
        bl.removeAll(Arrays.asList(needleA, needleB));

        // Verify results
        final ArgumentCaptor<Set<String>> argumentCaptor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(eventListener, Mockito.atLeastOnce()).added(Mockito.anySet());
        Mockito.verify(eventListener, Mockito.times(1)).removed(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().size());
        assertFalse(argumentCaptor.getValue().contains(needleA));
        assertTrue(argumentCaptor.getValue().contains(needleB));
        Mockito.verifyNoMoreInteractions(eventListener);
    }

    /**
     * Verifies that events are not captured by an event listener that's registered with another block list than the one
     * that is being modified.
     */
    @Test
    public void testDifferentBlockList() throws Exception
    {
        // Setup test fixture.
        final BlockListEventListener eventListener = Mockito.mock(BlockListEventListener.class);
        final BlockList blA = new BlockList();
        final BlockList blB = new BlockList();
        blA.register(eventListener);
        final String needle = "bd42ad42bf32b98a903f3c3eb5206d9bb318df597db9df7167ed6659db4b3f7d"; // hash of: unit-test@xmpp.org

        // Execute system under test
        blB.add(needle, "unit-test");

        // Verify results
        Mockito.verifyNoMoreInteractions(eventListener);
    }
}
