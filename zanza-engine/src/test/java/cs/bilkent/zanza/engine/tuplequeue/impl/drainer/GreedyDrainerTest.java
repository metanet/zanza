package cs.bilkent.zanza.engine.tuplequeue.impl.drainer;

import org.junit.Test;

import cs.bilkent.zanza.engine.tuplequeue.TupleQueue;
import cs.bilkent.zanza.engine.tuplequeue.impl.queue.SingleThreadedTupleQueue;
import cs.bilkent.zanza.operator.Tuple;
import cs.bilkent.zanza.operator.impl.TuplesImpl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class GreedyDrainerTest
{

    private static final long TIMEOUT_IN_MILLIS = Long.MAX_VALUE;

    @Test( expected = IllegalArgumentException.class )
    public void shouldFailWithNullTupleQueues ()
    {
        final GreedyDrainer greedyDrainer = new GreedyDrainer( 1, TIMEOUT_IN_MILLIS );
        greedyDrainer.drain( null, null );
    }

    @Test( expected = IllegalArgumentException.class )
    public void shouldFailWithEmptyTupleQueues ()
    {
        final GreedyDrainer greedyDrainer = new GreedyDrainer( 1, Long.MAX_VALUE );
        greedyDrainer.drain( null, new TupleQueue[] {} );
    }

    @Test
    public void shouldDrainAllTuplesFromSinglePort ()
    {
        final TupleQueue tupleQueue = new SingleThreadedTupleQueue( 1 );
        final Tuple tuple = new Tuple();
        tupleQueue.offerTuple( tuple );

        final GreedyDrainer greedyDrainer = new GreedyDrainer( 1, Long.MAX_VALUE );

        greedyDrainer.drain( null, new TupleQueue[] { tupleQueue } );

        final TuplesImpl tuples = greedyDrainer.getResult();
        assertNotNull( tuples );
        assertThat( tuples.getNonEmptyPortCount(), equalTo( 1 ) );
        assertThat( tuples.getTupleCount( 0 ), equalTo( 1 ) );
        assertTrue( tuple == tuples.getTupleOrFail( 0, 0 ) );
        assertThat( tupleQueue.size(), equalTo( 0 ) );
    }

    @Test
    public void shouldDrainAllTuplesFromMultiplePorts ()
    {
        final TupleQueue tupleQueue1 = new SingleThreadedTupleQueue( 1 );
        final Tuple tuple1 = new Tuple();
        tupleQueue1.offerTuple( tuple1 );
        final TupleQueue tupleQueue2 = new SingleThreadedTupleQueue( 1 );
        final Tuple tuple2 = new Tuple();
        tupleQueue2.offerTuple( tuple2 );

        final GreedyDrainer greedyDrainer = new GreedyDrainer( 2, Long.MAX_VALUE );

        greedyDrainer.drain( null, new TupleQueue[] { tupleQueue1, tupleQueue2 } );

        final TuplesImpl tuples = greedyDrainer.getResult();
        assertNotNull( tuples );
        assertThat( tuples.getNonEmptyPortCount(), equalTo( 2 ) );
        assertThat( tuples.getTupleCount( 0 ), equalTo( 1 ) );
        assertThat( tuples.getTupleCount( 1 ), equalTo( 1 ) );
        assertTrue( tuple1 == tuples.getTupleOrFail( 0, 0 ) );
        assertTrue( tuple2 == tuples.getTupleOrFail( 1, 0 ) );
        assertThat( tupleQueue1.size(), equalTo( 0 ) );
        assertThat( tupleQueue2.size(), equalTo( 0 ) );
    }

}
