package cs.bilkent.zanza.operator;


import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.unmodifiableMap;


/**
 * The tuple is the main data structure to manipulate data in Zanza.
 * A tuple is a mapping of keys to values where each value can be any type.
 * <p>
 * TODO Serializable, Iterable ???
 */
public final class Tuple implements Fields<String>
{

    public static final int NO_SEQUENCE_NUMBER = 0;


    private int sequenceNumber = NO_SEQUENCE_NUMBER;

    private final Map<String, Object> values;

    public Tuple ()
    {
        this.values = new HashMap<>();
    }

    public Tuple ( final String key, final Object value )
    {
        checkArgument( value != null, "value can't be null" );
        this.values = new HashMap<>();
        this.values.put( key, value );
    }

    public Tuple ( final int sequenceNumber, final String key, final Object value )
    {
        checkArgument( value != null, "value can't be null" );
        setSequenceNumber( sequenceNumber );
        this.values = new HashMap<>();
        this.values.put( key, value );
    }

    public Tuple ( final Map<String, Object> values )
    {
        checkArgument( values != null, "values can't be null" );
        this.values = new HashMap<>();
        this.values.putAll( values );
    }

    public Tuple ( final int sequenceNumber, final Map<String, Object> values )
    {
        checkArgument( values != null, "values can't be null" );
        setSequenceNumber( sequenceNumber );
        this.values = new HashMap<>();
        this.values.putAll( values );
    }

    public Map<String, Object> asMap ()
    {
        return unmodifiableMap( values );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public <T> T get ( final String key )
    {
        return (T) values.get( key );
    }

    @Override
    public boolean contains ( final String key )
    {
        return values.containsKey( key );
    }

    @Override
    public void set ( final String key, final Object value )
    {
        checkArgument( value != null, "value can't be null" );
        this.values.put( key, value );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public <T> T put ( final String key, final T value )
    {
        checkArgument( value != null, "value can't be null" );
        return (T) this.values.put( key, value );
    }

    @Override
    public Object remove ( final String key )
    {
        return this.values.remove( key );
    }

    @Override
    public boolean delete ( final String key )
    {
        return this.values.remove( key ) != null;
    }

    @Override
    public void clear ()
    {
        this.values.clear();
    }

    @Override
    public int size ()
    {
        return this.values.size();
    }

    @Override
    public Collection<String> keys ()
    {
        return Collections.unmodifiableCollection( values.keySet() );
    }

    /**
     * Returns true if a sequence number is assigned to the tuple
     *
     * @return true if a sequence number is assigned to the tuple
     */
    public boolean hasSequenceNumber ()
    {
        return sequenceNumber != NO_SEQUENCE_NUMBER;
    }

    /**
     * Returns sequence number assigned to the tuple.
     *
     * @return sequence number assigned to the tuple.
     *
     * @throws IllegalStateException
     *         if sequence number is not asigned.
     */
    public int getSequenceNumber ()
    {
        checkState( sequenceNumber != NO_SEQUENCE_NUMBER );
        return sequenceNumber;
    }

    /**
     * Assings sequence number to tuple
     *
     * @param sequenceNumber
     *         sequence number to assign
     */
    public void setSequenceNumber ( final int sequenceNumber )
    {
        checkArgument( sequenceNumber != NO_SEQUENCE_NUMBER );
        this.sequenceNumber = sequenceNumber;
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

        final Tuple tuple = (Tuple) o;

        if ( sequenceNumber != tuple.sequenceNumber )
        {
            return false;
        }
        return values.equals( tuple.values );

    }

    @Override
    public int hashCode ()
    {
        int result = sequenceNumber;
        result = 31 * result + values.hashCode();
        return result;
    }

    @Override
    public String toString ()
    {
        return "Tuple{" + ( sequenceNumber != NO_SEQUENCE_NUMBER ? "SN=" + sequenceNumber + ", " : "" ) + values + '}';
    }
}
