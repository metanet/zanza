package cs.bilkent.zanza.engine.pipeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static cs.bilkent.zanza.engine.TestUtils.assertTrueEventually;
import static cs.bilkent.zanza.engine.TestUtils.spawnThread;
import static cs.bilkent.zanza.engine.config.ThreadingPreference.MULTI_THREADED;
import static cs.bilkent.zanza.engine.config.ThreadingPreference.SINGLE_THREADED;
import cs.bilkent.zanza.engine.config.ZanzaConfig;
import cs.bilkent.zanza.engine.kvstore.KVStoreContext;
import cs.bilkent.zanza.engine.kvstore.impl.KVStoreContextManagerImpl;
import cs.bilkent.zanza.engine.partition.PartitionService;
import cs.bilkent.zanza.engine.partition.PartitionServiceImpl;
import cs.bilkent.zanza.engine.partition.impl.PartitionKeyFunctionFactoryImpl;
import static cs.bilkent.zanza.engine.pipeline.OperatorReplicaInitializationTest.withUpstreamConnectionStatus;
import static cs.bilkent.zanza.engine.pipeline.UpstreamConnectionStatus.ACTIVE;
import static cs.bilkent.zanza.engine.pipeline.UpstreamConnectionStatus.CLOSED;
import cs.bilkent.zanza.engine.pipeline.impl.tuplesupplier.CachedTuplesImplSupplier;
import cs.bilkent.zanza.engine.pipeline.impl.tuplesupplier.NonCachedTuplesImplSupplier;
import cs.bilkent.zanza.engine.supervisor.Supervisor;
import cs.bilkent.zanza.engine.tuplequeue.TupleQueue;
import cs.bilkent.zanza.engine.tuplequeue.TupleQueueContext;
import cs.bilkent.zanza.engine.tuplequeue.TupleQueueDrainerPool;
import cs.bilkent.zanza.engine.tuplequeue.impl.TupleQueueContextManagerImpl;
import cs.bilkent.zanza.engine.tuplequeue.impl.context.EmptyTupleQueueContext;
import cs.bilkent.zanza.engine.tuplequeue.impl.context.PartitionedTupleQueueContext;
import cs.bilkent.zanza.engine.tuplequeue.impl.drainer.pool.BlockingTupleQueueDrainerPool;
import cs.bilkent.zanza.engine.tuplequeue.impl.drainer.pool.NonBlockingTupleQueueDrainerPool;
import cs.bilkent.zanza.engine.tuplequeue.impl.queue.MultiThreadedTupleQueue;
import cs.bilkent.zanza.flow.OperatorDef;
import cs.bilkent.zanza.flow.OperatorDefBuilder;
import cs.bilkent.zanza.operator.InitializationContext;
import cs.bilkent.zanza.operator.InvocationContext;
import cs.bilkent.zanza.operator.Operator;
import cs.bilkent.zanza.operator.OperatorConfig;
import cs.bilkent.zanza.operator.Tuple;
import cs.bilkent.zanza.operator.Tuples;
import cs.bilkent.zanza.operator.impl.TuplesImpl;
import cs.bilkent.zanza.operator.kvstore.KVStore;
import cs.bilkent.zanza.operator.scheduling.ScheduleWhenAvailable;
import static cs.bilkent.zanza.operator.scheduling.ScheduleWhenTuplesAvailable.TupleAvailabilityByCount.EXACT;
import static cs.bilkent.zanza.operator.scheduling.ScheduleWhenTuplesAvailable.scheduleWhenTuplesAvailableOnAny;
import static cs.bilkent.zanza.operator.scheduling.ScheduleWhenTuplesAvailable.scheduleWhenTuplesAvailableOnDefaultPort;
import cs.bilkent.zanza.operator.scheduling.SchedulingStrategy;
import cs.bilkent.zanza.operator.schema.annotation.OperatorSchema;
import cs.bilkent.zanza.operator.schema.annotation.PortSchema;
import static cs.bilkent.zanza.operator.schema.annotation.PortSchemaScope.EXACT_FIELD_SET;
import cs.bilkent.zanza.operator.schema.annotation.SchemaField;
import cs.bilkent.zanza.operator.spec.OperatorSpec;
import static cs.bilkent.zanza.operator.spec.OperatorType.PARTITIONED_STATEFUL;
import static cs.bilkent.zanza.operator.spec.OperatorType.STATEFUL;
import static cs.bilkent.zanza.operator.spec.OperatorType.STATELESS;
import cs.bilkent.zanza.operators.FilterOperator;
import cs.bilkent.zanza.operators.MapperOperator;
import cs.bilkent.zanza.utils.Pair;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TODO clean up duplicate code
public class PipelineIntegrationTest
{

    private static final int REGION_ID = 1;

    private static final int REPLICA_INDEX = 1;


    private final ZanzaConfig zanzaConfig = new ZanzaConfig();

    private TupleQueueContextManagerImpl tupleQueueContextManager;

    private KVStoreContextManagerImpl kvStoreContextManager;

    private final KVStoreContext nopKvStoreContext = mock( KVStoreContext.class );

    private final PipelineReplicaId pipelineReplicaId1 = new PipelineReplicaId( new PipelineId( 0, 0 ), 0 );

    private final PipelineReplicaId pipelineReplicaId2 = new PipelineReplicaId( new PipelineId( 1, 0 ), 0 );

    private final PipelineReplicaId pipelineReplicaId3 = new PipelineReplicaId( new PipelineId( 2, 0 ), 0 );

    @Before
    public void init ()
    {
        final PartitionService partitionService = new PartitionServiceImpl( zanzaConfig );
        tupleQueueContextManager = new TupleQueueContextManagerImpl( zanzaConfig, partitionService, new PartitionKeyFunctionFactoryImpl() );
        kvStoreContextManager = new KVStoreContextManagerImpl( partitionService );
    }

    @Test
    public void testPipelineWithSingleOperator () throws ExecutionException, InterruptedException
    {
        final OperatorConfig mapperOperatorConfig = new OperatorConfig();
        final Function<Tuple, Tuple> multiplyBy2 = tuple -> new Tuple( "val", 2 * tuple.getIntegerValueOrDefault( "val", 0 ) );
        mapperOperatorConfig.set( MapperOperator.MAPPER_CONFIG_PARAMETER, multiplyBy2 );
        final OperatorDef mapperOperatorDef = OperatorDefBuilder.newInstance( "map", MapperOperator.class )
                                                                .setConfig( mapperOperatorConfig )
                                                                .build();

        final TupleQueueContext tupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                             REPLICA_INDEX,
                                                                                                             mapperOperatorDef,
                                                                                                             MULTI_THREADED );
        final TupleQueueDrainerPool drainerPool = new BlockingTupleQueueDrainerPool( zanzaConfig, mapperOperatorDef );
        final Supplier<TuplesImpl> tuplesImplSupplier = new NonCachedTuplesImplSupplier( mapperOperatorDef.outputPortCount() );

