package cs.bilkent.joker.engine.region.impl;

import java.util.List;

import org.junit.Test;

import cs.bilkent.joker.engine.region.RegionDef;
import static cs.bilkent.joker.engine.region.impl.RegionFormerImplRegionDefTest.assertRegion;
import cs.bilkent.joker.flow.FlowDef;
import cs.bilkent.joker.flow.FlowDefBuilder;
import cs.bilkent.joker.operator.InitializationContext;
import cs.bilkent.joker.operator.InvocationContext;
import cs.bilkent.joker.operator.Operator;
import cs.bilkent.joker.operator.OperatorDef;
import cs.bilkent.joker.operator.OperatorDefBuilder;
import cs.bilkent.joker.operator.scheduling.SchedulingStrategy;
import cs.bilkent.joker.operator.schema.annotation.OperatorSchema;
import cs.bilkent.joker.operator.schema.annotation.PortSchema;
import static cs.bilkent.joker.operator.schema.annotation.PortSchemaScope.EXACT_FIELD_SET;
import cs.bilkent.joker.operator.schema.annotation.SchemaField;
import cs.bilkent.joker.operator.schema.runtime.OperatorRuntimeSchemaBuilder;
import cs.bilkent.joker.operator.spec.OperatorSpec;
import cs.bilkent.joker.operator.spec.OperatorType;
import static cs.bilkent.joker.operator.spec.OperatorType.PARTITIONED_STATEFUL;
import static cs.bilkent.joker.operator.spec.OperatorType.STATELESS;
import cs.bilkent.joker.operators.MapperOperator;
import cs.bilkent.joker.testutils.AbstractJokerTest;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.fail;

public class RegionDefFormerImplAllRegionsTest extends AbstractJokerTest
{

    private final RegionDefFormerImpl regionFormer = new RegionDefFormerImpl( new IdGenerator() );

    private final FlowDefBuilder flowBuilder = new FlowDefBuilder();


    @Test
    public void testFlowWithSingleOperatorSequence ()
    {
        /**
         * O1 --> O2
         */

        final OperatorDef operator1 = OperatorDefBuilder.newInstance( "o1", MapperOperator.class ).build();
        final OperatorDef operator2 = OperatorDefBuilder.newInstance( "o2", MapperOperator.class ).build();
        flowBuilder.add( operator1 );
        flowBuilder.add( operator2 );
        flowBuilder.connect( "o1", "o2" );
        final FlowDef flow = flowBuilder.build();

        final List<RegionDef> regions = regionFormer.createRegions( flow );
        assertThat( regions, hasSize( 1 ) );
        assertRegion( regions.get( 0 ), STATELESS, emptyList(), asList( operator1, operator2 ) );
    }

    @Test
    public void testFlowWithTwoOperatorsWithMultipleConnections ()
    {
        /**
         *     /-\
         *    /   \
         * O1 -----> O2
         */

        final OperatorDef operator1 = OperatorDefBuilder.newInstance( "o1", DoubleOutputPortOperator.class )
                                                        .setPartitionFieldNames( singletonList( "f" ) )
                                                        .build();
        final OperatorRuntimeSchemaBuilder mapperSchema = new OperatorRuntimeSchemaBuilder( 1, 1 );
        mapperSchema.addInputField( 0, "f", Integer.class );
        final OperatorDef operator2 = OperatorDefBuilder.newInstance( "o2", MapperOperator.class )
                                                        .setExtendingSchema( mapperSchema )
                                                        .build();
        flowBuilder.add( operator1 );
        flowBuilder.add( operator2 );
        flowBuilder.connect( "o1", "o2" );
        final FlowDef flow = flowBuilder.build();

        final List<RegionDef> regions = regionFormer.createRegions( flow );
        assertThat( regions, hasSize( 1 ) );
        assertRegion( regions.get( 0 ), PARTITIONED_STATEFUL, singletonList( "f" ), asList( operator1, operator2 ) );
    }

    @Test
    public void testFlowWithMultipleOperatorSequences ()
    {
        /**
         *
         *          /--> O4
         *         /
         * O1 --> O2 --> O3
         *
         */

        final OperatorDef operator1 = OperatorDefBuilder.newInstance( "o1", MapperOperator.class ).build();
        final OperatorDef operator2 = OperatorDefBuilder.newInstance( "o2", MapperOperator.class ).build();
        final OperatorDef operator3 = OperatorDefBuilder.newInstance( "o3", MapperOperator.class ).build();
        final OperatorDef operator4 = OperatorDefBuilder.newInstance( "o4", MapperOperator.class ).build();
        flowBuilder.add( operator1 );
        flowBuilder.add( operator2 );
        flowBuilder.add( operator3 );
        flowBuilder.add( operator4 );
        flowBuilder.connect( "o1", "o2" );
        flowBuilder.connect( "o2", "o3" );
        flowBuilder.connect( "o2", "o4" );

        final FlowDef flow = flowBuilder.build();
        final List<RegionDef> regions = regionFormer.createRegions( flow );
        assertThat( regions, hasSize( 3 ) );
        assertRegionExists( regions, STATELESS, emptyList(), asList( operator1, operator2 ) );
        assertRegionExists( regions, STATELESS, emptyList(), singletonList( operator3 ) );
        assertRegionExists( regions, STATELESS, emptyList(), singletonList( operator4 ) );
    }

    private void assertRegionExists ( final List<RegionDef> regionDefs,
                                      final OperatorType regionType,
                                      final List<String> partitionFieldNames,
                                      final List<OperatorDef> operators )
    {
        for ( RegionDef regionDef : regionDefs )
        {
            try
            {
                assertThat( regionDef.getRegionType(), equalTo( regionType ) );
                assertThat( regionDef.getPartitionFieldNames(), equalTo( partitionFieldNames ) );
                assertThat( regionDef.getOperators(), equalTo( operators ) );
                return;
            }
            catch ( AssertionError expected )
            {

            }
        }

        fail();
    }

    @OperatorSpec( type = PARTITIONED_STATEFUL, inputPortCount = 1, outputPortCount = 2 )
    @OperatorSchema( inputs = { @PortSchema( portIndex = 0, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "f", type = Integer.class ) } ) }, outputs = { @PortSchema( portIndex = 0, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "f", type = Integer.class ) } ),
                                                                                                                                                                    @PortSchema( portIndex = 1, scope = EXACT_FIELD_SET, fields = { @SchemaField( name = "f", type = Integer.class ) } ) } )
    private static class DoubleOutputPortOperator implements Operator
    {

        @Override
        public SchedulingStrategy init ( final InitializationContext context )
        {
            return null;
        }

        @Override
        public void invoke ( final InvocationContext invocationContext )
        {

        }

    }

}