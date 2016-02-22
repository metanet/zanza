package cs.bilkent.zanza.engine.pipeline;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import cs.bilkent.zanza.engine.exception.InitializationException;
import cs.bilkent.zanza.engine.tuplequeue.TupleQueueContext;
import cs.bilkent.zanza.engine.tuplequeue.TupleQueueDrainer;
import cs.bilkent.zanza.engine.tuplequeue.TupleQueueDrainerFactory;
import cs.bilkent.zanza.engine.tuplequeue.impl.drainer.GreedyDrainer;
import cs.bilkent.zanza.flow.OperatorDefinition;
import cs.bilkent.zanza.kvstore.KVStore;
import cs.bilkent.zanza.operator.InvocationContext;
import cs.bilkent.zanza.operator.InvocationContext.InvocationReason;
import cs.bilkent.zanza.operator.InvocationResult;
import cs.bilkent.zanza.operator.Operator;
import cs.bilkent.zanza.operator.PortsToTuples;
import cs.bilkent.zanza.operator.Tuple;
import cs.bilkent.zanza.scheduling.ScheduleNever;
import cs.bilkent.zanza.scheduling.ScheduleWhenTuplesAvailable;
import static cs.bilkent.zanza.scheduling.ScheduleWhenTuplesAvailable.scheduleWhenTuplesAvailableOnDefaultPort;
import cs.bilkent.zanza.scheduling.SchedulingStrategy;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith( MockitoJUnitRunner.class )
public class OperatorInstanceTest
{

    @Mock
    private TupleQueueContext queue;

    @Mock
    private Operator operator;

    @Mock
    private OperatorDefinition operatorDefinition;

    @Mock
    private Function<Object, KVStore> kvStoreProvider;

    @Mock
    private KVStore kvStore;

    @Mock
    private TupleQueueDrainer drainer;

    @Mock
    private TupleQueueDrainerFactory drainerFactory;

    private final Object key = new Object();

    private OperatorInstance operatorInstance;

    @Before
    public void before ()
    {
        operatorInstance = new OperatorInstance( new PipelineInstanceId( 0, 0, 0 ),
                                                 "op1",
                                                 queue,
                                                 operatorDefinition,
                                                 kvStoreProvider,
                                                 drainerFactory );

        when( drainerFactory.create( any( SchedulingStrategy.class ) ) ).thenReturn( drainer );
        when( drainer.getKey() ).thenReturn( key );
        when( kvStoreProvider.apply( key ) ).thenReturn( kvStore );
    }

    @Test
    public void shouldSetStatusWhenInitializationSucceeds ()
    {
        initOperatorInstance( ScheduleNever.INSTANCE );
        assertThat( operatorInstance.getStatus(), equalTo( OperatorInstanceStatus.RUNNING ) );
    }

    @Test
    public void shouldSetStatusWhenInitializationFails ()
    {
        createOperatorInstance();
        final RuntimeException exception = new RuntimeException();
        when( operator.init( anyObject() ) ).thenThrow( exception );

        try
        {
            operatorInstance.init();
            fail();
        }
        catch ( InitializationException expected )
        {
            assertThat( operatorInstance.getStatus(), equalTo( OperatorInstanceStatus.INITIALIZATION_FAILED ) );
        }
    }

    @Test( expected = IllegalStateException.class )
    public void shouldFailToInvokeIfNotInitialized ()
    {
        operatorInstance.invoke( null );
    }

    @Test
    public void shouldNotInvokeOperatorAfterScheduleNever () throws Exception
    {
        initOperatorInstance( ScheduleNever.INSTANCE );

        operatorInstance.invoke( null );

        verify( queue, never() ).add( anyObject() );
    }

    @Test
    public void shouldInvokeOperatorWhenSchedulingStrategySatisfied ()
    {
        final ScheduleWhenTuplesAvailable strategy = scheduleWhenTuplesAvailableOnDefaultPort( 1 );
        final ScheduleWhenTuplesAvailable outputStrategy = scheduleWhenTuplesAvailableOnDefaultPort( 2 );
        initOperatorInstance( strategy );

        final ArgumentCaptor<InvocationContext> invocationContextCaptor = ArgumentCaptor.forClass( InvocationContext.class );

        final PortsToTuples operatorInput = new PortsToTuples( new Tuple( "f1", "val2" ) );
        when( drainer.getResult() ).thenReturn( operatorInput );

        final PortsToTuples output = new PortsToTuples( new Tuple( "f1", "val3" ) );
        when( operator.process( anyObject() ) ).thenReturn( new InvocationResult( outputStrategy, output ) );

        final PortsToTuples upstreamInput = new PortsToTuples( new Tuple( "f1", "val1" ) );
        final InvocationResult result = operatorInstance.invoke( upstreamInput );

        verify( queue ).add( upstreamInput );
        verify( drainerFactory ).create( strategy );
        verify( queue ).drain( drainer );
        verify( kvStoreProvider ).apply( key );
        verify( operator ).process( invocationContextCaptor.capture() );

        final InvocationContext context = invocationContextCaptor.getValue();
        assertThat( context.getReason(), equalTo( InvocationReason.SUCCESS ) );
        assertThat( context.getKVStore(), equalTo( kvStore ) );
        assertThat( context.getInputTuples(), equalTo( operatorInput ) );

        assertThat( result.getOutputTuples(), equalTo( output ) );
        assertThat( result.getSchedulingStrategy(), equalTo( outputStrategy ) );
        assertThat( operatorInstance.getSchedulingStrategy(), equalTo( outputStrategy ) );
    }

