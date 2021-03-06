package cs.bilkent.joker.engine.partition.impl;

import java.util.List;

import cs.bilkent.joker.engine.partition.PartitionKeyExtractor;
import static cs.bilkent.joker.engine.partition.impl.PartitionKeyNFwd3.computePartitionHash;
import cs.bilkent.joker.operator.Tuple;
import cs.bilkent.joker.partition.impl.PartitionKey;

public class PartitionKeyExtractorNFwd3 implements PartitionKeyExtractor
{

    private final List<String> partitionFieldNames;

    private final String field0;

    private final String field1;

    private final String field2;

    PartitionKeyExtractorNFwd3 ( final List<String> partitionFieldNames )
    {
        this.partitionFieldNames = partitionFieldNames;
        this.field0 = partitionFieldNames.get( 0 );
        this.field1 = partitionFieldNames.get( 1 );
        this.field2 = partitionFieldNames.get( 2 );
    }

    @Override
    public PartitionKey getPartitionKey ( final Tuple tuple )
    {
        return new PartitionKeyNFwd3( tuple, partitionFieldNames );
    }

    @Override
    public int getPartitionHash ( final Tuple tuple )
    {
        return computePartitionHash( tuple.get( field0 ), tuple.get( field1 ), tuple.get( field2 ) );
    }

}
