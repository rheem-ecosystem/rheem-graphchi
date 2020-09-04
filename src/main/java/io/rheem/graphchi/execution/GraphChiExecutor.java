package io.rheem.graphchi.execution;

import io.rheem.core.api.Configuration;
import io.rheem.core.api.Job;
import io.rheem.core.optimizer.OptimizationContext;
import io.rheem.core.plan.executionplan.ExecutionStage;
import io.rheem.core.plan.executionplan.ExecutionTask;
import io.rheem.core.platform.ChannelInstance;
import io.rheem.core.platform.ExecutionState;
import io.rheem.core.platform.Executor;
import io.rheem.core.platform.ExecutorTemplate;
import io.rheem.core.platform.PartialExecution;
import io.rheem.core.platform.lineage.ExecutionLineageNode;
import io.rheem.core.util.Tuple;
import io.rheem.graphchi.operators.GraphChiExecutionOperator;
import io.rheem.graphchi.platform.GraphChiPlatform;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * {@link Executor} for the {@link GraphChiPlatform}.
 */
public class GraphChiExecutor extends ExecutorTemplate {

    private final GraphChiPlatform platform;

    private final Configuration configuration;

    private final Job job;

    public GraphChiExecutor(GraphChiPlatform platform, Job job) {
        super(job.getCrossPlatformExecutor());
        this.job = job;
        this.platform = platform;
        this.configuration = job.getConfiguration();
    }

    @Override
    public void execute(final ExecutionStage stage, OptimizationContext optimizationContext, ExecutionState executionState) {
        Queue<ExecutionTask> scheduledTasks = new LinkedList<>(stage.getStartTasks());
        Set<ExecutionTask> executedTasks = new HashSet<>();

        while (!scheduledTasks.isEmpty()) {
            final ExecutionTask task = scheduledTasks.poll();
            if (executedTasks.contains(task)) continue;
            this.execute(task, optimizationContext, executionState);
            executedTasks.add(task);
            Arrays.stream(task.getOutputChannels())
                    .flatMap(channel -> channel.getConsumers().stream())
                    .filter(consumer -> consumer.getStage() == stage)
                    .forEach(scheduledTasks::add);
        }
    }

    /**
     * Brings the given {@code task} into execution.
     */
    private void execute(ExecutionTask task, OptimizationContext optimizationContext, ExecutionState executionState) {
        final GraphChiExecutionOperator graphChiExecutionOperator = (GraphChiExecutionOperator) task.getOperator();

        ChannelInstance[] inputChannelInstances = new ChannelInstance[task.getNumInputChannels()];
        for (int i = 0; i < inputChannelInstances.length; i++) {
            inputChannelInstances[i] = executionState.getChannelInstance(task.getInputChannel(i));
        }
        final OptimizationContext.OperatorContext operatorContext = optimizationContext.getOperatorContext(graphChiExecutionOperator);
        ChannelInstance[] outputChannelInstances = new ChannelInstance[task.getNumOuputChannels()];
        for (int i = 0; i < outputChannelInstances.length; i++) {
            outputChannelInstances[i] = task.getOutputChannel(i).createInstance(this, operatorContext, i);
        }

        long startTime = System.currentTimeMillis();
        final Tuple<Collection<ExecutionLineageNode>, Collection<ChannelInstance>> results =
                graphChiExecutionOperator.execute(inputChannelInstances, outputChannelInstances, operatorContext);
        long endTime = System.currentTimeMillis();

        final Collection<ExecutionLineageNode> executionLineageNodes = results.getField0();
        final Collection<ChannelInstance> producedChannelInstances = results.getField1();

        for (ChannelInstance outputChannelInstance : outputChannelInstances) {
            if (outputChannelInstance != null) {
                executionState.register(outputChannelInstance);
            }
        }

        final PartialExecution partialExecution = this.createPartialExecution(executionLineageNodes, endTime - startTime);
        executionState.add(partialExecution);
        this.registerMeasuredCardinalities(producedChannelInstances);
    }

    @Override
    public void dispose() {
        // Maybe clean up some files?
    }

    @Override
    public GraphChiPlatform getPlatform() {
        return this.platform;
    }
}