    @Test
    public void shouldNotInvokeOperatorWhenSchedulingStrategyNotSatisfied ()
    {
        final ScheduleWhenTuplesAvailable strategy = scheduleWhenTuplesAvailableOnDefaultPort( 1 );
        initOperatorInstance( strategy );

        final PortsToTuples upstreamInput = new PortsToTuples( new Tuple( "f1", "val1" ) );
        final InvocationResult result = operatorInstance.invoke( upstreamInput );

        verify( queue ).add( upstreamInput );
        verify( drainerFactory ).create( strategy );
        verify( queue ).drain( drainer );
        verify( kvStoreProvider, never() ).apply( key );
        verify( operator, never() ).process( anyObject() );
        assertNull( result );
        assertThat( operatorInstance.getSchedulingStrategy(), equalTo( strategy ) );
    }

    @Test
    public void shouldNotForceInvokeOperatorAfterScheduleNever ()
    {
        initOperatorInstance( ScheduleNever.INSTANCE );

        operatorInstance.forceInvoke( null, InvocationReason.INPUT_PORT_CLOSED );

        verify( queue, never() ).add( anyObject() );
    }

    @Test
    public void shouldForceInvokeRegardlessOfSchedulingStrategy ()
    {
        testForceInvoke( ScheduleNever.INSTANCE );
    }

    @Test
    public void shouldForcefullySetToScheduleNeverIfForceInvokeDoesNotReturnIt ()
    {
        testForceInvoke( scheduleWhenTuplesAvailableOnDefaultPort( 2 ) );
    }

    private void testForceInvoke ( final SchedulingStrategy outputStrategy )
    {
        final ScheduleWhenTuplesAvailable strategy = scheduleWhenTuplesAvailableOnDefaultPort( 1 );
        when( kvStoreProvider.apply( null ) ).thenReturn( kvStore );
        initOperatorInstance( strategy );

        final PortsToTuples upstreamInput = new PortsToTuples();
        final ArgumentCaptor<TupleQueueDrainer> drainerCaptor = ArgumentCaptor.forClass( TupleQueueDrainer.class );
        final ArgumentCaptor<InvocationContext> invocationContextCaptor = ArgumentCaptor.forClass( InvocationContext.class );

        final PortsToTuples output = new PortsToTuples( new Tuple( "f1", "val" ) );
        when( operator.process( anyObject() ) ).thenReturn( new InvocationResult( outputStrategy, output ) );

        final PortsToTuples result = operatorInstance.forceInvoke( upstreamInput, InvocationReason.INPUT_PORT_CLOSED );

        verify( queue ).add( upstreamInput );
        verify( queue ).drain( drainerCaptor.capture() );
        verify( kvStoreProvider ).apply( null );
        assertTrue( drainerCaptor.getValue() instanceof GreedyDrainer );

        verify( operator ).process( invocationContextCaptor.capture() );

        final InvocationContext context = invocationContextCaptor.getValue();
        assertThat( context.getReason(), equalTo( InvocationReason.INPUT_PORT_CLOSED ) );
        assertThat( context.getKVStore(), equalTo( kvStore ) );
        assertThat( context.getInputTuples(), equalTo( upstreamInput ) );

        assertThat( result, equalTo( output ) );
        assertThat( operatorInstance.getSchedulingStrategy(), equalTo( ScheduleNever.INSTANCE ) );
    }

    private void initOperatorInstance ( final SchedulingStrategy strategy )
    {
        createOperatorInstance();
        when( operator.init( anyObject() ) ).thenReturn( strategy );
        operatorInstance.init();
    }

    private void createOperatorInstance ()
    {
        try
        {
            when( operatorDefinition.createOperator() ).thenReturn( operator );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( e );
        }
    }

}