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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

/**
 * Blocks stanzas sent from entities that are on a block list to a MUC service or MUC room.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StanzaBlocker implements PacketInterceptor
{
    private static final Logger Log = LoggerFactory.getLogger(StanzaBlocker.class);

    /**
     * The block list representation.
     */
    protected final BlockList blockList;

    /**
     * Creates a new instance that blocks all stanzas from entities that are on the provided block list.
     *
     * @param blockList Representation of a list of blocked entities.
     */
    public StanzaBlocker(final BlockList blockList)
    {
        this.blockList = blockList;
    }

    @Override
    public void interceptPacket(final Packet stanza, final Session session, final boolean incoming, final boolean processed) throws PacketRejectedException
    {
        if (stanza == null || stanza.getTo() == null || !incoming || processed) {
            return;
        }

        final boolean addressedToMUC = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(stanza.getTo()) != null;
        if (!addressedToMUC) {
            return;
        }

        if (blockList.contains(stanza.getFrom())) {
            Log.info("Blocking stanza from user {} sent to a MUC entity {} as they are on to the block list.", stanza.getFrom(), stanza.getTo() );
            throw new PacketRejectedException("You are forbidden to interact with chat rooms.");
        }
    }
}
