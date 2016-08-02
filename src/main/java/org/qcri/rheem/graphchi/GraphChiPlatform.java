package org.qcri.rheem.graphchi;

import edu.cmu.graphchi.io.CompressedIO;
import org.qcri.rheem.core.api.Configuration;
import org.qcri.rheem.core.mapping.Mapping;
import org.qcri.rheem.core.optimizer.channels.ChannelConversion;
import org.qcri.rheem.core.optimizer.costs.LoadProfileToTimeConverter;
import org.qcri.rheem.core.optimizer.costs.LoadToTimeConverter;
import org.qcri.rheem.core.platform.Executor;
import org.qcri.rheem.core.platform.Platform;
import org.qcri.rheem.core.plugin.Plugin;
import org.qcri.rheem.core.util.ReflectionUtils;
import org.qcri.rheem.graphchi.execution.GraphChiExecutor;
import org.qcri.rheem.graphchi.mappings.PageRankMapping;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * GraphChi {@link Platform} for Rheem.
 */
public class GraphChiPlatform extends Platform implements Plugin {

    public static final String CPU_MHZ_PROPERTY = "rheem.graphchi.cpu.mhz";

    public static final String CORES_PROPERTY = "rheem.graphchi.cores";

    public static final String HDFS_MS_PER_MB_PROPERTY = "rheem.graphchi.hdfs.ms-per-mb";

    private static final String DEFAULT_CONFIG_FILE = "rheem-graphchi-defaults.properties";

    private static GraphChiPlatform instance;

    private final Collection<Mapping> mappings = new LinkedList<>();

    protected GraphChiPlatform() {
        super("GraphChi");
        this.initialize();
    }

    /**
     * Initializes this instance.
     */
    private void initialize() {
        // Set up.
        CompressedIO.disableCompression();
        GraphChiPlatform.class.getClassLoader().setClassAssertionStatus(
                "edu.cmu.graphchi.preprocessing.FastSharder", false);

        this.mappings.add(new PageRankMapping());
    }
    @Override
    public void configureDefaults(Configuration configuration) {
        configuration.load(ReflectionUtils.loadResource(DEFAULT_CONFIG_FILE));
    }

    public static GraphChiPlatform getInstance() {
        if (instance == null) {
            instance = new GraphChiPlatform();
        }
        return instance;
    }

    @Override
    public Executor.Factory getExecutorFactory() {
        return job -> new GraphChiExecutor(this, job);
    }

    @Override
    public Collection<Mapping> getMappings() {
        return this.mappings;
    }

    @Override
    public Collection<Platform> getRequiredPlatforms() {
        return Collections.singleton(this);
    }

    @Override
    public Collection<ChannelConversion> getChannelConversions() {
        return Collections.emptyList();
    }

    @Override
    public void setProperties(Configuration configuration) {
        // Nothing to do, because we already configured the properties in #configureDefaults(...).
    }

    @Override
    public LoadProfileToTimeConverter createLoadProfileToTimeConverter(Configuration configuration) {
        int cpuMhz = (int) configuration.getLongProperty(CPU_MHZ_PROPERTY);
        int numCores = (int) configuration.getLongProperty(CORES_PROPERTY);
        double hdfsMsPerMb = configuration.getDoubleProperty(HDFS_MS_PER_MB_PROPERTY);
        return LoadProfileToTimeConverter.createDefault(
                LoadToTimeConverter.createLinearCoverter(1 / (numCores * cpuMhz * 1000)),
                LoadToTimeConverter.createLinearCoverter(hdfsMsPerMb / 1000000),
                LoadToTimeConverter.createLinearCoverter(0),
                (cpuEstimate, diskEstimate, networkEstimate) -> cpuEstimate.plus(diskEstimate).plus(networkEstimate)
        );
    }
}
