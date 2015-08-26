/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.engine.cfg;

import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.nfs.ChronicleNfsServer;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import org.dcache.xdr.OncRpcSvc;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;


/**
 * Created by peter on 26/08/15.
 */
public class NfsCfg implements Installable, Marshallable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NfsCfg.class);
    private boolean enabled;
    private boolean debug;
    private OncRpcSvc oncRpcSvc;

    @Override
    public NfsCfg install(String path, AssetTree assetTree) throws IOException, URISyntaxException {
        if (enabled) {
            LOGGER.info("Enabling NFS for " + assetTree);
            oncRpcSvc = ChronicleNfsServer.start(assetTree, debug);
        }
        return this;
    }

    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IllegalStateException {
        wire.read(() -> "enabled").bool(b -> enabled = b)
                .read(() -> "debug").bool(b -> debug = b);
    }

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write(() -> "enabled").bool(enabled)
                .write(() -> "debug").bool(debug);
    }

    @Override
    public String toString() {
        return "NfsCfg{" +
                "enabled=" + enabled +
                ", debug=" + debug +
                '}';
    }
}
