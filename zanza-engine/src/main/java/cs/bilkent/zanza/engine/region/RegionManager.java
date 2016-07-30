package cs.bilkent.zanza.engine.region;

import cs.bilkent.zanza.flow.FlowDef;

public interface RegionManager
{

    Region createRegion ( final FlowDef flow, final RegionConfig regionConfig );

    void releaseRegion ( int regionId );

}