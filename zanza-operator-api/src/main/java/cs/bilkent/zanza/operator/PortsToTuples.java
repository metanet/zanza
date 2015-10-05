package cs.bilkent.zanza.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

/**
 * Used for incoming and outgoing tuples
 */
public class PortsToTuples
{
    private final Int2ObjectHashMap<List<Tuple>> tuplesByPort = new Int2ObjectHashMap<>();

    public PortsToTuples()
    {
    }

    public PortsToTuples(final Tuple tuple)
    {
        checkNotNull( tuple, "tuple can't be null" );
        add(tuple);
    }

    public PortsToTuples(final List<Tuple> tuples)
    {
        checkNotNull( tuples, "tuples can't be null" );
        addAll(tuples);
    }

    public void add(final Tuple tuple)
    {
        checkNotNull( tuple, "tuple can't be null" );
        add(Port.DEFAULT_PORT_INDEX, tuple);
    }

    public void addAll(final List<Tuple> tuples)
    {
        checkNotNull( tuples, "tuples can't be null" );
        addAll(Port.DEFAULT_PORT_INDEX, tuples);
    }

    public void add(final int portIndex, final Tuple tuple)
    {
        checkArgument( portIndex >= 0, "port must be non-negative" );
        checkNotNull( tuple, "tuple can't be null" );
        final List<Tuple> tuples = getOrCreateTuples(portIndex);
        tuples.add(tuple);
    }

    public void addAll(final int portIndex, final List<Tuple> tuplesToAdd)
    {
        checkArgument( portIndex >= 0, "port must be non-negative" );
        checkNotNull( tuplesToAdd, "tuples can't be null" );
        final List<Tuple> tuples = getOrCreateTuples(portIndex);
        tuples.addAll(tuplesToAdd);
    }

    private List<Tuple> getOrCreateTuples(final int portIndex)
    {
        return tuplesByPort.computeIfAbsent(portIndex, ignoredPortIndex -> new ArrayList<>());
    }

    public List<Tuple> getTuples(final int portIndex)
    {
        final List<Tuple> tuples = tuplesByPort.get(portIndex);
        return tuples != null ? tuples : Collections.emptyList();
    }

    public List<Tuple> getTuplesByDefaultPort()
    {
        return getTuples(Port.DEFAULT_PORT_INDEX);
    }

    public int[] getPorts()
    {
        final Int2ObjectHashMap<List<Tuple>>.KeySet keys = tuplesByPort.keySet();
        final int[] ports = new int[keys.size()];

        final Int2ObjectHashMap<List<Tuple>>.KeyIterator it = keys.iterator();

        for (int i = 0; i < keys.size(); i++)
        {
            ports[i] = it.nextInt();
        }

        Arrays.sort(ports);

        return ports;
    }

    public int getPortCount()
    {
        return tuplesByPort.keySet().size();
    }
}
