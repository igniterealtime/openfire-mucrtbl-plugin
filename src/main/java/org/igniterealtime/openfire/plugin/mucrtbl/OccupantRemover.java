/*
 * Copyright (C) 2023-2024 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.muc.spi.OccupantManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * An event listener that removes any occupants from rooms that they're in, when they are added to a block list.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class OccupantRemover implements BlockListEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(OccupantRemover.class);

    /**
     * The block list representation.
     */
    protected final BlockList blockList;

    /**
     * Creates a new instance that, when invoked, will remove entities that are added to the provided block list.
     *
     * @param blockList The block list representation
     */
    public OccupantRemover(final BlockList blockList)
    {
        this.blockList = blockList;
    }

    @Override
    public void added(final Set<String> hashes)
    {
        final List<MultiUserChatService> multiUserChatServices = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices();
        for (MultiUserChatService service : multiUserChatServices)
        {
            // Use the JIDs of all occupants of MUC rooms of the service
            final Collection<OccupantManager.Occupant> allOccupants = new HashSet<>();
            service.getOccupantManager().getLocalOccupantsByNode().values().forEach(allOccupants::addAll);
            allOccupants.addAll(service.getOccupantManager().getFederatedOccupants());
            final Map<JID, Set<OccupantManager.Occupant>> occupantsByJID = allOccupants.stream()
                .collect(Collectors.groupingBy(OccupantManager.Occupant::getRealJID, Collectors.toSet()));

            // Determine which of these JIDs are on the blocklist (if any). Note that this operates on the entire block
            // list, not only on the hashes that were just added. This isn't an explicit design choice, but an acceptable
            // side effect (entities on the block list but not in the event are undesired, and should be removed anyway).
            final Set<JID> blockedJids = blockList.filterBlocked(occupantsByJID.keySet());

            // Remove all occupants that are blocked from all rooms that they're in.
            for (final JID blockedJid : blockedJids) {
                try {
                    final Set<OccupantManager.Occupant> blockedOccupants = occupantsByJID.get(blockedJid);
                    for (final OccupantManager.Occupant blockedOccupant : blockedOccupants) {
                        removeOccupantFromRoom(service, blockedOccupant);
                    }
                } catch (Throwable t) {
                    Log.warn("Unable to remove occupant ({}) that was added to the block list from rooms.", blockedJid, t);
                }
            }
        }
    }

    @Override
    public void removed(Set<String> hashes)
    {
        // Unused
    }

    // Inspired by org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl#tryRemoveOccupantFromRoom
    private void removeOccupantFromRoom(@Nonnull final MultiUserChatService service, @Nonnull final OccupantManager.Occupant occupant)
    {
        final Lock lock = service.getChatRoomLock(occupant.getRoomName());
        lock.lock();
        try {
            final MUCRoom room = service.getChatRoom(occupant.getRoomName());
            if (room == null) {
                return;
            }

            if (!room.hasOccupant(occupant.getRealJID())) {
                // Occupant no longer in room? A different thread/cluster-node might have beaten us to the punch.
                return;
            }

            // Kick the user from the room that he/she had previously joined.
            Log.info("Removing occupant {} ({}) from room {} as they were added to the block list.", occupant.getRealJID(), occupant.getNickname(), room.getJID());
            room.kickOccupant(occupant.getRealJID(), null, null, "You are forbidden to be in this chatroom.");

            // Ensure that other cluster nodes see any changes that might have been applied.
            service.syncChatRoom(room);
        } catch (final NotAllowedException e) {
            // Do nothing since we cannot kick owners or admins
            Log.debug("Skip removing {}, because it's not allowed (this user likely is an owner of admin of the room).", occupant, e);
        } finally {
            lock.unlock();
        }
    }
}
