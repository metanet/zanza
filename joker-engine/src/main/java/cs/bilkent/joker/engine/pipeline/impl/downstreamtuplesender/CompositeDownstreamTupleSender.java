package cs.bilkent.joker.engine.pipeline.impl.downstreamtuplesender;

import java.util.Arrays;

import cs.bilkent.joker.engine.pipeline.DownstreamTupleSender;
import cs.bilkent.joker.operator.impl.TuplesImpl;

public class CompositeDownstreamTupleSender implements DownstreamTupleSender
{

    private final DownstreamTupleSender[] senders;

    private final int size;

    public CompositeDownstreamTupleSender ( final DownstreamTupleSender[] senders )
    {
        this.senders = Arrays.copyOf( senders, senders.length );
        this.size = senders.length;
    }

    @Override
    public void send ( final TuplesImpl tuples )
    {
        for ( int i = 0; i < size; i++ )
        {
            senders[ i ].send( tuples );
        }
    }

    public DownstreamTupleSender[] getDownstreamTupleSenders ()
    {
        return Arrays.copyOf( senders, senders.length );
    }

}
