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

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.*;

/**
 * Interacts with the Pub/Sub service and node (XEP-0060) on which the block list is maintained.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class PubSubHandler implements PacketInterceptor
{
    private static final Logger Log = LoggerFactory.getLogger(PubSubHandler.class);

    /**
     * The address of the pub/sub service that contains the node on which the block list is maintained.
     */
    private final JID service;

    /**
     * The pub/sub node (on {@link #service}) on which the block list is maintained.
     */
    private final String node;

    /**
     * The address that represents the local entity that interacts with the pub/sub service.
     */
    private final JID selfAddress;

    /**
     * The block list representation that is populated with data from the pub/sub node.
     */
    private final BlockList blockList;

    /**
     * Creates a new instance that populates the provided block list representation, using data obtained from the
     * provided pub/sub node on the provided service.
     *
     * @param blockList Block list representation to represent data on the pub/sub node.
     * @param service The address of the pub/sub service that contains the node on which the block list is maintained.
     * @param node The pub/sub node (on service) on which the block list is maintained.
     */
    public PubSubHandler(final BlockList blockList, final JID service, final String node) {
        this.blockList = blockList;
        this.service = service;
        this.node = node;
        this.selfAddress = XMPPServer.getInstance().createJID(null, "mucrtbl");
    }

    @Override
    public void interceptPacket(final Packet stanza, final Session session, final boolean incoming, final boolean processed) throws PacketRejectedException
    {
        if (!incoming || processed || session instanceof ClientSession) {
            return;
        }

        if (!service.equals(stanza.getFrom())) {
            return;
        }

        if (!selfAddress.equals(stanza.getTo())) {
            return;
        }

        if (stanza instanceof IQ) {
            if (((IQ) stanza).isResponse()) {
                handleIQResult((IQ) stanza);
            }
            // This stanza is addressed to 'us'. Prevent further processing of this stanza.
            throw new PacketRejectedException(); // Will generate an error for requests, which is appropriate.
        }
        if (stanza instanceof Message) {
            handleMessage((Message) stanza);

            // This stanza is addressed to 'us'. Prevent further processing of this stanza.
            throw new PacketRejectedException(); // A message rejected without a rejection message will not generate an error response that's sent back to the sender.
        }
    }

    /**
     * Attempts to remove any subscription to a pub/sub node.
     *
     * This method sends a stanza that represents a subscription removal, but does not verify if there is a pre-existing
     * subscription, nor if the request is successful.
     *
     * @param service the pub/sub service that contains the node.
     * @param node The node from which to unsubscribe.
     */
    public void attemptUnsubscribe(final JID service, final String node) throws UnauthorizedException
    {
        Log.debug("Attempting to be unsubscribed from node '{}' on service '{}'", node, service);
        final IQ stanza = new IQ(IQ.Type.set);
        stanza.setTo(service);
        stanza.setFrom(selfAddress);
        final Element subEl = stanza.setChildElement("pubsub", "http://jabber.org/protocol/pubsub").addElement("unsubscribe");
        subEl.addAttribute("node", node);
        subEl.addAttribute("jid", selfAddress.toString());

        XMPPServer.getInstance().getIQRouter().route(stanza);
    }

    /**
     * Attempts to create a subscription to a pub/sub node.
     *
     * This method sends a stanza that represents a subscription request, but does not verify if the request is successful.
     *
     * @param service the pub/sub service that contains the node.
     * @param node The node to which to subscribe.
     */
    public void attemptSubscribe(final JID service, final String node) throws UnauthorizedException
    {
        Log.debug("Attempting to subscribe to node '{}' on service '{}'", node, service);
        final IQ stanza = new IQ(IQ.Type.set);
        stanza.setTo(service);
        stanza.setFrom(selfAddress);
        final Element subEl = stanza.setChildElement("pubsub", "http://jabber.org/protocol/pubsub").addElement("subscribe");
        subEl.addAttribute("node", node);
        subEl.addAttribute("jid", selfAddress.toString());

        XMPPServer.getInstance().getIQRouter().route(stanza);
    }

    /**
     * Sends a request to retrieve all items from a pub/sub node.
     *
     * This method sends a stanza that represents the request, but does not verify if the request is successful.
     *
     * @param service the pub/sub service that contains the node.
     * @param node The node from which to unsubscribe.
     */
    public void requestAllItems(final JID service, final String node)
    {
        Log.debug("Attempting to retrieve all hashes from node '{}' on service '{}'", node, service);
        final IQ stanza = new IQ(IQ.Type.get);
        stanza.setTo(service);
        stanza.setFrom(selfAddress);
        final Element subEl = stanza.setChildElement("pubsub", "http://jabber.org/protocol/pubsub").addElement("items");
        subEl.addAttribute("node", node);

        XMPPServer.getInstance().getIQRouter().route(stanza);
    }

    /**
     * Processes an IQ response, that is assumed to be generated by the pub/sub service (that contains the node that is
     * used to maintain the block list) and is addressed to this component.
     *
     * This method only acts on results that contain listings of items on a pub/sub node, which are assumed to relate
     * to the block list. These items are added to the block list representation that is maintained in Openfire.
     *
     * @param iq An IQ response
     */
    protected void handleIQResult(final IQ iq)
    {
        if (!iq.isResponse()) {
            throw new IllegalArgumentException("Argument is expected to be an IQ response, but was not.");
        }
        Log.trace("Handling IQ result: {}", iq.toXML());

        final Element childElement = iq.getChildElement();
        if (childElement == null || !"pubsub".equals(childElement.getName()) || !"http://jabber.org/protocol/pubsub".equals(childElement.getNamespaceURI())) {
            // Ignore results that are not pubsub listings
            return;
        }
        final Element itemsEl = childElement.element("items");
        if (itemsEl == null || !node.equals(itemsEl.attributeValue("node"))) {
            // Ignore results from another node.
            return;
        }

        // Received new to-be-banned nodes.
        final Map<String, String> hashes = extractHashesFromPubsubItems(itemsEl);

        Log.debug("Received a list of hashes from the block list. List size: {}", hashes.size());
        if (!hashes.isEmpty()) {
            blockList.addAll(hashes);
        }

        // TODO: Was this a partial result? Request the next page! Prosody's pubsub implementation currently doesn't
        //       support RSM, so pagination is unlikely to become a relevant feature anytime soon.
    }

    /**
     * Processes message stanza, that is assumed to be a pub/sub event generated by the pub/sub service (that contains
     * the node that is used to maintain the block list) and is addressed to this component.
     *
     * This method item addition as well as retraction events. Corresponding changes will be applied to the block list
     * representation that is maintained in Openfire.
     *
     * @param message Message stanza (presumed to be a pubsub event)
     */
    protected void handleMessage(final Message message)
    {
        Log.trace("Handling message: {}", message.toXML());
        final Element eventEl = message.getChildElement("event", "http://jabber.org/protocol/pubsub#event");
        if (eventEl == null) {
            // Ignore results that are not pubsub events
            return;
        }
        final Element itemsEl = eventEl.element("items");
        if (itemsEl == null || !node.equals(itemsEl.attributeValue("node"))) {
            // Ignore non-items, or items from a different node.
            return;
        }

        // Remove items that are retracted from the blocklist.
        final Set<String> hashesRetracted = new HashSet<>();
        for (final Element itemEl : itemsEl.elements("retract")) {
            final String id = itemEl.attributeValue("id");
            if (id != null) {
                hashesRetracted.add(id);
            }
        }

        if (!hashesRetracted.isEmpty()) {
            Log.debug("Received hash(es) from the pubsub service that are removed from the block list. List size: {}", hashesRetracted.size());
            blockList.removeAll(hashesRetracted);
        }

        // Add new items to the blocklist.
        final Map<String, String> hashesAdded = extractHashesFromPubsubItems(itemsEl);

        if (!hashesAdded.isEmpty()) {
            Log.debug("Received hash(es) from the pubsub service that are added to the block list. List size: {}", hashesAdded.size());
            blockList.addAll(hashesAdded);
        }
    }

    static Map<String, String> extractHashesFromPubsubItems(final Element itemsEl)
    {
        final Map<String, String> results = new HashMap<>();
        final List<Element> items = itemsEl.elements("item");
        for (final Element item : items) {
            final String itemId = item.attributeValue("id");

            // Best-effort parsing of a reason, from the item payload that presumably is XEP-0377.
            String reason = "";
            try {
                final Element report = item.element(QName.get("report", "urn:xmpp:reporting:1"));
                if (report != null) {
                    final String reasonValue = report.attributeValue("reason");
                    if ("urn:xmpp:reporting:spam".equals(reasonValue)) {
                        reason = "Spam";
                    }
                    if ("urn:xmpp:reporting:abuse".equals(reasonValue)) {
                        reason = "Abuse";
                    }
                    final String text = report.elementTextTrim("text");
                    if (text != null && !text.isEmpty()) {
                        if (!reason.isEmpty()) {
                            reason += ": ";
                        }
                        reason += text;
                    }
                }
                Log.trace("Identified for item '{}' reason: {}", itemId, reason);
            } catch (Exception e) {
                Log.warn("Unable to parse reason from item with ID {}", itemId, e);
            }

            results.put(itemId, reason);
        }
        return results;
    }
}
