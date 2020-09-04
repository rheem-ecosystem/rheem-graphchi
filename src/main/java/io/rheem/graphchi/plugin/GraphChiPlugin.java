package io.rheem.graphchi.plugin;

import io.rheem.core.api.Configuration;
import io.rheem.core.mapping.Mapping;
import io.rheem.core.optimizer.channels.ChannelConversion;
import io.rheem.core.platform.Platform;
import io.rheem.core.plugin.Plugin;
import io.rheem.core.util.fs.LocalFileSystem;
import io.rheem.graphchi.channels.ChannelConversions;
import io.rheem.graphchi.mappings.Mappings;
import io.rheem.graphchi.platform.GraphChiPlatform;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * This {@link Plugin} activates default capabilities of the {@link GraphChiPlatform}.
 */
public class GraphChiPlugin implements Plugin {

    @Override
    public Collection<Mapping> getMappings() {
        return Mappings.ALL;
    }

    @Override
    public Collection<Platform> getRequiredPlatforms() {
        return Collections.singleton(GraphChiPlatform.getInstance());
    }

    @Override
    public Collection<ChannelConversion> getChannelConversions() {
        return ChannelConversions.ALL;
    }

    @Override
    public void setProperties(Configuration configuration) {
        final File localTempDir = LocalFileSystem.findTempDir();
        if (localTempDir != null) {
            configuration.setProperty("rheem.graphchi.tempdir", localTempDir.toString());
        }
    }

}
