package cs.bilkent.zanza.engine.partition;

public interface PartitionService
{

    int getPartitionCount ();

    int[] getOrCreatePartitionDistribution ( int regionId, int replicaCount );

}