//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.core.client;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.Future;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ShutdownThread;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.WebSocketChannel;
import org.eclipse.jetty.websocket.core.WebSocketPolicy;
import org.eclipse.jetty.websocket.core.extensions.ExtensionConfig;
import org.eclipse.jetty.websocket.core.extensions.WebSocketExtensionRegistry;

public class WebSocketClient extends ContainerLifeCycle
{
    private static final Logger LOG = Log.getLogger(WebSocketClient.class);
    private HttpClient httpClient;
    private WebSocketPolicy policy;
    private WebSocketExtensionRegistry extensionRegistry;
    private DecoratedObjectFactory objectFactory;

    // TODO: Things to consider for inclusion in this class (or removal if they can be set elsewhere, like HttpClient)
    // - AsyncWrite Idle Timeout
    // - Bind Address
    // - SslContextFactory setup
    // - Connect Timeout
    // - Cookie Store

    public WebSocketClient()
    {
        this(new HttpClient());
        // Internally created, let websocket client's lifecycle manage it.
        this.addManaged(httpClient);
    }

    public WebSocketClient(HttpClient httpClient)
    {
        this.httpClient = httpClient;
        this.policy = WebSocketPolicy.newClientPolicy();
        this.extensionRegistry = new WebSocketExtensionRegistry();
        this.objectFactory = new DecoratedObjectFactory();
    }

    public Future<WebSocketChannel> connect(FrameHandler frameHandler, URI wsUri) throws IOException
    {
        WebSocketClientUpgradeRequest request = new WebSocketClientUpgradeRequest(this, wsUri, frameHandler);
        return connect(request);
    }

    public Future<WebSocketChannel> connect(WebSocketClientUpgradeRequest request) throws IOException
    {
        if (!isStarted())
        {
            throw new IllegalStateException(WebSocketClient.class.getSimpleName() + "@" + this.hashCode() + " is not started");
        }

        URI toUri = request.getURI();

        // Validate websocket URI
        if (!toUri.isAbsolute())
        {
            throw new IllegalArgumentException("WebSocket URI must be absolute");
        }

        if (StringUtil.isBlank(toUri.getScheme()))
        {
            throw new IllegalArgumentException("WebSocket URI must include a scheme");
        }

        String scheme = toUri.getScheme().toLowerCase(Locale.ENGLISH);
        if (("ws".equals(scheme) == false) && ("wss".equals(scheme) == false))
        {
            throw new IllegalArgumentException("WebSocket URI scheme only supports [ws] and [wss], not [" + scheme + "]");
        }

        // Validate Requested Extensions
        for (ExtensionConfig reqExt : request.getExtensions())
        {
            if (!extensionRegistry.isAvailable(reqExt.getName()))
            {
                throw new IllegalArgumentException("Requested extension [" + reqExt.getName() + "] is not installed");
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("connect websocket {} to {}", request.getFrameHandler(), request.getURI());

        init();

        return request.sendAsync();
    }

    // TODO: review need for this.
    private synchronized void init() throws IOException
    {
        if (!ShutdownThread.isRegistered(this))
        {
            ShutdownThread.register(this);
        }
    }

    public WebSocketExtensionRegistry getExtensionRegistry()
    {
        return extensionRegistry;
    }

    public HttpClient getHttpClient()
    {
        return httpClient;
    }

    public DecoratedObjectFactory getObjectFactory()
    {
        return objectFactory;
    }

    public WebSocketPolicy getPolicy()
    {
        return policy;
    }
}