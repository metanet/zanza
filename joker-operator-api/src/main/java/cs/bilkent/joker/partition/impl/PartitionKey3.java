package cs.bilkent.joker.partition.impl;

import java.util.AbstractList;
import java.util.List;

import static cs.bilkent.joker.partition.impl.PartitionKeyUtil.hashHead;
import static cs.bilkent.joker.partition.impl.PartitionKeyUtil.hashTail;

public class PartitionKey3 extends AbstractList<Object> implements PartitionKey
{

    private final Object val0;

    private final Object val1;

    private final Object val2;

    private final int hashCode;

    public PartitionKey3 ( final Object val0, final Object val1, final Object val2 )
    {
        this.val0 = val0;
        this.val1 = val1;
        this.val2 = val2;
        this.hashCode = computeHashCode( val0, val1, val2 );
    }

    @Override
    public int partitionHashCode ()
    {
        return hashCode;
    }

    @Override
    public Object get ( final int index )
    {
        if ( index == 0 )
        {
            return val0;
        }

        if ( index == 1 )
        {
            return val1;
        }

        if ( index == 2 )
        {
            return val2;
        }

        throw new IndexOutOfBoundsException( "Index: " + index + ", Size: " + 3 );
    }

    @Override
    public int size ()
    {
        return 3;
    }

    @Override
    public boolean equals ( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null )
        {
            return false;
        }
        if ( getClass() != o.getClass() )
        {
            return o instanceof List && super.equals( o );
        }

        final PartitionKey3 n2 = (PartitionKey3) o;
        return val0.equals( n2.val0 ) && val1.equals( n2.val1 ) && val2.equals( n2.val2 );
    }

    @Override
    public int hashCode ()
    {
        return hashCode;
    }

    @Override
    public String toString ()
    {
        return "PartitionKey3{" + "val0=" + val0 + ", val1=" + val1 + ", val2=" + val2 + '}';
    }

    public static int computeHashCode ( final Object val0, final Object val1, final Object val2 )
    {
        return hashTail( hashTail( hashHead( val0 ), val1 ), val2 );
    }

}
