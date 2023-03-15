<!--
- Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
-
- Licensed under the Apache License, Version 2.0 (the "License");
- you may not use this file except in compliance with the License.
- You may obtain a copy of the License at
-
- http://www.apache.org/licenses/LICENSE-2.0
-
- Unless required by applicable law or agreed to in writing, software
- distributed under the License is distributed on an "AS IS" BASIS,
- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
- See the License for the specific language governing permissions and
- limitations under the License.
-->
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp" %>
<%@ page import="org.igniterealtime.openfire.plugin.mucrtbl.MucRealTimeBlockListPlugin" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.util.Collections" %>
<%@ taglib uri="admin" prefix="admin"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    final MucRealTimeBlockListPlugin plugin = (MucRealTimeBlockListPlugin) XMPPServer.getInstance().getPluginManager().getPluginByName( "MUC Real-Time Block List" ).orElseThrow(IllegalStateException::new);
    String success = request.getParameter("success");
    boolean update = request.getParameter("update") != null;

    String error = null;

    final Cookie csrfCookie = CookieUtils.getCookie( request, "csrf");
    String csrfParam = ParamUtils.getParameter( request, "csrf");

    if (update)
    {
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) )
        {
            error = "csrf";
        }
        else
        {
            // First parse all properties. Allow exceptions to be thrown before any properties are changed.
            JID serviceJID = null;
            try {
                serviceJID = new JID(request.getParameter("serviceJID"));
            } catch (IllegalArgumentException e) {
                error = "invalid service JID";
            }
            final String serviceNode = request.getParameter("serviceNode");
            if (serviceNode == null || serviceNode.isEmpty()) {
                error = "invalid service node";
            }
            final boolean stanzaBlockerEnabled = ParamUtils.getBooleanParameter(request, "stanzaBlockerEnabled");
            final boolean occupantRemoverEnabled = ParamUtils.getBooleanParameter(request, "occupantRemoverEnabled");

            if (error == null) {
                // Change property values based on the parsed values.
                MucRealTimeBlockListPlugin.reinitOnConfigChange = false;
                MucRealTimeBlockListPlugin.BLOCKLIST_SERVICE_JID.setValue(serviceJID);
                MucRealTimeBlockListPlugin.BLOCKLIST_SERVICE_NODE.setValue(serviceNode);
                MucRealTimeBlockListPlugin.BLOCKLIST_STANZABLOCKER_DISABLED.setValue(!stanzaBlockerEnabled);
                MucRealTimeBlockListPlugin.BLOCKLIST_OCCUPANTREMOVER_DISABLED.setValue(!occupantRemoverEnabled);
                MucRealTimeBlockListPlugin.reinitOnConfigChange = true;
                MucRealTimeBlockListPlugin.reInit(); // prevent each change to restart the plugin. Instead, do it just once.

                webManager.logEvent("MUC RTBL settings have been updated.", "service JID: " + serviceJID + "\nservice node: " + serviceNode + "\nstanza blocker enabled: " + stanzaBlockerEnabled + "\noccupant remover enabled: " + occupantRemoverEnabled);
                response.sendRedirect("mucrtbl.jsp?success=true");
                return;
            }
        }
    }
    csrfParam = StringUtils.randomString( 15 );
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute( "csrf", csrfParam) ;

    pageContext.setAttribute( "serviceJID", MucRealTimeBlockListPlugin.BLOCKLIST_SERVICE_JID.getValue() );
    pageContext.setAttribute( "serviceNode", MucRealTimeBlockListPlugin.BLOCKLIST_SERVICE_NODE.getValue() );
    pageContext.setAttribute( "stanzaBlockerEnabled", !MucRealTimeBlockListPlugin.BLOCKLIST_STANZABLOCKER_DISABLED.getValue() );
    pageContext.setAttribute( "occupantRemoverEnabled", !MucRealTimeBlockListPlugin.BLOCKLIST_OCCUPANTREMOVER_DISABLED.getValue() );
    pageContext.setAttribute( "hashes", plugin.getBlockList() == null ? Collections.emptyMap() : plugin.getBlockList().getAll() );
%>
<html>
<head>
    <title><fmt:message key="mucrtbl.page.title"/></title>
    <meta name="pageID" content="mucrtbl"/>
</head>
<body>

<% if (error != null) { %>

<div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
                <% if ( "csrf".equalsIgnoreCase( error )  ) { %>
                <fmt:message key="global.csrf.failed" />
                <% } else { %>
                <fmt:message key="admin.error" />: <c:out value="error"></c:out>
                <% } %>
            </td></tr>
        </tbody>
    </table>
</div><br>

<%  } %>


<%  if (success != null) { %>

<div class="jive-info">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="/images/info-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-info-text">
                <fmt:message key="settings.saved.successfully" />
            </td></tr>
        </tbody>
    </table>
</div><br>

<%  } %>

<p>
    <fmt:message key="mucrtbl.page.description"/>
</p>

<br>

<div class="jive-contentBoxHeader"><fmt:message key="mucrtbl.page.config.header" /></div>
<div class="jive-contentBox">

    <p><fmt:message key="mucrtbl.page.config.description" /></p>

    <form>
        <input type="hidden" name="csrf" value="${csrf}">

        <table width="80%" cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td style="white-space: nowrap;">
                    <label for="serviceJID"><fmt:message key="mucrtbl.page.config.servicejid.label" /></label>
                </td>
                <td>
                    <input type="text" name="serviceJID" id="serviceJID" size="75" maxlength="1024" value="${empty serviceJID ? "" : admin:escapeHTMLTags(serviceJID)}">
                </td>
            </tr>
            <tr>
                <td style="white-space: nowrap;">
                    <label for="serviceNode"><fmt:message key="mucrtbl.page.config.servicenode.label" /></label>
                </td>
                <td>
                    <input type="text" name="serviceNode" id="serviceNode" size="75" maxlength="1024" value="${empty serviceNode ? "" : admin:escapeHTMLTags(serviceNode)}">
                </td>
            </tr>
            <tr>
                <td colspan="2">
                    <input type="checkbox" name="stanzaBlockerEnabled" id="stanzaBlockerEnabled" ${stanzaBlockerEnabled ? "checked" : ""}>
                    <label for="stanzaBlockerEnabled"><fmt:message key="mucrtbl.page.config.stanzablocker.enabled.label" /></label>
                </td>
            </tr>
            <tr>
                <td colspan="2">
                    <input type="checkbox" name="occupantRemoverEnabled" id="occupantRemoverEnabled" ${occupantRemoverEnabled ? "checked" : ""}>
                    <label for="occupantRemoverEnabled"><fmt:message key="mucrtbl.page.config.occupantremover.enabled.label" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%"></td>
                <td width="99%">
                    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
                </td>
            </tr>
        </table>
    </form>

</div>

<div class="jive-contentBoxHeader"><fmt:message key="mucrtbl.page.content.header" /></div>
<div class="jive-contentBox">

    <p><fmt:message key="mucrtbl.page.content.description"><fmt:param value="${hashes.size()}"/></fmt:message></p>

    <c:if test="${hashes.size() < 50 && hashes.size() > 0}">
        <p><fmt:message key="mucrtbl.page.content.hashes"/></p>
        <ul style="margin: 1em; list-style: initial">
            <c:forEach items="${hashes}" var="entry">
                <li style="list-style: initial"><code><c:out value="${entry.key}"/></code> <c:out value="${entry.value}"/></li>
            </c:forEach>
        </ul>
    </c:if>
</div>
</body>
</html>