        final OperatorReplica operator = new OperatorReplica( pipelineReplicaId1,
                                                              mapperOperatorDef,
                                                              tupleQueueContext,
                                                              nopKvStoreContext,
                                                              drainerPool,
                                                              tuplesImplSupplier );
        final PipelineReplica pipeline = new PipelineReplica( zanzaConfig,
                                                              pipelineReplicaId1,
                                                              new OperatorReplica[] { operator },
                                                              new EmptyTupleQueueContext( "map", mapperOperatorDef.inputPortCount() ) );
        final Supervisor supervisor = mock( Supervisor.class );
        final SupervisorNotifier supervisorNotifier = new SupervisorNotifier( supervisor, pipeline );

        pipeline.init( new UpstreamContext( 0, new UpstreamConnectionStatus[] { ACTIVE } ), supervisorNotifier );

        final TupleCollectorDownstreamTupleSender tupleCollector = new TupleCollectorDownstreamTupleSender( mapperOperatorDef
                                                                                                                    .outputPortCount() );
        final PipelineReplicaRunner runner = new PipelineReplicaRunner( zanzaConfig,
                                                                        pipeline,
                                                                        supervisor,
                                                                        supervisorNotifier,
                                                                        tupleCollector );

        final Thread runnerThread = spawnThread( runner );

        final int initialVal = 1 + new Random().nextInt( 98 );
        final int tupleCount = 200;
        for ( int i = 0; i < tupleCount; i++ )
        {
            tupleQueueContext.offer( 0, singletonList( new Tuple( "val", initialVal + i ) ) );
        }

        assertTrueEventually( () -> assertEquals( tupleCount, tupleCollector.tupleQueues[ 0 ].size() ) );
        final List<Tuple> tuples = tupleCollector.tupleQueues[ 0 ].pollTuplesAtLeast( 1 );
        for ( int i = 0; i < tupleCount; i++ )
        {
            final Tuple expected = multiplyBy2.apply( new Tuple( "val", initialVal + i ) );
            assertEquals( expected, tuples.get( i ) );
        }

