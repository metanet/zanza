package cs.bilkent.zanza.engine.partition.impl;

public class PartitionKey6
{

    private final Object val1;

    private final Object val2;

    private final Object val3;

    private final Object val4;

    private final Object val5;

    private final Object val6;

    private final int hashCode;

    public PartitionKey6 ( final Object val1,
                           final Object val2,
                           final Object val3,
                           final Object val4,
                           final Object val5,
                           final Object val6 )
    {
        this.val1 = val1;
        this.val2 = val2;
        this.val3 = val3;
        this.val4 = val4;
        this.val5 = val5;
        this.val6 = val6;
        this.hashCode = computeHashCode( val1, val2, val3, val4, val5, val6 );
    }

    @Override
    public boolean equals ( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        final PartitionKey6 that = (PartitionKey6) o;

        if ( !val1.equals( that.val1 ) )
        {
            return false;
        }
        if ( !val2.equals( that.val2 ) )
        {
            return false;
        }
        if ( !val3.equals( that.val3 ) )
        {
            return false;
        }
        if ( !val4.equals( that.val4 ) )
        {
            return false;
        }
        if ( !val5.equals( that.val5 ) )
        {
            return false;
        }

        return val6.equals( that.val6 );
    }

    @Override
    public int hashCode ()
    {
        return hashCode;
    }

    @Override
    public String toString ()
    {
        return "PartitionKey6{" + "val1=" + val1 + ", val2=" + val2 + ", val3=" + val3 + ", val4=" + val4 + ", val5=" + val5 + ", val6="
               + val6 + '}';
    }

    public static int computeHashCode ( final Object val1,
                                        final Object val2,
                                        final Object val3,
                                        final Object val4,
                                        final Object val5,
                                        final Object val6 )
    {
        int result = val1.hashCode();
        result = 31 * result + val2.hashCode();
        result = 31 * result + val3.hashCode();
        result = 31 * result + val4.hashCode();
        result = 31 * result + val5.hashCode();
        return 31 * result + val6.hashCode();
    }

}
