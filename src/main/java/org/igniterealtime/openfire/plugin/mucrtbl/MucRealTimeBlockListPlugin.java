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
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.muc.MUCEventDelegate;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.File;

public class MucRealTimeBlockListPlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger(MucRealTimeBlockListPlugin.class);

    private BlockList blockList;

    private RTBLMUCEventDelegate rtblmucEventDelegate;

    private PubSubHandler pubSubHandler;

    private StanzaBlocker stanzaBlocker;

    private OccupantRemover occupantRemover;

    public static boolean reinitOnConfigChange = true;

    public static final SystemProperty<JID> BLOCKLIST_SERVICE_JID = SystemProperty.Builder.ofType(JID.class)
        .setKey("plugin.mucrtbl.blocklist.service")
        .setPlugin("MUC Real-Time Block List")
        .setDefaultValue(new JID("xmppbl.org"))
        .setDynamic(true)
        .addListener(o -> reInit())
        .build();

    public static final SystemProperty<String> BLOCKLIST_SERVICE_NODE = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.mucrtbl.blocklist.node")
        .setPlugin("MUC Real-Time Block List")
        .setDefaultValue("muc_bans_sha256")
        .setDynamic(true)
        .addListener(o -> reInit())
        .build();

    public static final SystemProperty<Boolean> BLOCKLIST_STANZABLOCKER_DISABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("plugin.mucrtbl.blocklist.stanzablocker.disabled")
        .setPlugin("MUC Real-Time Block List")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(o -> reInit())
        .build();

    public static final SystemProperty<Boolean> BLOCKLIST_OCCUPANTREMOVER_DISABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("plugin.mucrtbl.blocklist.occupantremover.disabled")
        .setPlugin("MUC Real-Time Block List")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(o -> reInit())
        .build();

    public static void reInit() {
        final MucRealTimeBlockListPlugin plugin = (MucRealTimeBlockListPlugin) XMPPServer.getInstance().getPluginManager().getPluginByName("MUC Real-Time Block List")
            .orElseThrow(IllegalStateException::new);
        if (reinitOnConfigChange) {
            plugin.destroyPlugin();
            plugin.initializePlugin(null, null);
        }
    }

    @Override
    public void initializePlugin(final PluginManager manager, final File pluginDirectory)
    {
        if (BLOCKLIST_SERVICE_JID.getValue() == null || BLOCKLIST_SERVICE_NODE.getValue() == null) {
            Log.warn("Unable to start: system properties '{}' and/or '{}' do not have a value.", BLOCKLIST_SERVICE_NODE.getKey(), BLOCKLIST_SERVICE_JID.getKey());
            return;
        }

        Log.info("Starting...");
        blockList = new BlockList();
        rtblmucEventDelegate = new RTBLMUCEventDelegate(blockList);
        addToAllServices(rtblmucEventDelegate);
        if (!BLOCKLIST_STANZABLOCKER_DISABLED.getValue()) {
            stanzaBlocker = new StanzaBlocker(blockList);
            InterceptorManager.getInstance().addInterceptor(stanzaBlocker);
        }
        if (!BLOCKLIST_OCCUPANTREMOVER_DISABLED.getValue()) {
            occupantRemover = new OccupantRemover(blockList);
            blockList.register(occupantRemover);
        }

        pubSubHandler = new PubSubHandler(blockList, BLOCKLIST_SERVICE_JID.getValue(), BLOCKLIST_SERVICE_NODE.getValue());
        try {
            InterceptorManager.getInstance().addInterceptor(pubSubHandler);
            pubSubHandler.attemptUnsubscribe(BLOCKLIST_SERVICE_JID.getValue(), BLOCKLIST_SERVICE_NODE.getValue());
            pubSubHandler.attemptSubscribe(BLOCKLIST_SERVICE_JID.getValue(), BLOCKLIST_SERVICE_NODE.getValue());
            pubSubHandler.requestAllItems(BLOCKLIST_SERVICE_JID.getValue(), BLOCKLIST_SERVICE_NODE.getValue());
        } catch (UnauthorizedException e) {
            throw new RuntimeException(e);
        }
        Log.debug("Started.");
    }

    @Override
    public void destroyPlugin()
    {
        Log.info("Stopping...");
        if (occupantRemover != null) {
            blockList.unregister(occupantRemover);
            occupantRemover = null;
        }

        if (stanzaBlocker != null) {
            InterceptorManager.getInstance().removeInterceptor(stanzaBlocker);
            stanzaBlocker = null;
        }

        if (pubSubHandler != null) {
            InterceptorManager.getInstance().removeInterceptor(pubSubHandler);
            pubSubHandler = null;
        }

        if (rtblmucEventDelegate != null) {
            removeFromAllServices(rtblmucEventDelegate);
            rtblmucEventDelegate = null;
        }
        blockList = null;
        Log.debug("Stopped.");
    }

    // TODO add delegate to a service that is being created after the plugin is already running.

    /**
     * Adds the delegate to all MUC services that exist in the server.
     *
     * Warning: this can replace a pre-existing delegate.
     *
     * @param delegate the delegate to add to all MUC services.
     */
    protected void addToAllServices(final RTBLMUCEventDelegate delegate) {
        Log.debug("Adding delegate to all existing MUC services.");
        XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices().forEach(multiUserChatService -> {
            if (multiUserChatService instanceof MultiUserChatServiceImpl) {
                final MUCEventDelegate installedDelegate = ((MultiUserChatServiceImpl) multiUserChatService).getMUCDelegate();
                if (installedDelegate != null && installedDelegate != delegate) {
                    Log.warn("Replacing a pre-existing MUC delegate on service '{}'", multiUserChatService.getName());
                }
                ((MultiUserChatServiceImpl) multiUserChatService).setMUCDelegate(delegate);
                Log.debug("Adding delegate to MUC service '{}'.", multiUserChatService.getName());
            }
        });
    }

    /**
     * Removes the delegate from all MUC services that exist in the server, that currently have this delegate installed.
     *
     * @param delegate the delegate to remove from all MUC services.
     */
    protected void removeFromAllServices(final RTBLMUCEventDelegate delegate) {
        Log.debug("Removing delegate from all MUC services.");
        XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices().forEach(multiUserChatService -> {
            if (multiUserChatService instanceof MultiUserChatServiceImpl) {
                final MUCEventDelegate installedDelegate = ((MultiUserChatServiceImpl) multiUserChatService).getMUCDelegate();
                if (delegate == installedDelegate) {
                    ((MultiUserChatServiceImpl) multiUserChatService).setMUCDelegate(null);
                    Log.debug("Removed delegate from MUC service '{}'.", multiUserChatService.getName());
                } else {
                    Log.warn("Not removed delegate from MUC service '{}', as it appears to have a different delegate than ours: {}", multiUserChatService.getName(), installedDelegate);
                }
            }
        });
    }

    public BlockList getBlockList() {
        return blockList;
    }
}
