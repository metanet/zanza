package cs.bilkent.joker.operators;

import java.util.function.Consumer;

import cs.bilkent.joker.operator.InitCtx;
import cs.bilkent.joker.operator.InvocationCtx;
import cs.bilkent.joker.operator.Operator;
import cs.bilkent.joker.operator.OperatorConfig;
import cs.bilkent.joker.operator.Tuple;
import cs.bilkent.joker.operator.scheduling.ScheduleWhenAvailable;
import cs.bilkent.joker.operator.scheduling.SchedulingStrategy;
import cs.bilkent.joker.operator.schema.runtime.TupleSchema;
import cs.bilkent.joker.operator.spec.OperatorSpec;
import static cs.bilkent.joker.operator.spec.OperatorType.STATEFUL;


/**
 * Produces output tuples on each invocation using the provided tuple generator function
 */
@OperatorSpec( type = STATEFUL, inputPortCount = 0, outputPortCount = 1 )
public class BeaconOperator implements Operator
{

    public static final String TUPLE_POPULATOR_CONFIG_PARAMETER = "tuplePopulator";

    public static final String TUPLE_COUNT_CONFIG_PARAMETER = "tupleCount";


    private Consumer<Tuple> tuplePopulatorFunc;

    private int tupleCount;

    private TupleSchema outputSchema;

    @Override
    public SchedulingStrategy init ( final InitCtx ctx )
    {
        final OperatorConfig config = ctx.getConfig();

        this.tuplePopulatorFunc = config.getOrFail( TUPLE_POPULATOR_CONFIG_PARAMETER );
        this.tupleCount = config.getOrFail( TUPLE_COUNT_CONFIG_PARAMETER );
        this.outputSchema = ctx.getOutputPortSchema( 0 );

        return ScheduleWhenAvailable.INSTANCE;
    }

    @Override
    public void invoke ( final InvocationCtx ctx )
    {
        for ( int i = 0; i < tupleCount; i++ )
        {
            final Tuple tuple = new Tuple( outputSchema );
            tuplePopulatorFunc.accept( tuple );
            ctx.output( tuple );
        }
    }

}
