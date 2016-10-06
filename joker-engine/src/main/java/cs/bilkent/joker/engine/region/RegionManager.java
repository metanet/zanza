package cs.bilkent.joker.engine.region;

import java.util.List;

import cs.bilkent.joker.engine.pipeline.PipelineId;
import cs.bilkent.joker.flow.FlowDef;

public interface RegionManager
{

    Region createRegion ( FlowDef flow, RegionConfig regionConfig );

    void validatePipelineMergeParameters ( List<PipelineId> pipelineIds );

    Region mergePipelines ( List<PipelineId> pipelineIdsToMerge );

    void validatePipelineSplitParameters ( PipelineId pipelineId, List<Integer> pipelineOperatorIndicesToSplit );

    Region splitPipeline ( PipelineId pipelineId, List<Integer> pipelineOperatorIndicesToSplit );

    void releaseRegion ( int regionId );

}
