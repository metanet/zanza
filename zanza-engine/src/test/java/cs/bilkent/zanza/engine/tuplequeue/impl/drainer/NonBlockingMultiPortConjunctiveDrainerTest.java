package cs.bilkent.zanza.engine.tuplequeue.impl.drainer;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import cs.bilkent.zanza.engine.tuplequeue.TupleQueue;
import cs.bilkent.zanza.engine.tuplequeue.TupleQueueDrainer;
import cs.bilkent.zanza.engine.tuplequeue.impl.queue.UnboundedTupleQueue;
import cs.bilkent.zanza.operator.PortsToTuples;
import cs.bilkent.zanza.operator.Tuple;
import cs.bilkent.zanza.scheduling.ScheduleWhenTuplesAvailable.PortToTupleCount;
import cs.bilkent.zanza.scheduling.ScheduleWhenTuplesAvailable.TupleAvailabilityByCount;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class NonBlockingMultiPortConjunctiveDrainerTest
{

    @Test( expected = IllegalArgumentException.class )
    public void test_input_nullTupleQueues ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount.AT_LEAST,
                                                                                      getPortToTupleCounts( 1, 1 ) );
        drainer.drain( null, null );
    }

    @Test( expected = IllegalArgumentException.class )
    public void test_input_emptyTupleQueues ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount.AT_LEAST,
                                                                                      getPortToTupleCounts( 1, 1 ) );
        drainer.drain( null, new TupleQueue[] {} );
    }

    @Test( expected = IllegalArgumentException.class )
    public void test_input_singleTupleQueue ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount.AT_LEAST,
                                                                                      getPortToTupleCounts( 1, 1 ) );
        drainer.drain( null, new TupleQueue[] { new UnboundedTupleQueue( 1 ) } );
    }

    @Test
    public void test_TupleAvailabilityByCount_AT_LEAST_allQueuesSatisfy ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount.AT_LEAST,
                                                                                      getPortToTupleCounts( 1, 1 ) );
        final TupleQueue tupleQueue1 = new UnboundedTupleQueue( 1 );
        final TupleQueue tupleQueue2 = new UnboundedTupleQueue( 2 );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );

        drainer.drain( null, new TupleQueue[] { tupleQueue1, tupleQueue2 } );

        final PortsToTuples portsToTuples = drainer.getResult();
        assertNotNull( portsToTuples );
        assertThat( portsToTuples.getTupleCount( 0 ), equalTo( 1 ) );
        assertThat( portsToTuples.getTupleCount( 1 ), equalTo( 2 ) );
        assertThat( tupleQueue1.size(), equalTo( 0 ) );
        assertThat( tupleQueue2.size(), equalTo( 0 ) );
    }

    @Test
    public void test_TupleAvailabilityByCount_AT_LEAST_allQueuesDoNotSatisfy ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount.AT_LEAST,
                                                                                      getPortToTupleCounts( 2, 2 ) );
        final TupleQueue tupleQueue1 = new UnboundedTupleQueue( 1 );
        final TupleQueue tupleQueue2 = new UnboundedTupleQueue( 2 );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );

        drainer.drain( null, new TupleQueue[] { tupleQueue1, tupleQueue2 } );

        assertNull( drainer.getResult() );
        assertThat( tupleQueue1.size(), equalTo( 1 ) );
        assertThat( tupleQueue2.size(), equalTo( 2 ) );
    }

    @Test
    public void test_TupleAvailabilityByCount_EXACT_allQueuesSatisfy ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount.EXACT,
                                                                                      getPortToTupleCounts( 2, 2 ) );
        final TupleQueue tupleQueue1 = new UnboundedTupleQueue( 2 );
        final TupleQueue tupleQueue2 = new UnboundedTupleQueue( 2 );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );

        drainer.drain( null, new TupleQueue[] { tupleQueue1, tupleQueue2 } );

        final PortsToTuples portsToTuples = drainer.getResult();
        assertThat( portsToTuples.getPortCount(), equalTo( 2 ) );
        assertThat( portsToTuples.getTupleCount( 0 ), equalTo( 2 ) );
        assertThat( portsToTuples.getTupleCount( 1 ), equalTo( 2 ) );
        assertThat( tupleQueue1.size(), equalTo( 1 ) );
        assertThat( tupleQueue2.size(), equalTo( 0 ) );
    }

    @Test
    public void test_TupleAvailabilityByCount_EXACT_allQueuesDoNotSatisfy ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount.EXACT,
                                                                                      getPortToTupleCounts( 2, 2 ) );
        final TupleQueue tupleQueue1 = new UnboundedTupleQueue( 2 );
        final TupleQueue tupleQueue2 = new UnboundedTupleQueue( 2 );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );

        drainer.drain( null, new TupleQueue[] { tupleQueue1, tupleQueue2 } );

        assertNull( drainer.getResult() );
        assertThat( tupleQueue1.size(), equalTo( 2 ) );
        assertThat( tupleQueue2.size(), equalTo( 1 ) );
    }

    @Test
    public void test_TupleAvailabilityByCount_AT_LEAST_BUT_SAME_ON_ALL_PORTS_allQueuesSatisfy ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount
                                                                                              .AT_LEAST_BUT_SAME_ON_ALL_PORTS,
                                                                                      getPortToTupleCounts( 2, 2 ) );
        final TupleQueue tupleQueue1 = new UnboundedTupleQueue( 2 );
        final TupleQueue tupleQueue2 = new UnboundedTupleQueue( 2 );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );

        drainer.drain( null, new TupleQueue[] { tupleQueue1, tupleQueue2 } );

        final PortsToTuples portsToTuples = drainer.getResult();
        assertNotNull( portsToTuples );
        assertThat( portsToTuples.getPortCount(), equalTo( 2 ) );
        assertThat( portsToTuples.getTupleCount( 0 ), equalTo( 2 ) );
        assertThat( portsToTuples.getTupleCount( 1 ), equalTo( 2 ) );
        assertThat( tupleQueue1.size(), equalTo( 1 ) );
        assertThat( tupleQueue2.size(), equalTo( 0 ) );
    }

    @Test
    public void test_TupleAvailabilityByCount_AT_LEAST_BUT_SAME_ON_ALL_PORTS_allQueuesDoNotSatisfy ()
    {
        final TupleQueueDrainer drainer = new NonBlockingMultiPortConjunctiveDrainer( TupleAvailabilityByCount
                                                                                              .AT_LEAST_BUT_SAME_ON_ALL_PORTS,
                                                                                      getPortToTupleCounts( 2, 2 ) );
        final TupleQueue tupleQueue1 = new UnboundedTupleQueue( 2 );
        final TupleQueue tupleQueue2 = new UnboundedTupleQueue( 2 );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue1.offerTuple( new Tuple() );
        tupleQueue2.offerTuple( new Tuple() );

        drainer.drain( null, new TupleQueue[] { tupleQueue1, tupleQueue2 } );

        assertNull( drainer.getResult() );
        assertThat( tupleQueue1.size(), equalTo( 2 ) );
        assertThat( tupleQueue2.size(), equalTo( 1 ) );
    }

    private List<PortToTupleCount> getPortToTupleCounts ( final int port0Count, final int port1Count )
    {
        final List<PortToTupleCount> counts = new ArrayList<>();
        counts.add( new PortToTupleCount( 0, port0Count ) );
        counts.add( new PortToTupleCount( 1, port1Count ) );
        return counts;
    }

}