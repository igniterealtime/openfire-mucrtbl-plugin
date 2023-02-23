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

import org.jivesoftware.openfire.muc.MUCEventDelegate;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.util.Map;

/**
 * Openfire MUCEventDelegate that is used to prevent entities on the block list from joining a room. This implementation
 * also prevents these entities from inviting or being invited in MUC rooms.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RTBLMUCEventDelegate extends MUCEventDelegate
{
    private static final Logger Log = LoggerFactory.getLogger(RTBLMUCEventDelegate.class);

    /**
     * The block list representation.
     */
    protected final BlockList blockList;

    /**
     * Creates as new instance that uses the provided block list instance when deciding which entities are allowed to
     * interact with MUC rooms.
     *
     * @param blockList the block list that contains entities that are to be prevented from joining MUC rooms.
     */
    public RTBLMUCEventDelegate(final BlockList blockList)
    {
        this.blockList = blockList;
    }

    @Override
    public boolean joiningRoom(MUCRoom room, JID userjid)
    {
        final boolean blocked = blockList.contains(userjid);
        Log.trace("Entity '{}' (that is joining room '{}') {} on the block list.", userjid, room.getJID(), blocked ? "is" : "is not");
        return !blocked; // return 'true' if the user can join the room.
    }

    @Override
    public InvitationResult sendingInvitation(MUCRoom room, JID inviteeJID, JID inviterJID, String inviteMessage)
    {
        if (blockList.contains(inviteeJID) || blockList.contains(inviterJID)) {
            Log.trace("Rejecting invitation sent by '{}' to '{}' (for room '{}') as one of both is on the block list.", inviterJID, inviterJID, room.getJID());
            return InvitationResult.REJECTED;
        }
        return InvitationResult.HANDLED_BY_OPENFIRE;
    }

    @Override
    public InvitationRejectionResult sendingInvitationRejection(MUCRoom room, JID to, JID from, String reason)
    {
        return InvitationRejectionResult.HANDLED_BY_OPENFIRE;
    }

    @Override
    public Map<String, String> getRoomConfig(String roomName)
    {
        return null;
    }

    @Override
    public boolean destroyingRoom(String roomName, JID userjid)
    {
        return true;
    }

    @Override
    public boolean shouldRecreate(String roomName, JID userjid)
    {
        return false;
    }
}
