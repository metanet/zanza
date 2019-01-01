package cs.bilkent.joker.engine.pipeline.impl.downstreamcollector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.inject.Named;

import static cs.bilkent.joker.JokerModule.DOWNSTREAM_FAILURE_FLAG_NAME;
import cs.bilkent.joker.engine.exception.JokerException;
import cs.bilkent.joker.engine.metric.PipelineReplicaMeter.Ticker;
import cs.bilkent.joker.engine.partition.PartitionKeyExtractor;
import static cs.bilkent.joker.engine.partition.PartitionUtil.getPartitionId;
import cs.bilkent.joker.engine.pipeline.DownstreamCollector;
import cs.bilkent.joker.engine.tuplequeue.OperatorQueue;
import cs.bilkent.joker.engine.util.concurrent.BackoffIdleStrategy;
import cs.bilkent.joker.engine.util.concurrent.IdleStrategy;
import cs.bilkent.joker.operator.Tuple;
import cs.bilkent.joker.operator.impl.TuplesImpl;

public abstract class AbstractPartitionedDownstreamCollector implements DownstreamCollector, Supplier<OperatorQueue[]>
{

    private final IdleStrategy idleStrategy = BackoffIdleStrategy.newDefaultInstance();

    private final AtomicBoolean failureFlag;

    private final int partitionCount;

    private final int[] partitionDistribution;

    private final int replicaCount;

    private final OperatorQueue[] operatorQueues;

    private final PartitionKeyExtractor partitionKeyExtractor;

    private final Ticker ticker;

    private final List<Tuple>[] tupleLists;

    private final int[] indices;

    AbstractPartitionedDownstreamCollector ( @Named( DOWNSTREAM_FAILURE_FLAG_NAME ) final AtomicBoolean failureFlag,
                                             final int partitionCount,
                                             final int[] partitionDistribution,
                                             final OperatorQueue[] operatorQueues,
                                             final PartitionKeyExtractor partitionKeyExtractor,
                                             final Ticker ticker )
    {
        this.failureFlag = failureFlag;
        this.partitionCount = partitionCount;
        this.partitionDistribution = Arrays.copyOf( partitionDistribution, partitionDistribution.length );
        this.replicaCount = operatorQueues.length;
        this.operatorQueues = Arrays.copyOf( operatorQueues, operatorQueues.length );
        this.partitionKeyExtractor = partitionKeyExtractor;
        this.ticker = ticker;
        this.tupleLists = new List[ operatorQueues.length ];
        for ( int i = 0; i < operatorQueues.length; i++ )
        {
            tupleLists[ i ] = new ArrayList<>();
        }
        this.indices = new int[ operatorQueues.length ];
    }

    public final OperatorQueue[] get ()
    {
        return Arrays.copyOf( operatorQueues, operatorQueues.length );
    }

    final void send ( final TuplesImpl input, final int sourcePortIndex, final int destinationPortIndex )
    {
        for ( Tuple tuple : input.getTuplesModifiable( sourcePortIndex ) )
        {
            final int replicaIndex = partitionDistribution[ getPartitionId( partitionKeyExtractor.getHash( tuple ), partitionCount ) ];
            tupleLists[ replicaIndex ].add( tuple );
        }

        final boolean recordQueueOfferTime = ticker.tryTick();
        int completed = 0;
        while ( completed < replicaCount )
        {
            completed = 0;
            for ( int i = 0; i < replicaCount; i++ )
            {
                final List<Tuple> tuples = tupleLists[ i ];
                int fromIndex = indices[ i ];
                if ( fromIndex < tuples.size() )
                {
                    if ( recordQueueOfferTime && fromIndex == 0 )
                    {
                        tuples.get( 0 ).setQueueOfferTime( System.nanoTime() );
                    }

                    final int offered = operatorQueues[ i ].offer( destinationPortIndex, tuples, fromIndex );
                    if ( offered > 0 )
                    {
                        fromIndex += offered;
                        indices[ i ] = fromIndex;
                    }
                    else if ( idleStrategy.idle() && failureFlag.get() )
                    {
                        throw new JokerException( "Not sending tuples to downstream since failure flag is set" );
                    }
                }

                if ( fromIndex == tuples.size() )
                {
                    completed++;
                }
            }
        }

        idleStrategy.reset();
        for ( int i = 0; i < replicaCount; i++ )
        {
            tupleLists[ i ].clear();
            indices[ i ] = 0;
        }
    }

}
