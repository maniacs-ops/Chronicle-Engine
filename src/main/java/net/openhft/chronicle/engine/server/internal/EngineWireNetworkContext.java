/*
 * Copyright 2016 higherfrequencytrading.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.openhft.chronicle.engine.server.internal;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.annotation.UsedViaReflection;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import net.openhft.chronicle.engine.cfg.EngineClusterContext;
import net.openhft.chronicle.engine.tree.VanillaAsset;
import net.openhft.chronicle.network.*;
import net.openhft.chronicle.network.api.TcpHandler;
import net.openhft.chronicle.network.cluster.ClusterContext;
import net.openhft.chronicle.wire.AbstractMarshallable;
import net.openhft.chronicle.wire.Demarshallable;
import net.openhft.chronicle.wire.WireIn;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SocketChannel;

import static net.openhft.chronicle.engine.api.tree.RequestContext.requestContext;
import static net.openhft.chronicle.engine.server.internal.EngineWireNetworkContext.ConnectionStatus.CONNECTED;
import static net.openhft.chronicle.engine.server.internal.EngineWireNetworkContext.ConnectionStatus.DISCONNECTED;

/**
 * @author Rob Austin.
 */
public class EngineWireNetworkContext<T extends EngineWireNetworkContext>
        extends VanillaNetworkContext<T> {

    static final Logger LOG = LoggerFactory.getLogger(EngineWireNetworkContext.class);

    private Asset rootAsset;
    private MapView<ConnectionDetails, ConnectionStatus> hostByConnectionStatus;
    private MapView<SocketChannel, TcpHandler> socketChannelByHandlers;
    private TcpHandler handler;

    public EngineWireNetworkContext(Asset asset) {
        this.rootAsset = asset.root();
        // TODO make configurable
        serverThreadingStrategy(ServerThreadingStrategy.CONCURRENT);
        ((VanillaAsset) rootAsset.acquireAsset("/proc")).configMapServer();

        try {
            {
                String path = "/proc/connections/cluster/connectivity";
                RequestContext requestContext = requestContext(path).
                        type(ConnectionDetails.class).
                        type2(ConnectionStatus.class);
                hostByConnectionStatus = rootAsset.root().acquireAsset(path)
                        .acquireView(MapView.class, requestContext);
            }

            {
                String path = "/proc/connections/handlers";
                RequestContext requestContext = requestContext(path).
                        type(SocketChannel.class).
                        type2(TcpHandler.class);
                socketChannelByHandlers = rootAsset.root().acquireAsset(path)
                        .acquireView(MapView.class, requestContext);

                onHandlerChanged0(handler);

            }

        } catch (Exception e) {
            if (Jvm.isDebug())
                Jvm.debug().on(getClass(), e);
            throw e;
        }
    }

    @NotNull
    public Asset rootAsset() {
        return this.rootAsset;
    }

    @Override
    public void onHandlerChanged(TcpHandler handler) {
        this.handler = handler;
        onHandlerChanged0(handler);
    }

    private void onHandlerChanged0(TcpHandler handler) {
        SocketChannel socketChannel = socketChannel();
        if (socketChannelByHandlers != null && socketChannel != null) {
            socketChannelByHandlers.put(socketChannel, handler);
        }
    }

    @Override
    public void close() {
        SocketChannel socketChannel = socketChannel();
        if (socketChannelByHandlers != null && socketChannel != null)
            socketChannelByHandlers.remove(socketChannel);
    }

    @Override
    public ConnectionListener acquireConnectionListener() {

        return new ConnectionListener() {

            @Override
            public void onConnected(int localIdentifier, int remoteIdentifier) {
                ConnectionDetails key = new ConnectionDetails(localIdentifier, remoteIdentifier);
                hostByConnectionStatus.put(key, CONNECTED);
                LOG.info(key + ", connectionStatus=" + CONNECTED);
            }

            @Override
            public void onDisconnected(int localIdentifier, int remoteIdentifier) {
                ConnectionDetails key = new ConnectionDetails(localIdentifier, remoteIdentifier);
                hostByConnectionStatus.put(key, DISCONNECTED);
                LOG.info(key + ", connectionStatus=" + DISCONNECTED);
            }
        };

    }

    @Override
    public String toString() {
        return "hostByConnectionStatus=" + hostByConnectionStatus.entrySet().toString();
    }

    public enum ConnectionStatus {
        CONNECTED, DISCONNECTED
    }

    public static class ConnectionDetails extends AbstractMarshallable {
        int localIdentifier;
        int remoteIdentifier;

        ConnectionDetails(int localIdentifier, int remoteIdentifier) {
            this.localIdentifier = localIdentifier;
            this.remoteIdentifier = remoteIdentifier;
        }

        public int localIdentifier() {
            return localIdentifier;
        }

        public int remoteIdentifier() {
            return remoteIdentifier;
        }

        @Override
        public String toString() {
            return "localId=" + localIdentifier + ", remoteId=" + remoteIdentifier;
        }
    }

    public static class Factory implements
            MarshallableFunction<ClusterContext,
                    NetworkContext>, Demarshallable {

        @UsedViaReflection
        private Factory(@NotNull WireIn wireIn) {
        }

        public Factory() {
        }

        @Override
        public NetworkContext apply(ClusterContext context) {
            return new EngineWireNetworkContext<>(((EngineClusterContext) context).assetRoot());
        }
    }
}