        final UpstreamContext updatedUpstreamContext = new UpstreamContext( 1, new UpstreamConnectionStatus[] { CLOSED } );
        when( supervisor.getUpstreamContext( pipelineReplicaId1 ) ).thenReturn( updatedUpstreamContext );
        runner.updatePipelineUpstreamContext();
        runnerThread.join();
    }

    @Test
    public void testPipelineWithMultipleOperators_pipelineUpstreamClosed () throws ExecutionException, InterruptedException
    {
        final OperatorConfig mapperOperatorConfig = new OperatorConfig();
        final Function<Tuple, Tuple> add1 = tuple -> new Tuple( "val", 1 + tuple.getIntegerValueOrDefault( "val", -1 ) );
        mapperOperatorConfig.set( MapperOperator.MAPPER_CONFIG_PARAMETER, add1 );
        final OperatorDef mapperOperatorDef = OperatorDefBuilder.newInstance( "map", MapperOperator.class )
                                                                .setConfig( mapperOperatorConfig )
                                                                .build();

        final TupleQueueContext mapperTupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                   REPLICA_INDEX,
                                                                                                                   mapperOperatorDef,
                                                                                                                   MULTI_THREADED );

        final TupleQueueDrainerPool mapperDrainerPool = new BlockingTupleQueueDrainerPool( zanzaConfig, mapperOperatorDef );
        final Supplier<TuplesImpl> mapperTuplesImplSupplier = new CachedTuplesImplSupplier( mapperOperatorDef.outputPortCount() );

        final OperatorReplica mapperOperator = new OperatorReplica( pipelineReplicaId1,
                                                                    mapperOperatorDef,
                                                                    mapperTupleQueueContext,
                                                                    nopKvStoreContext,
                                                                    mapperDrainerPool,
                                                                    mapperTuplesImplSupplier );

        final OperatorConfig filterOperatorConfig = new OperatorConfig();
        final Predicate<Tuple> filterEvenVals = tuple -> tuple.getInteger( "val" ) % 2 == 0;
        filterOperatorConfig.set( FilterOperator.PREDICATE_CONFIG_PARAMETER, filterEvenVals );
        final OperatorDef filterOperatorDef = OperatorDefBuilder.newInstance( "filter", FilterOperator.class )
                                                                .setConfig( filterOperatorConfig )
                                                                .build();

        final TupleQueueContext filterTupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                   REPLICA_INDEX,
                                                                                                                   filterOperatorDef,
                                                                                                                   SINGLE_THREADED );

        final TupleQueueDrainerPool filterDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, filterOperatorDef );
        final Supplier<TuplesImpl> filterTuplesImplSupplier = new NonCachedTuplesImplSupplier( filterOperatorDef.inputPortCount() );

        final OperatorReplica filterOperator = new OperatorReplica( pipelineReplicaId1,
                                                                    filterOperatorDef,
                                                                    filterTupleQueueContext,
                                                                    nopKvStoreContext,
                                                                    filterDrainerPool,
                                                                    filterTuplesImplSupplier );

        final PipelineReplica pipeline = new PipelineReplica( zanzaConfig,
                                                              pipelineReplicaId1,
                                                              new OperatorReplica[] { mapperOperator, filterOperator },
                                                              new EmptyTupleQueueContext( "map", mapperOperatorDef.inputPortCount() ) );

        final Supervisor supervisor = mock( Supervisor.class );
        final SupervisorNotifier supervisorNotifier = new SupervisorNotifier( supervisor, pipeline );

        pipeline.init( new UpstreamContext( 0, new UpstreamConnectionStatus[] { ACTIVE } ), supervisorNotifier );

        final TupleCollectorDownstreamTupleSender tupleCollector = new TupleCollectorDownstreamTupleSender( filterOperatorDef
                                                                                                                    .outputPortCount() );

        final PipelineReplicaRunner runner = new PipelineReplicaRunner( zanzaConfig,
                                                                        pipeline,
                                                                        supervisor,
                                                                        supervisorNotifier,
                                                                        tupleCollector );

        final Thread runnerThread = spawnThread( runner );

        final int initialVal = 2 + 2 * new Random().nextInt( 98 );
        final int tupleCount = 200;

        for ( int i = 0; i < tupleCount; i++ )
        {
            final int value = initialVal + i;
            final Tuple tuple = new Tuple( "val", value );
            mapperTupleQueueContext.offer( 0, singletonList( tuple ) );
        }

        final int evenValCount = tupleCount / 2;
        assertTrueEventually( () -> assertEquals( evenValCount, tupleCollector.tupleQueues[ 0 ].size() ) );
        final List<Tuple> tuples = tupleCollector.tupleQueues[ 0 ].pollTuplesAtLeast( 1 );
        for ( int i = 0; i < evenValCount; i++ )
        {
            final Tuple expected = add1.apply( new Tuple( "val", initialVal + ( i * 2 ) ) );
            if ( filterEvenVals.test( expected ) )
            {
                assertEquals( expected, tuples.get( i ) );
            }
        }

        final UpstreamContext updatedUpstreamContext = new UpstreamContext( 1, new UpstreamConnectionStatus[] { CLOSED } );
        when( supervisor.getUpstreamContext( pipelineReplicaId1 ) ).thenReturn( updatedUpstreamContext );
        runner.updatePipelineUpstreamContext();
        runnerThread.join();
    }

    @Test
    public void testPipelineWithMultipleOperators_pipelineUpstreamClosed_0inputOperator () throws InterruptedException
    {
        final int batchCount = 4;

        final OperatorConfig generatorOperatorConfig = new OperatorConfig();
        generatorOperatorConfig.set( "batchCount", batchCount );

        final OperatorDef generatorOperatorDef = OperatorDefBuilder.newInstance( "generator", ValueGeneratorOperator.class )
                                                                   .setConfig( generatorOperatorConfig )
                                                                   .build();

        final TupleQueueContext generatorTupleQueueContext = new EmptyTupleQueueContext( generatorOperatorDef.id(),
                                                                                         generatorOperatorDef.inputPortCount() );

        final TupleQueueDrainerPool generatorDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, generatorOperatorDef );
        final Supplier<TuplesImpl> generatorTuplesImplSupplier = new CachedTuplesImplSupplier( generatorOperatorDef.outputPortCount() );

        final OperatorReplica generatorOperator = new OperatorReplica( pipelineReplicaId1,
                                                                       generatorOperatorDef,
                                                                       generatorTupleQueueContext,
                                                                       nopKvStoreContext,
                                                                       generatorDrainerPool,
                                                                       generatorTuplesImplSupplier );

        final OperatorConfig passerOperatorConfig = new OperatorConfig();
        passerOperatorConfig.set( "batchCount", batchCount / 2 );

        final OperatorDef passerOperatorDef = OperatorDefBuilder.newInstance( "passer", ValuePasserOperator.class )
                                                                .setConfig( passerOperatorConfig )
                                                                .build();

        final TupleQueueContext passerTupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                   REPLICA_INDEX,
                                                                                                                   passerOperatorDef,
                                                                                                                   SINGLE_THREADED );

        final TupleQueueDrainerPool passerDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, passerOperatorDef );
        final Supplier<TuplesImpl> passerTuplesImplSupplier = new CachedTuplesImplSupplier( passerOperatorDef.outputPortCount() );

        final OperatorReplica passerOperator = new OperatorReplica( pipelineReplicaId1,
                                                                    passerOperatorDef,
                                                                    passerTupleQueueContext,
                                                                    nopKvStoreContext,
                                                                    passerDrainerPool,
                                                                    passerTuplesImplSupplier );

        final KVStoreContext[] kvStoreContexts = kvStoreContextManager.createPartitionedKVStoreContexts( REGION_ID, 1, "state" );

        final OperatorDef stateOperatorDef = OperatorDefBuilder.newInstance( "state", ValueStateOperator.class )
                                                               .setPartitionFieldNames( singletonList( "val" ) )
                                                               .build();

        final PartitionedTupleQueueContext[] stateTupleQueueContexts = tupleQueueContextManager.createPartitionedTupleQueueContext(
                REGION_ID,
                1,
                stateOperatorDef );
        final PartitionedTupleQueueContext stateTupleQueueContext = stateTupleQueueContexts[ 0 ];

        final TupleQueueDrainerPool stateDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, stateOperatorDef );
        final Supplier<TuplesImpl> stateTuplesImplSupplier = new CachedTuplesImplSupplier( stateOperatorDef.outputPortCount() );

        final OperatorReplica stateOperator = new OperatorReplica( pipelineReplicaId1,
                                                                   stateOperatorDef,
                                                                   stateTupleQueueContext,
                                                                   kvStoreContexts[ 0 ],
                                                                   stateDrainerPool,
                                                                   stateTuplesImplSupplier );

        final PipelineReplica pipeline = new PipelineReplica( zanzaConfig,
                                                              pipelineReplicaId1,
                                                              new OperatorReplica[] { generatorOperator, passerOperator, stateOperator },
                                                              new EmptyTupleQueueContext( "generator",
                                                                                          generatorOperatorDef.inputPortCount() ) );

        final Supervisor supervisor = mock( Supervisor.class );
        final SupervisorNotifier supervisorNotifier = new SupervisorNotifier( supervisor, pipeline );

        pipeline.init( new UpstreamContext( 0, new UpstreamConnectionStatus[] {} ), supervisorNotifier );

        final PipelineReplicaRunner runner = new PipelineReplicaRunner( zanzaConfig,
                                                                        pipeline,
                                                                        supervisor,
                                                                        supervisorNotifier,
                                                                        mock( DownstreamTupleSender.class ) );

        final Thread runnerThread = spawnThread( runner );

        final ValueGeneratorOperator generatorOp = (ValueGeneratorOperator) generatorOperator.getOperator();
        generatorOp.start = true;

        assertTrueEventually( () -> assertTrue( generatorOp.count > 1000 ) );

        final UpstreamContext updatedUpstreamContext = new UpstreamContext( 1, new UpstreamConnectionStatus[] {} );
        when( supervisor.getUpstreamContext( pipelineReplicaId1 ) ).thenReturn( updatedUpstreamContext );
        runner.updatePipelineUpstreamContext();

        assertTrueEventually( () -> verify( supervisor ).notifyPipelineReplicaCompleted( pipelineReplicaId1 ) );

        runnerThread.join();

        final ValuePasserOperator passerOp = (ValuePasserOperator) passerOperator.getOperator();
        final ValueStateOperator stateOp = (ValueStateOperator) stateOperator.getOperator();

        assertEquals( generatorOp.count, passerOp.count );
        assertEquals( generatorOp.count, stateOp.count );
        assertEquals( generatorOp.count, getKVStoreTotalItemCount( REGION_ID, "state" ) );
    }

    @Test
    public void testMultiplePipelines_singleInputPort () throws ExecutionException, InterruptedException
    {
        final SupervisorImpl supervisor = new SupervisorImpl();
        supervisor.upstreamContexts.put( pipelineReplicaId1, new UpstreamContext( 0, new UpstreamConnectionStatus[] { ACTIVE } ) );
        supervisor.upstreamContexts.put( pipelineReplicaId2, new UpstreamContext( 0, new UpstreamConnectionStatus[] { ACTIVE } ) );
        supervisor.inputPortIndices.put( pipelineReplicaId1, 0 );
        supervisor.inputPortIndices.put( pipelineReplicaId2, 0 );

        final OperatorConfig mapperOperatorConfig = new OperatorConfig();
        final Function<Tuple, Tuple> add1 = tuple -> new Tuple( "val", 1 + tuple.getIntegerValueOrDefault( "val", -1 ) );
        mapperOperatorConfig.set( MapperOperator.MAPPER_CONFIG_PARAMETER, add1 );
        final OperatorDef mapperOperatorDef = OperatorDefBuilder.newInstance( "map", MapperOperator.class )
                                                                .setConfig( mapperOperatorConfig )
                                                                .build();

        final TupleQueueContext mapperTupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                   REPLICA_INDEX,
                                                                                                                   mapperOperatorDef,
                                                                                                                   MULTI_THREADED );

        final TupleQueueDrainerPool mapperDrainerPool = new BlockingTupleQueueDrainerPool( zanzaConfig, mapperOperatorDef );
        final Supplier<TuplesImpl> mapperTuplesImplSupplier = new NonCachedTuplesImplSupplier( mapperOperatorDef.outputPortCount() );

        final OperatorReplica mapperOperator = new OperatorReplica( pipelineReplicaId1,
                                                                    mapperOperatorDef,
                                                                    mapperTupleQueueContext,
                                                                    nopKvStoreContext,
                                                                    mapperDrainerPool,
                                                                    mapperTuplesImplSupplier );

        final PipelineReplica pipeline1 = new PipelineReplica( zanzaConfig,
                                                               pipelineReplicaId1,
                                                               new OperatorReplica[] { mapperOperator },
                                                               new EmptyTupleQueueContext( "map", mapperOperatorDef.inputPortCount() ) );
        final SupervisorNotifier supervisorNotifier1 = new SupervisorNotifier( supervisor, pipeline1 );

        pipeline1.init( supervisor.upstreamContexts.get( pipelineReplicaId1 ), supervisorNotifier1 );

        final OperatorConfig filterOperatorConfig = new OperatorConfig();
        final Predicate<Tuple> filterEvenVals = tuple -> tuple.getInteger( "val" ) % 2 == 0;
        filterOperatorConfig.set( FilterOperator.PREDICATE_CONFIG_PARAMETER, filterEvenVals );
        final OperatorDef filterOperatorDef = OperatorDefBuilder.newInstance( "filter", FilterOperator.class )
                                                                .setConfig( filterOperatorConfig )
                                                                .build();

        final TupleQueueContext filterTupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                   REPLICA_INDEX,
                                                                                                                   filterOperatorDef,
                                                                                                                   MULTI_THREADED );

        final DownstreamTupleSenderImpl tupleSender = new DownstreamTupleSenderImpl( filterTupleQueueContext,
                                                                                     new Pair[] { Pair.of( 0, 0 ) } );

        final TupleQueueDrainerPool filterDrainerPool = new BlockingTupleQueueDrainerPool( zanzaConfig, filterOperatorDef );
        final Supplier<TuplesImpl> filterTuplesImplSupplier = new NonCachedTuplesImplSupplier( filterOperatorDef.inputPortCount() );

        final OperatorReplica filterOperator = new OperatorReplica( pipelineReplicaId2,
                                                                    filterOperatorDef,
                                                                    filterTupleQueueContext,
                                                                    nopKvStoreContext,
                                                                    filterDrainerPool,
                                                                    filterTuplesImplSupplier );

        final PipelineReplica pipeline2 = new PipelineReplica( zanzaConfig,
                                                               pipelineReplicaId2,
                                                               new OperatorReplica[] { filterOperator },
                                                               new EmptyTupleQueueContext( "filter", filterOperatorDef.inputPortCount() ) );
        final SupervisorNotifier supervisorNotifier2 = new SupervisorNotifier( supervisor, pipeline2 );

        pipeline2.init( supervisor.upstreamContexts.get( pipelineReplicaId2 ), supervisorNotifier2 );

        final PipelineReplicaRunner runner1 = new PipelineReplicaRunner( zanzaConfig,
                                                                         pipeline1,
                                                                         supervisor,
                                                                         supervisorNotifier1,
                                                                         tupleSender );

        final TupleCollectorDownstreamTupleSender tupleCollector2 = new TupleCollectorDownstreamTupleSender( filterOperatorDef
                                                                                                                     .outputPortCount() );

        final PipelineReplicaRunner runner2 = new PipelineReplicaRunner( zanzaConfig,
                                                                         pipeline2,
                                                                         supervisor,
                                                                         supervisorNotifier2,
                                                                         tupleCollector2 );

        supervisor.targetPipelineReplicaId = pipelineReplicaId2;
        supervisor.runner = runner2;

        final Thread runnerThread1 = spawnThread( runner1 );
        final Thread runnerThread2 = spawnThread( runner2 );

        final int initialVal = 2 + 2 * new Random().nextInt( 98 );
        final int tupleCount = 200;

        for ( int i = 0; i < tupleCount; i++ )
        {
            final int value = initialVal + i;
            final Tuple tuple = new Tuple( "val", value );
            mapperTupleQueueContext.offer( 0, singletonList( tuple ) );
        }

        final int evenValCount = tupleCount / 2;
        assertTrueEventually( () -> assertEquals( evenValCount, tupleCollector2.tupleQueues[ 0 ].size() ) );
        final List<Tuple> tuples = tupleCollector2.tupleQueues[ 0 ].pollTuplesAtLeast( 1 );
        for ( int i = 0; i < evenValCount; i++ )
        {
            final Tuple expected = add1.apply( new Tuple( "val", initialVal + ( i * 2 ) ) );
            if ( filterEvenVals.test( expected ) )
            {
                assertEquals( expected, tuples.get( i ) );
            }
        }

        supervisor.upstreamContexts.put( pipelineReplicaId1, new UpstreamContext( 1, new UpstreamConnectionStatus[] { CLOSED } ) );
        runner1.updatePipelineUpstreamContext();
        runnerThread1.join();
        runnerThread2.join();
        assertTrue( supervisor.completedPipelines.contains( pipelineReplicaId1 ) );
        assertTrue( supervisor.completedPipelines.contains( pipelineReplicaId2 ) );
    }

    @Test
    public void testMultiplePipelines_multipleInputPorts () throws InterruptedException
    {
        final SupervisorImpl supervisor = new SupervisorImpl();
        supervisor.upstreamContexts.put( pipelineReplicaId1, new UpstreamContext( 0, new UpstreamConnectionStatus[] {} ) );
        supervisor.upstreamContexts.put( pipelineReplicaId2, new UpstreamContext( 0, new UpstreamConnectionStatus[] {} ) );
        supervisor.upstreamContexts.put( pipelineReplicaId3, new UpstreamContext( 0, new UpstreamConnectionStatus[] { ACTIVE, ACTIVE } ) );
        supervisor.inputPortIndices.put( pipelineReplicaId1, 0 );
        supervisor.inputPortIndices.put( pipelineReplicaId2, 1 );

        final int batchCount = 4;

        final OperatorConfig generatorOperatorConfig1 = new OperatorConfig();
        generatorOperatorConfig1.set( "batchCount", batchCount );

        final OperatorDef generatorOperatorDef1 = OperatorDefBuilder.newInstance( "generator1", ValueGeneratorOperator.class )
                                                                    .setConfig( generatorOperatorConfig1 )
                                                                    .build();

        final TupleQueueContext generatorTupleQueueContext1 = new EmptyTupleQueueContext( generatorOperatorDef1.id(),
                                                                                          generatorOperatorDef1.inputPortCount() );

        final TupleQueueDrainerPool generatorDrainerPool1 = new NonBlockingTupleQueueDrainerPool( zanzaConfig, generatorOperatorDef1 );
        final Supplier<TuplesImpl> generatorTuplesImplSupplier1 = new NonCachedTuplesImplSupplier( generatorOperatorDef1.outputPortCount
                                                                                                                                 () );

        final OperatorReplica generatorOperator1 = new OperatorReplica( pipelineReplicaId1,
                                                                        generatorOperatorDef1,
                                                                        generatorTupleQueueContext1,
                                                                        nopKvStoreContext,
                                                                        generatorDrainerPool1,
                                                                        generatorTuplesImplSupplier1 );

        final PipelineReplica pipeline1 = new PipelineReplica( zanzaConfig,
                                                               pipelineReplicaId1,
                                                               new OperatorReplica[] { generatorOperator1 },
                                                               new EmptyTupleQueueContext( "generator1",
                                                                                           generatorOperatorDef1.inputPortCount() ) );

        final SupervisorNotifier supervisorNotifier1 = new SupervisorNotifier( supervisor, pipeline1 );
        pipeline1.init( supervisor.upstreamContexts.get( pipelineReplicaId1 ), supervisorNotifier1 );

        final OperatorConfig generatorOperatorConfig2 = new OperatorConfig();
        generatorOperatorConfig2.set( "batchCount", batchCount );
        generatorOperatorConfig2.set( "increment", false );

        final OperatorDef generatorOperatorDef2 = OperatorDefBuilder.newInstance( "generator2", ValueGeneratorOperator.class )
                                                                    .setConfig( generatorOperatorConfig2 )
                                                                    .build();

        final TupleQueueContext generatorTupleQueueContext2 = new EmptyTupleQueueContext( generatorOperatorDef2.id(),
                                                                                          generatorOperatorDef2.inputPortCount() );

        final TupleQueueDrainerPool generatorDrainerPool2 = new NonBlockingTupleQueueDrainerPool( zanzaConfig, generatorOperatorDef2 );
        final Supplier<TuplesImpl> generatorTuplesImplSupplier2 = new NonCachedTuplesImplSupplier( generatorOperatorDef2.outputPortCount
                                                                                                                                 () );

        final OperatorReplica generatorOperator2 = new OperatorReplica( pipelineReplicaId2,
                                                                        generatorOperatorDef2,
                                                                        generatorTupleQueueContext2,
                                                                        nopKvStoreContext,
                                                                        generatorDrainerPool2,
                                                                        generatorTuplesImplSupplier2 );

        final PipelineReplica pipeline2 = new PipelineReplica( zanzaConfig,
                                                               pipelineReplicaId2,
                                                               new OperatorReplica[] { generatorOperator2 },
                                                               new EmptyTupleQueueContext( "generator2",
                                                                                           generatorOperatorDef2.inputPortCount() ) );
        final SupervisorNotifier supervisorNotifier2 = new SupervisorNotifier( supervisor, pipeline2 );

        pipeline2.init( supervisor.upstreamContexts.get( pipelineReplicaId2 ), supervisorNotifier2 );

        final OperatorConfig sinkOperatorConfig = new OperatorConfig();
        final OperatorDef sinkOperatorDef = OperatorDefBuilder.newInstance( "sink", ValueSinkOperator.class )
                                                              .setConfig( sinkOperatorConfig )
                                                              .build();

        final TupleQueueContext sinkTupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                 REPLICA_INDEX,
                                                                                                                 sinkOperatorDef,
                                                                                                                 MULTI_THREADED );

        final TupleQueueDrainerPool sinkDrainerPool = new BlockingTupleQueueDrainerPool( zanzaConfig, sinkOperatorDef );
        final Supplier<TuplesImpl> sinkTuplesImplSupplier = new CachedTuplesImplSupplier( sinkOperatorDef.outputPortCount() );

        final KVStoreContext sinkKVStoreContext = kvStoreContextManager.createDefaultKVStoreContext( REGION_ID, "sink" );

        final OperatorReplica sinkOperator = new OperatorReplica( pipelineReplicaId3,
                                                                  sinkOperatorDef,
                                                                  sinkTupleQueueContext,
                                                                  sinkKVStoreContext,
                                                                  sinkDrainerPool,
                                                                  sinkTuplesImplSupplier );

        final OperatorConfig passerOperatorConfig = new OperatorConfig();
        passerOperatorConfig.set( "batchCount", batchCount / 2 );

        final OperatorDef passerOperatorDef = OperatorDefBuilder.newInstance( "passer", ValuePasserOperator.class )
                                                                .setConfig( passerOperatorConfig )
                                                                .build();

        final TupleQueueContext passerTupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                   REPLICA_INDEX,
                                                                                                                   passerOperatorDef,
                                                                                                                   SINGLE_THREADED );

        final TupleQueueDrainerPool passerDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, passerOperatorDef );
        final Supplier<TuplesImpl> passerTuplesImplSupplier = new CachedTuplesImplSupplier( passerOperatorDef.outputPortCount() );

        final OperatorReplica passerOperator = new OperatorReplica( pipelineReplicaId3,
                                                                    passerOperatorDef,
                                                                    passerTupleQueueContext,
                                                                    nopKvStoreContext,
                                                                    passerDrainerPool,
                                                                    passerTuplesImplSupplier );

        final KVStoreContext[] kvStoreContexts = kvStoreContextManager.createPartitionedKVStoreContexts( REGION_ID, 1, "state" );

        final OperatorDef stateOperatorDef = OperatorDefBuilder.newInstance( "state", ValueStateOperator.class )
                                                               .setPartitionFieldNames( singletonList( "val" ) )
                                                               .build();

        final PartitionedTupleQueueContext[] stateTupleQueueContexts = tupleQueueContextManager.createPartitionedTupleQueueContext(
                REGION_ID,
                1,
                stateOperatorDef );
        final PartitionedTupleQueueContext stateTupleQueueContext = stateTupleQueueContexts[ 0 ];

        final TupleQueueDrainerPool stateDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, stateOperatorDef );
        final Supplier<TuplesImpl> stateTuplesImplSupplier = new CachedTuplesImplSupplier( stateOperatorDef.outputPortCount() );

        final OperatorReplica stateOperator = new OperatorReplica( pipelineReplicaId3,
                                                                   stateOperatorDef,
                                                                   stateTupleQueueContext,
                                                                   kvStoreContexts[ 0 ],
                                                                   stateDrainerPool,
                                                                   stateTuplesImplSupplier );

        final PipelineReplica pipeline3 = new PipelineReplica( zanzaConfig,
                                                               pipelineReplicaId3,
                                                               new OperatorReplica[] { sinkOperator, passerOperator, stateOperator },
                                                               new EmptyTupleQueueContext( "sink", sinkOperatorDef.inputPortCount() ) );
        final SupervisorNotifier supervisorNotifier3 = new SupervisorNotifier( supervisor, pipeline3 );

        pipeline3.init( supervisor.upstreamContexts.get( pipelineReplicaId3 ), supervisorNotifier3 );

        final PipelineReplicaRunner runner1 = new PipelineReplicaRunner( zanzaConfig,
                                                                         pipeline1,
                                                                         supervisor,
                                                                         supervisorNotifier1,
                                                                         new DownstreamTupleSenderImpl( sinkTupleQueueContext,
                                                                                                        new Pair[] { Pair.of( 0, 0 ) } ) );
        final PipelineReplicaRunner runner2 = new PipelineReplicaRunner( zanzaConfig,
                                                                         pipeline2,
                                                                         supervisor,
                                                                         supervisorNotifier2,
                                                                         new DownstreamTupleSenderImpl( sinkTupleQueueContext,
                                                                                                        new Pair[] { Pair.of( 0, 1 ) } ) );
        final PipelineReplicaRunner runner3 = new PipelineReplicaRunner( zanzaConfig,
                                                                         pipeline3,
                                                                         supervisor,
                                                                         supervisorNotifier3,
                                                                         new DownstreamTupleSenderImpl( null, new Pair[] {} ) );

        supervisor.targetPipelineReplicaId = pipelineReplicaId3;
        supervisor.runner = runner3;

        final Thread runnerThread1 = spawnThread( runner1 );
        final Thread runnerThread2 = spawnThread( runner2 );
        final Thread runnerThread3 = spawnThread( runner3 );

        final ValueGeneratorOperator generatorOp1 = (ValueGeneratorOperator) generatorOperator1.getOperator();
        generatorOp1.start = true;

        final ValueGeneratorOperator generatorOp2 = (ValueGeneratorOperator) generatorOperator2.getOperator();

        assertTrueEventually( () -> assertTrue( generatorOp1.count > 5000 ) );
        supervisor.upstreamContexts.put( pipelineReplicaId1, new UpstreamContext( 1, new UpstreamConnectionStatus[] {} ) );
        runner1.updatePipelineUpstreamContext();

        generatorOp2.start = true;
        assertTrueEventually( () -> assertTrue( generatorOp2.count < -5000 ) );
        supervisor.upstreamContexts.put( pipelineReplicaId2, new UpstreamContext( 1, new UpstreamConnectionStatus[] {} ) );
        runner2.updatePipelineUpstreamContext();

        assertTrueEventually( () -> supervisor.completedPipelines.contains( pipelineReplicaId1 ) );
        assertTrueEventually( () -> supervisor.completedPipelines.contains( pipelineReplicaId2 ) );
        assertTrueEventually( () -> supervisor.completedPipelines.contains( pipelineReplicaId3 ) );

        runnerThread1.join();
        runnerThread2.join();
        runnerThread3.join();

        final ValuePasserOperator passerOp = (ValuePasserOperator) passerOperator.getOperator();
        final ValueStateOperator stateOp = (ValueStateOperator) stateOperator.getOperator();

        final int totalCount = generatorOp1.count + Math.abs( generatorOp2.count );
        assertEquals( totalCount, passerOp.count );
        assertEquals( totalCount, stateOp.count );
        assertEquals( totalCount, getKVStoreTotalItemCount( REGION_ID, "state" ) );
    }

    @Test
    public void testMultiplePipelines_partitionedStatefulDownstreamPipeline () throws ExecutionException, InterruptedException
    {
        final SupervisorImpl supervisor = new SupervisorImpl();
        supervisor.upstreamContexts.put( pipelineReplicaId1, new UpstreamContext( 0, new UpstreamConnectionStatus[] {} ) );
        supervisor.upstreamContexts.put( pipelineReplicaId2, new UpstreamContext( 0, new UpstreamConnectionStatus[] { ACTIVE } ) );
        supervisor.inputPortIndices.put( pipelineReplicaId1, 0 );
        supervisor.inputPortIndices.put( pipelineReplicaId2, 0 );

        final int batchCount = 4;

        final OperatorConfig generatorOperatorConfig = new OperatorConfig();
        generatorOperatorConfig.set( "batchCount", batchCount );

        final OperatorDef generatorOperatorDef = OperatorDefBuilder.newInstance( "generator", ValueGeneratorOperator.class )
                                                                   .setConfig( generatorOperatorConfig )
                                                                   .build();

        final TupleQueueContext generatorTupleQueueContext = new EmptyTupleQueueContext( generatorOperatorDef.id(),
                                                                                         generatorOperatorDef.inputPortCount() );

        final TupleQueueDrainerPool generatorDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, generatorOperatorDef );
        final Supplier<TuplesImpl> generatorTuplesImplSupplier = new NonCachedTuplesImplSupplier( generatorOperatorDef.outputPortCount() );

        final OperatorReplica generatorOperator = new OperatorReplica( pipelineReplicaId1,
                                                                       generatorOperatorDef,
                                                                       generatorTupleQueueContext,
                                                                       nopKvStoreContext,
                                                                       generatorDrainerPool,
                                                                       generatorTuplesImplSupplier );

        final OperatorConfig passerOperatorConfig = new OperatorConfig();
        passerOperatorConfig.set( "batchCount", batchCount / 2 );

        final OperatorDef passerOperatorDef = OperatorDefBuilder.newInstance( "passer", ValuePasserOperator.class )
                                                                .setConfig( passerOperatorConfig )
                                                                .build();

        final TupleQueueContext passerTupleQueueContext = tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                   REPLICA_INDEX,
                                                                                                                   passerOperatorDef,
                                                                                                                   SINGLE_THREADED );

        final TupleQueueDrainerPool passerDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, passerOperatorDef );
        final Supplier<TuplesImpl> passerTuplesImplSupplier = new CachedTuplesImplSupplier( passerOperatorDef.outputPortCount() );

        final OperatorReplica passerOperator = new OperatorReplica( pipelineReplicaId1,
                                                                    passerOperatorDef,
                                                                    passerTupleQueueContext,
                                                                    nopKvStoreContext,
                                                                    passerDrainerPool,
                                                                    passerTuplesImplSupplier );

        final KVStoreContext[] kvStoreContexts = kvStoreContextManager.createPartitionedKVStoreContexts( REGION_ID, 1, "state" );

        final OperatorDef stateOperatorDef = OperatorDefBuilder.newInstance( "state", ValueStateOperator.class )
                                                               .setPartitionFieldNames( singletonList( "val" ) )
                                                               .build();

        final PartitionedTupleQueueContext[] stateTupleQueueContexts = tupleQueueContextManager.createPartitionedTupleQueueContext(
                REGION_ID,
                1,
                stateOperatorDef );
        final PartitionedTupleQueueContext stateTupleQueueContext = stateTupleQueueContexts[ 0 ];

        final TupleQueueDrainerPool stateDrainerPool = new NonBlockingTupleQueueDrainerPool( zanzaConfig, stateOperatorDef );
        final Supplier<TuplesImpl> stateTuplesImplSupplier = new CachedTuplesImplSupplier( stateOperatorDef.outputPortCount() );

        final OperatorReplica stateOperator = new OperatorReplica( pipelineReplicaId2,
                                                                   stateOperatorDef,
                                                                   stateTupleQueueContext,
                                                                   kvStoreContexts[ 0 ],
                                                                   stateDrainerPool,
                                                                   stateTuplesImplSupplier );

        final PipelineReplica pipeline1 = new PipelineReplica( zanzaConfig,
                                                               pipelineReplicaId1,
                                                               new OperatorReplica[] { generatorOperator, passerOperator },
                                                               new EmptyTupleQueueContext( "generator",
                                                                                           generatorOperatorDef.inputPortCount() ) );
        final SupervisorNotifier supervisorNotifier1 = new SupervisorNotifier( supervisor, pipeline1 );

        pipeline1.init( supervisor.upstreamContexts.get( pipelineReplicaId1 ), supervisorNotifier1 );

        final PipelineReplica pipeline2 = new PipelineReplica( zanzaConfig,
                                                               pipelineReplicaId2,
                                                               new OperatorReplica[] { stateOperator },
                                                               tupleQueueContextManager.createDefaultTupleQueueContext( REGION_ID,
                                                                                                                        REPLICA_INDEX,
                                                                                                                        stateOperatorDef,
                                                                                                                        MULTI_THREADED ) );

        final SupervisorNotifier supervisorNotifier2 = new SupervisorNotifier( supervisor, pipeline2 );

        pipeline2.init( supervisor.upstreamContexts.get( pipelineReplicaId2 ), supervisorNotifier2 );

        final PipelineReplicaRunner runner1 = new PipelineReplicaRunner( zanzaConfig,
                                                                         pipeline1,
                                                                         supervisor,
                                                                         supervisorNotifier1,
                                                                         new DownstreamTupleSenderImpl(
                                                                                 pipeline2.getUpstreamTupleQueueContext(),
                                                                                                        new Pair[] { Pair.of( 0, 0 ) } ) );
        final PipelineReplicaRunner runner2 = new PipelineReplicaRunner( zanzaConfig,
                                                                         pipeline2,
                                                                         supervisor,
                                                                         supervisorNotifier2,
                                                                         mock( DownstreamTupleSender.class ) );

        supervisor.targetPipelineReplicaId = pipelineReplicaId2;
        supervisor.runner = runner2;

        final Thread runnerThread1 = spawnThread( runner1 );
        final Thread runnerThread2 = spawnThread( runner2 );

        final ValueGeneratorOperator generatorOp = (ValueGeneratorOperator) generatorOperator.getOperator();
        generatorOp.start = true;

        assertTrueEventually( () -> assertTrue( generatorOp.count > 1000 ) );

        supervisor.upstreamContexts.put( pipelineReplicaId1, new UpstreamContext( 1, new UpstreamConnectionStatus[] {} ) );
        runner1.updatePipelineUpstreamContext();

        assertTrueEventually( () -> supervisor.completedPipelines.contains( pipelineReplicaId1 ) );
        assertTrueEventually( () -> supervisor.completedPipelines.contains( pipelineReplicaId2 ) );

        runnerThread1.join();
        runnerThread2.join();

        final ValuePasserOperator passerOp = (ValuePasserOperator) passerOperator.getOperator();
        final ValueStateOperator stateOp = (ValueStateOperator) stateOperator.getOperator();

        assertEquals( generatorOp.count, passerOp.count );
        assertEquals( generatorOp.count, stateOp.count );
        assertEquals( generatorOp.count, getKVStoreTotalItemCount( REGION_ID, "state" ) );
    }

    private int getKVStoreTotalItemCount ( final int regionId, final String operatorId )
    {
        int count = 0;
        for ( KVStore kvStore : kvStoreContextManager.getKVStores( regionId, operatorId ) )
        {
            count += kvStore.size();
        }

        return count;
    }

    private static class TupleCollectorDownstreamTupleSender implements DownstreamTupleSender
    {

        private final TupleQueue[] tupleQueues;

        TupleCollectorDownstreamTupleSender ( final int portCount )
        {
            tupleQueues = new TupleQueue[ portCount ];
            for ( int i = 0; i < portCount; i++ )
            {
                tupleQueues[ i ] = new MultiThreadedTupleQueue( 1000 );
            }
        }

        @Override
        public Future<Void> send ( final TuplesImpl tuples )
        {
            for ( int i = 0; i < tuples.getPortCount(); i++ )
            {
                tupleQueues[ i ].offerTuples( tuples.getTuples( i ) );
            }

            return null;
        }

    }


    @OperatorSpec( type = STATEFUL, inputPortCount = 2, outputPortCount = 1 )
    @OperatorSchema( inputs = { @PortSchema( portIndex = 0, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "val", type = Integer.class ) } ),

                                @PortSchema( portIndex = 1, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "val", type = Integer.class ) } ) }, outputs = { @PortSchema( portIndex = 0, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "val", type = Integer.class ) } ) } )
    public static class ValueSinkOperator implements Operator
    {

        @Override
        public SchedulingStrategy init ( final InitializationContext context )
        {
            return scheduleWhenTuplesAvailableOnAny( 2, 1, 0, 1 );
        }

        @Override
        public void invoke ( final InvocationContext invocationContext )
        {
            final Tuples input = invocationContext.getInput();

            final Tuples output = invocationContext.getOutput();
            output.addAll( input.getTuples( 0 ) );
            output.addAll( input.getTuples( 1 ) );
        }

    }


    @OperatorSpec( type = STATELESS, inputPortCount = 0, outputPortCount = 1 )
    @OperatorSchema( inputs = {}, outputs = { @PortSchema( portIndex = 0, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "val",
            type = Integer.class ) } ) } )
    public static class ValueGeneratorOperator implements Operator
    {

        private volatile boolean start;

        private int batchCount;

        private volatile int count;

        private boolean increment;

        @Override
        public SchedulingStrategy init ( final InitializationContext context )
        {
            final OperatorConfig config = context.getConfig();
            batchCount = config.getInteger( "batchCount" );
            count = config.getIntegerOrDefault( "initial", 0 );
            increment = config.getBooleanOrDefault( "increment", true );
            return ScheduleWhenAvailable.INSTANCE;
        }

        @Override
        public void invoke ( final InvocationContext invocationContext )
        {
            if ( start )
            {
                final Tuples output = invocationContext.getOutput();
                for ( int i = 0; i < batchCount; i++ )
                {
                    final int val = increment ? ++count : --count;
                    output.add( new Tuple( "val", val ) );
                }
            }
            else
            {
                sleepUninterruptibly( 1, TimeUnit.MICROSECONDS );
            }
        }

    }


    @OperatorSpec( type = STATELESS, inputPortCount = 1, outputPortCount = 1 )
    @OperatorSchema( inputs = { @PortSchema( portIndex = 0, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "val", type = Integer.class ) } ) }, outputs = { @PortSchema( portIndex = 0, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "val", type = Integer.class ) } ) } )
    public static class ValuePasserOperator implements Operator
    {

        private volatile int count;

        @Override
        public SchedulingStrategy init ( final InitializationContext context )
        {
            final int batchCount = context.getConfig().getInteger( "batchCount" );
            return scheduleWhenTuplesAvailableOnDefaultPort( EXACT, batchCount );
        }

        @Override
        public void invoke ( final InvocationContext invocationContext )
        {
            final Tuples input = invocationContext.getInput();
            final Tuples output = invocationContext.getOutput();
            output.addAll( input.getTuplesByDefaultPort() );
            count += input.getTupleCount( 0 );
        }

    }


    @OperatorSpec( type = PARTITIONED_STATEFUL, inputPortCount = 1, outputPortCount = 1 )
    @OperatorSchema( inputs = { @PortSchema( portIndex = 0, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "val", type = Integer.class ) } ) }, outputs = {} )
    public static class ValueStateOperator implements Operator
    {

        private volatile int count;

        @Override
        public SchedulingStrategy init ( final InitializationContext context )
        {
            return scheduleWhenTuplesAvailableOnDefaultPort( EXACT, 1 );
        }

        @Override
        public void invoke ( final InvocationContext invocationContext )
        {
            final Tuples input = invocationContext.getInput();
            final KVStore kvStore = invocationContext.getKVStore();
            for ( Tuple tuple : input.getTuplesByDefaultPort() )
            {
                kvStore.set( "tuple", tuple );
            }

            count += input.getTupleCount( 0 );
        }

    }


    public static class DownstreamTupleSenderImpl implements DownstreamTupleSender
    {

        private final TupleQueueContext tupleQueueContext;

        private final Pair<Integer, Integer>[] ports;

        public DownstreamTupleSenderImpl ( final TupleQueueContext tupleQueueContext, final Pair<Integer, Integer>[] ports )
        {
            this.tupleQueueContext = tupleQueueContext;
            this.ports = ports;
        }

        @Override
        public Future<Void> send ( final TuplesImpl tuples )
        {
            for ( Pair<Integer, Integer> p : ports )
            {
                final int outputPort = p._1;
                final int inputPort = p._2;
                if ( tuples.getTupleCount( outputPort ) > 0 )
                {
                    tupleQueueContext.offer( inputPort, tuples.getTuples( outputPort ) );
                }
            }

            return null;
        }

    }


    public static class SupervisorImpl implements Supervisor
    {

        private final Map<PipelineReplicaId, UpstreamContext> upstreamContexts = new ConcurrentHashMap<>();

        private final Map<PipelineReplicaId, Integer> inputPortIndices = new HashMap<>();

        private final Set<PipelineReplicaId> completedPipelines = Collections.newSetFromMap( new ConcurrentHashMap<>() );

        private PipelineReplicaId targetPipelineReplicaId;

        private PipelineReplicaRunner runner;

        @Override
        public UpstreamContext getUpstreamContext ( final PipelineReplicaId id )
        {
            return upstreamContexts.get( id );
        }

        @Override
        public synchronized void notifyPipelineReplicaCompleted ( final PipelineReplicaId id )
        {
            assertTrue( completedPipelines.add( id ) );
            if ( !id.equals( targetPipelineReplicaId ) )
            {
                final UpstreamContext currentUpstreamContext = upstreamContexts.get( targetPipelineReplicaId );
                final UpstreamContext newUpstreamContext = withUpstreamConnectionStatus( currentUpstreamContext,
                                                                                         inputPortIndices.get( id ),
                                                                                         CLOSED );
                upstreamContexts.put( targetPipelineReplicaId, newUpstreamContext );
                runner.updatePipelineUpstreamContext();
            }
        }

        @Override
        public void notifyPipelineReplicaFailed ( final PipelineReplicaId id, final Throwable failure )
        {
            System.err.println( "Pipeline Replica " + id + " failed with " + failure );
            failure.printStackTrace();
        }

    }

}
