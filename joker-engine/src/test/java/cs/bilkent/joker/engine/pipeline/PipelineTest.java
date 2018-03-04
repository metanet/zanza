package cs.bilkent.joker.engine.pipeline;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.mockito.stubbing.Answer;

import cs.bilkent.joker.engine.config.JokerConfig;
import cs.bilkent.joker.engine.flow.PipelineId;
import cs.bilkent.joker.engine.flow.RegionExecPlan;
import static cs.bilkent.joker.engine.pipeline.OperatorReplicaStatus.COMPLETED;
import static cs.bilkent.joker.engine.pipeline.OperatorReplicaStatus.RUNNING;
import static cs.bilkent.joker.engine.pipeline.UpstreamContext.INITIAL_VERSION;
import cs.bilkent.joker.engine.region.Region;
import cs.bilkent.joker.engine.supervisor.Supervisor;
import cs.bilkent.joker.operator.OperatorDef;
import cs.bilkent.joker.operator.scheduling.SchedulingStrategy;
import cs.bilkent.joker.test.AbstractJokerTest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineTest extends AbstractJokerTest
{

    private final PipelineId pipelineId = new PipelineId( 0, 0 );

    @Test
    public void shouldInitializePipelineReplicas ()
    {
        final PipelineReplica pipelineReplica0 = mock( PipelineReplica.class ), pipelineReplica1 = mock( PipelineReplica.class );
        final SchedulingStrategy schedulingStrategy = mock( SchedulingStrategy.class );
        final SchedulingStrategy[] schedulingStrategies = new SchedulingStrategy[] { schedulingStrategy };
        final SchedulingStrategy[][] fusedSchedulingStrategies = new SchedulingStrategy[][] { { schedulingStrategy } };
        final UpstreamContext upstreamContext = mock( UpstreamContext.class );
        final UpstreamContext[] upstreamContexts = new UpstreamContext[] { upstreamContext };
        final UpstreamContext[][] fusedUpstreamContexts = new UpstreamContext[][] { { upstreamContext } };

        when( pipelineReplica0.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );
        when( pipelineReplica1.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );

        final RegionExecPlan regionExecPlan = mock( RegionExecPlan.class );
        when( regionExecPlan.getReplicaCount() ).thenReturn( 2 );
        when( regionExecPlan.getOperatorCountByPipelineStartIndex( pipelineId.getPipelineStartIndex() ) ).thenReturn( 1 );

        final Region region = mock( Region.class );
        when( region.getPipelineReplicas( pipelineId ) ).thenReturn( new PipelineReplica[] { pipelineReplica0, pipelineReplica1 } );
        when( region.getSchedulingStrategies( pipelineId ) ).thenReturn( schedulingStrategies );
        when( region.getFusedSchedulingStrategies( pipelineId ) ).thenReturn( fusedSchedulingStrategies );
        when( region.getUpstreamContexts( pipelineId ) ).thenReturn( upstreamContexts );
        when( region.getFusedUpstreamContexts( pipelineId ) ).thenReturn( fusedUpstreamContexts );
        when( region.getExecPlan() ).thenReturn( regionExecPlan );

        final Pipeline pipeline = new Pipeline( pipelineId, region );
        pipeline.init();

        assertThat( pipeline.getPipelineStatus(), equalTo( RUNNING ) );
        assertThat( pipeline.getUpstreamContext(), equalTo( upstreamContexts[ 0 ] ) );

        verify( pipelineReplica0 ).init( fusedSchedulingStrategies, fusedUpstreamContexts );
        verify( pipelineReplica1 ).init( fusedSchedulingStrategies, fusedUpstreamContexts );
    }

    @Test
    public void shouldStartPipelineReplicaRunnersOnceInitialized ()
    {
        final PipelineReplica pipelineReplica0 = mock( PipelineReplica.class ), pipelineReplica1 = mock( PipelineReplica.class );
        final PipelineReplicaId pipelineReplicaId0 = new PipelineReplicaId( pipelineId, 0 );
        when( pipelineReplica0.id() ).thenReturn( pipelineReplicaId0 );
        final PipelineReplicaId pipelineReplicaId1 = new PipelineReplicaId( pipelineId, 1 );
        when( pipelineReplica1.id() ).thenReturn( pipelineReplicaId1 );
        final SchedulingStrategy schedulingStrategy = mock( SchedulingStrategy.class );
        final SchedulingStrategy[] schedulingStrategies = new SchedulingStrategy[] { schedulingStrategy };
        final SchedulingStrategy[][] fusedSchedulingStrategies = new SchedulingStrategy[][] { { schedulingStrategy } };
        final UpstreamContext upstreamContext = mock( UpstreamContext.class );
        final UpstreamContext[] upstreamContexts = new UpstreamContext[] { upstreamContext };
        final UpstreamContext[][] fusedUpstreamContexts = new UpstreamContext[][] { { upstreamContext } };

        when( pipelineReplica0.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );
        when( pipelineReplica1.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );

        final RegionExecPlan regionExecPlan = mock( RegionExecPlan.class );
        when( regionExecPlan.getReplicaCount() ).thenReturn( 2 );
        when( regionExecPlan.getOperatorCountByPipelineStartIndex( pipelineId.getPipelineStartIndex() ) ).thenReturn( 1 );

        final Region region = mock( Region.class );
        when( region.getPipelineReplicas( pipelineId ) ).thenReturn( new PipelineReplica[] { pipelineReplica0, pipelineReplica1 } );
        when( region.getSchedulingStrategies( pipelineId ) ).thenReturn( schedulingStrategies );
        when( region.getFusedSchedulingStrategies( pipelineId ) ).thenReturn( fusedSchedulingStrategies );
        when( region.getUpstreamContexts( pipelineId ) ).thenReturn( upstreamContexts );
        when( region.getFusedUpstreamContexts( pipelineId ) ).thenReturn( fusedUpstreamContexts );
        when( region.getExecPlan() ).thenReturn( regionExecPlan );

        final Pipeline pipeline = new Pipeline( pipelineId, region );
        pipeline.init();

        final Supervisor supervisor = mock( Supervisor.class );
        when( supervisor.getUpstreamContext( pipelineReplicaId0 ) ).thenReturn( upstreamContexts[ 0 ] );
        when( supervisor.getUpstreamContext( pipelineReplicaId1 ) ).thenReturn( upstreamContexts[ 0 ] );

        pipeline.startPipelineReplicaRunners( new JokerConfig(), supervisor, new ThreadGroup( "test" ) );

        pipeline.stopPipelineReplicaRunners( TimeUnit.SECONDS.toMillis( 30 ) );
    }

    @Test
    public void shouldNotStartPipelineReplicaRunnersMultipleTimes ()
    {
        final PipelineReplica pipelineReplica0 = mock( PipelineReplica.class ), pipelineReplica1 = mock( PipelineReplica.class );
        final PipelineReplicaId pipelineReplicaId0 = new PipelineReplicaId( pipelineId, 0 );
        when( pipelineReplica0.id() ).thenReturn( pipelineReplicaId0 );
        final PipelineReplicaId pipelineReplicaId1 = new PipelineReplicaId( pipelineId, 1 );
        when( pipelineReplica1.id() ).thenReturn( pipelineReplicaId1 );
        final SchedulingStrategy schedulingStrategy = mock( SchedulingStrategy.class );
        final SchedulingStrategy[] schedulingStrategies = new SchedulingStrategy[] { schedulingStrategy };
        final SchedulingStrategy[][] fusedSchedulingStrategies = new SchedulingStrategy[][] { { schedulingStrategy } };
        final UpstreamContext upstreamContext = mock( UpstreamContext.class );
        final UpstreamContext[] upstreamContexts = new UpstreamContext[] { upstreamContext };
        final UpstreamContext[][] fusedUpstreamContexts = new UpstreamContext[][] { { upstreamContext } };

        when( pipelineReplica0.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );
        when( pipelineReplica1.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );

        final RegionExecPlan regionExecPlan = mock( RegionExecPlan.class );
        when( regionExecPlan.getReplicaCount() ).thenReturn( 2 );
        when( regionExecPlan.getOperatorCountByPipelineStartIndex( pipelineId.getPipelineStartIndex() ) ).thenReturn( 1 );

        final Region region = mock( Region.class );
        when( region.getPipelineReplicas( pipelineId ) ).thenReturn( new PipelineReplica[] { pipelineReplica0, pipelineReplica1 } );
        when( region.getSchedulingStrategies( pipelineId ) ).thenReturn( schedulingStrategies );
        when( region.getFusedSchedulingStrategies( pipelineId ) ).thenReturn( fusedSchedulingStrategies );
        when( region.getUpstreamContexts( pipelineId ) ).thenReturn( upstreamContexts );
        when( region.getFusedUpstreamContexts( pipelineId ) ).thenReturn( fusedUpstreamContexts );
        when( region.getExecPlan() ).thenReturn( regionExecPlan );

        final Pipeline pipeline = new Pipeline( pipelineId, region );
        pipeline.init();

        final Supervisor supervisor = mock( Supervisor.class );
        when( supervisor.getUpstreamContext( pipelineReplicaId0 ) ).thenReturn( upstreamContexts[ 0 ] );
        when( supervisor.getUpstreamContext( pipelineReplicaId1 ) ).thenReturn( upstreamContexts[ 0 ] );

        pipeline.startPipelineReplicaRunners( new JokerConfig(), supervisor, new ThreadGroup( "test" ) );
        try
        {
            pipeline.startPipelineReplicaRunners( new JokerConfig(), supervisor, new ThreadGroup( "test" ) );
            fail();
        }
        catch ( IllegalStateException ignored )
        {
        }

        pipeline.stopPipelineReplicaRunners( TimeUnit.SECONDS.toMillis( 30 ) );
    }

    @Test( expected = IllegalStateException.class )
    public void shouldNotStopPipelineReplicaRunnersIfNotStarted ()
    {
        final PipelineReplica pipelineReplica0 = mock( PipelineReplica.class ), pipelineReplica1 = mock( PipelineReplica.class );
        final PipelineReplicaId pipelineReplicaId0 = new PipelineReplicaId( pipelineId, 0 );
        when( pipelineReplica0.id() ).thenReturn( pipelineReplicaId0 );
        final PipelineReplicaId pipelineReplicaId1 = new PipelineReplicaId( pipelineId, 1 );
        when( pipelineReplica1.id() ).thenReturn( pipelineReplicaId1 );
        final SchedulingStrategy schedulingStrategy = mock( SchedulingStrategy.class );
        final SchedulingStrategy[] schedulingStrategies = new SchedulingStrategy[] { schedulingStrategy };
        final SchedulingStrategy[][] fusedSchedulingStrategies = new SchedulingStrategy[][] { { schedulingStrategy } };
        final UpstreamContext upstreamContext = mock( UpstreamContext.class );
        final UpstreamContext[] upstreamContexts = new UpstreamContext[] { upstreamContext };
        final UpstreamContext[][] fusedUpstreamContexts = new UpstreamContext[][] { { upstreamContext } };

        when( pipelineReplica0.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );
        when( pipelineReplica1.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );

        final RegionExecPlan regionExecPlan = mock( RegionExecPlan.class );
        when( regionExecPlan.getReplicaCount() ).thenReturn( 2 );
        when( regionExecPlan.getOperatorCountByPipelineStartIndex( pipelineId.getPipelineStartIndex() ) ).thenReturn( 1 );

        final Region region = mock( Region.class );
        when( region.getPipelineReplicas( pipelineId ) ).thenReturn( new PipelineReplica[] { pipelineReplica0, pipelineReplica1 } );
        when( region.getSchedulingStrategies( pipelineId ) ).thenReturn( schedulingStrategies );
        when( region.getFusedSchedulingStrategies( pipelineId ) ).thenReturn( fusedSchedulingStrategies );
        when( region.getUpstreamContexts( pipelineId ) ).thenReturn( upstreamContexts );
        when( region.getFusedUpstreamContexts( pipelineId ) ).thenReturn( fusedUpstreamContexts );
        when( region.getExecPlan() ).thenReturn( regionExecPlan );

        final Pipeline pipeline = new Pipeline( pipelineId, region );
        pipeline.init();

        pipeline.stopPipelineReplicaRunners( TimeUnit.SECONDS.toMillis( 30 ) );
    }

    @Test
    public void shouldNotShutdownOperatorsIfRunnersNotStopped ()
    {
        final PipelineReplica pipelineReplica0 = mock( PipelineReplica.class ), pipelineReplica1 = mock( PipelineReplica.class );
        final PipelineReplicaId pipelineReplicaId0 = new PipelineReplicaId( pipelineId, 0 );
        when( pipelineReplica0.id() ).thenReturn( pipelineReplicaId0 );
        final PipelineReplicaId pipelineReplicaId1 = new PipelineReplicaId( pipelineId, 1 );
        when( pipelineReplica1.id() ).thenReturn( pipelineReplicaId1 );
        final SchedulingStrategy schedulingStrategy = mock( SchedulingStrategy.class );
        final SchedulingStrategy[] schedulingStrategies = new SchedulingStrategy[] { schedulingStrategy };
        final SchedulingStrategy[][] fusedSchedulingStrategies = new SchedulingStrategy[][] { { schedulingStrategy } };
        final UpstreamContext upstreamContext = mock( UpstreamContext.class );
        final UpstreamContext[] upstreamContexts = new UpstreamContext[] { upstreamContext };
        final UpstreamContext[][] fusedUpstreamContexts = new UpstreamContext[][] { { upstreamContext } };

        when( pipelineReplica0.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );
        when( pipelineReplica1.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );

        final RegionExecPlan regionExecPlan = mock( RegionExecPlan.class );
        when( regionExecPlan.getReplicaCount() ).thenReturn( 2 );
        when( regionExecPlan.getOperatorCountByPipelineStartIndex( pipelineId.getPipelineStartIndex() ) ).thenReturn( 1 );

        final Region region = mock( Region.class );
        when( region.getPipelineReplicas( pipelineId ) ).thenReturn( new PipelineReplica[] { pipelineReplica0, pipelineReplica1 } );
        when( region.getSchedulingStrategies( pipelineId ) ).thenReturn( schedulingStrategies );
        when( region.getFusedSchedulingStrategies( pipelineId ) ).thenReturn( fusedSchedulingStrategies );
        when( region.getUpstreamContexts( pipelineId ) ).thenReturn( upstreamContexts );
        when( region.getFusedUpstreamContexts( pipelineId ) ).thenReturn( fusedUpstreamContexts );
        when( region.getExecPlan() ).thenReturn( regionExecPlan );

        final Pipeline pipeline = new Pipeline( pipelineId, region );
        pipeline.init();

        final Supervisor supervisor = mock( Supervisor.class );
        when( supervisor.getUpstreamContext( pipelineReplicaId0 ) ).thenReturn( upstreamContexts[ 0 ] );
        when( supervisor.getUpstreamContext( pipelineReplicaId1 ) ).thenReturn( upstreamContexts[ 0 ] );

        pipeline.startPipelineReplicaRunners( new JokerConfig(), supervisor, new ThreadGroup( "test" ) );
        try
        {
            pipeline.shutdown();
            fail();
        }
        catch ( Exception e )
        {
            pipeline.stopPipelineReplicaRunners( TimeUnit.SECONDS.toMillis( 30 ) );
        }
    }

    @Test
    public void shouldCompleteRunnerWhenUpstreamContextIsUpdated ()
    {
        final PipelineReplica pipelineReplica0 = mock( PipelineReplica.class ), pipelineReplica1 = mock( PipelineReplica.class );
        final PipelineReplicaId pipelineReplicaId0 = new PipelineReplicaId( pipelineId, 0 );
        when( pipelineReplica0.id() ).thenReturn( pipelineReplicaId0 );
        final PipelineReplicaId pipelineReplicaId1 = new PipelineReplicaId( pipelineId, 1 );
        when( pipelineReplica1.id() ).thenReturn( pipelineReplicaId1 );
        final SchedulingStrategy schedulingStrategy = mock( SchedulingStrategy.class );
        final SchedulingStrategy[] schedulingStrategies = new SchedulingStrategy[] { schedulingStrategy };
        final SchedulingStrategy[][] fusedSchedulingStrategies = new SchedulingStrategy[][] { { schedulingStrategy } };
        final UpstreamContext upstreamContext = mock( UpstreamContext.class );
        final UpstreamContext[] upstreamContexts = new UpstreamContext[] { upstreamContext };
        final UpstreamContext[][] fusedUpstreamContexts = new UpstreamContext[][] { { upstreamContext } };

        when( pipelineReplica0.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );
        when( pipelineReplica1.getStatus() ).thenReturn( OperatorReplicaStatus.INITIAL );

        final RegionExecPlan regionExecPlan = mock( RegionExecPlan.class );
        when( regionExecPlan.getReplicaCount() ).thenReturn( 2 );
        when( regionExecPlan.getOperatorCountByPipelineStartIndex( pipelineId.getPipelineStartIndex() ) ).thenReturn( 1 );

        final Region region = mock( Region.class );
        when( region.getPipelineReplicas( pipelineId ) ).thenReturn( new PipelineReplica[] { pipelineReplica0, pipelineReplica1 } );
        when( region.getSchedulingStrategies( pipelineId ) ).thenReturn( schedulingStrategies );
        when( region.getFusedSchedulingStrategies( pipelineId ) ).thenReturn( fusedSchedulingStrategies );
        when( region.getUpstreamContexts( pipelineId ) ).thenReturn( upstreamContexts );
        when( region.getFusedUpstreamContexts( pipelineId ) ).thenReturn( fusedUpstreamContexts );
        when( region.getExecPlan() ).thenReturn( regionExecPlan );

        final Pipeline pipeline = new Pipeline( pipelineId, region );
        pipeline.init();

        final Supervisor supervisor = mock( Supervisor.class );
        when( supervisor.getUpstreamContext( pipelineReplicaId0 ) ).thenReturn( upstreamContexts[ 0 ] );
        when( supervisor.getUpstreamContext( pipelineReplicaId1 ) ).thenReturn( upstreamContexts[ 0 ] );

        final AtomicBoolean completed = new AtomicBoolean( false );
        when( pipelineReplica0.isCompleted() ).thenAnswer( (Answer<Boolean>) invocation -> completed.get() );

        when( pipelineReplica1.isCompleted() ).thenAnswer( (Answer<Boolean>) invocation -> completed.get() );

        pipeline.startPipelineReplicaRunners( new JokerConfig(), supervisor, new ThreadGroup( "test" ) );

        final UpstreamContext newUpstreamContext = mock( UpstreamContext.class );
        when( newUpstreamContext.getVersion() ).thenReturn( INITIAL_VERSION + 1 );
        reset( supervisor );
        when( supervisor.getUpstreamContext( pipelineReplicaId0 ) ).thenReturn( newUpstreamContext );
        when( supervisor.getUpstreamContext( pipelineReplicaId1 ) ).thenReturn( newUpstreamContext );

        final OperatorDef operatorDef = mock( OperatorDef.class );
        when( regionExecPlan.getOperatorDefsByPipelineStartIndex( pipelineId.getPipelineStartIndex() ) ).thenReturn( new OperatorDef[] {
                operatorDef } );

        pipeline.handleUpstreamContextUpdated( newUpstreamContext );

        assertTrueEventually( () -> {
            verify( pipelineReplica0 ).setUpstreamContext( newUpstreamContext );
            verify( pipelineReplica1 ).setUpstreamContext( newUpstreamContext );
        } );

        verify( newUpstreamContext ).isInvokable( operatorDef, schedulingStrategies[ 0 ] );

        completed.set( true );

        assertTrueEventually( () -> {
            verify( supervisor ).notifyPipelineReplicaCompleted( pipelineReplicaId0 );
            verify( supervisor ).notifyPipelineReplicaCompleted( pipelineReplicaId1 );

            pipeline.handlePipelineReplicaCompleted( 0 );
            pipeline.handlePipelineReplicaCompleted( 1 );
        } );

        assertThat( pipeline.getPipelineStatus(), equalTo( COMPLETED ) );

        pipeline.stopPipelineReplicaRunners( TimeUnit.SECONDS.toMillis( 30 ) );
    }

}
