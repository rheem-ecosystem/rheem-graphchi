package org.qcri.rheem.graphchi.operators;

import edu.cmu.graphchi.ChiFilenames;
import edu.cmu.graphchi.apps.Pagerank;
import edu.cmu.graphchi.datablocks.FloatConverter;
import edu.cmu.graphchi.engine.GraphChiEngine;
import edu.cmu.graphchi.preprocessing.FastSharder;
import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.vertexdata.VertexAggregator;
import org.qcri.rheem.basic.channels.HdfsFile;
import org.qcri.rheem.core.api.exception.RheemException;
import org.qcri.rheem.core.plan.executionplan.Channel;
import org.qcri.rheem.core.plan.rheemplan.InputSlot;
import org.qcri.rheem.core.plan.rheemplan.Operator;
import org.qcri.rheem.core.plan.rheemplan.OperatorBase;
import org.qcri.rheem.core.plan.rheemplan.OutputSlot;
import org.qcri.rheem.core.platform.Platform;
import org.qcri.rheem.core.types.DataSetType;
import org.qcri.rheem.core.util.fs.FileSystem;
import org.qcri.rheem.core.util.fs.FileSystems;
import org.qcri.rheem.graphchi.GraphChiPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * PageRank {@link Operator} implementation for the {@link GraphChiPlatform}.
 */
public class GraphChiPageRankOperator extends OperatorBase implements GraphChiOperator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public GraphChiPageRankOperator() {
        super(1, 1, false, null);
        // TODO: Which datatype should we impose?
        this.inputSlots[0] = new InputSlot<>("input", this, DataSetType.createDefault(Object.class));
        this.outputSlots[0] = new OutputSlot<>("input", this, DataSetType.createDefault(Object.class));
    }

    @Override
    public void execute(Channel[] inputChannels, Channel[] outputChannels) {
        assert inputChannels.length == this.getNumInputs();
        assert outputChannels.length == this.getNumOutputs();

        final HdfsFile inputHdfsFile = (HdfsFile) inputChannels[0];
        final HdfsFile outputHdfsFile = (HdfsFile) outputChannels[0];
        try {
            this.runGraphChi(inputHdfsFile, outputHdfsFile);
        } catch (IOException e) {
            throw new RheemException(String.format("Running %s failed.", this), e);
        }
    }

    private void runGraphChi(HdfsFile inputHdfsFile, HdfsFile outputHdfsFile) throws IOException {

        final String inputPath = inputHdfsFile.getSinglePath();
        final FileSystem inputFs = FileSystems.getFileSystem(inputPath).get();

        // Create shards.
        String graphName = File.createTempFile("rheem-graphchi", "graph").toString();
//        String graphName = String.format("rheem-graphchi-%06x", new Random().nextInt(0xFFFFFF));
        // As suggested by GraphChi, we propose to use approximately 1 shard per 1,000,000 edges.
        final int numShards = 2 + (int) inputFs.getFileSize(inputPath) / (10 * 1000000);
        if (!new File(ChiFilenames.getFilenameIntervals(graphName, numShards)).exists()) {
            FastSharder sharder = createSharder(graphName, numShards);
            final InputStream inputStream = inputFs.open(inputPath);
            sharder.shard(inputStream, FastSharder.GraphInputFormat.EDGELIST);
        } else {
            this.logger.info("Found shards -- no need to preprocess");
        }

        // Run GraphChi.
        GraphChiEngine<Float, Float> engine = new GraphChiEngine<>(graphName, numShards);
        engine.setEdataConverter(new

                FloatConverter()

        );
        engine.setVertexDataConverter(new

                FloatConverter()

        );
        engine.setModifiesInedges(false); // Important optimization
        engine.run(new Pagerank(), 4);

        // Output results.
        final FileSystem outFs = FileSystems.getFileSystem(outputHdfsFile.getSinglePath()).get();
        try (final DataOutputStream dos = new DataOutputStream(outFs.create(outputHdfsFile.getSinglePath()))) {
            VertexIdTranslate trans = engine.getVertexIdTranslate();
            VertexAggregator.foreach(engine.numVertices(), graphName, new FloatConverter(),
                    (vertexId, vertexValue) -> {
                        try {
                            dos.writeInt(trans.backward(vertexId));
                            dos.writeFloat(vertexValue);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }

                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }


    }

    /**
     * Initialize the sharder-program.
     *
     * @param graphName
     * @param numShards
     * @return
     * @throws IOException
     */
    protected static FastSharder createSharder(String graphName, int numShards) throws IOException {
        return new FastSharder<>(
                graphName,
                numShards,
                (vertexId, token) ->
                        (token == null ? 0.0f : Float.parseFloat(token)),
                (from, to, token) ->
                        (token == null ? 0.0f : Float.parseFloat(token)),
                new FloatConverter(),
                new FloatConverter());
    }


    @Override
    public Platform getPlatform() {
        return GraphChiPlatform.getInstance();
    }

    @Override
    public List<Class<? extends Channel>> getSupportedInputChannels(int index) {
        return Collections.singletonList(HdfsFile.class);
    }

    @Override
    public List<Class<? extends Channel>> getSupportedOutputChannels(int index) {
        return Collections.singletonList(HdfsFile.class);
    }
}
