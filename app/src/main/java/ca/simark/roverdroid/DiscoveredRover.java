package ca.simark.roverdroid;

import android.net.nsd.NsdServiceInfo;

public class DiscoveredRover {
    private NsdServiceInfo fNsdServiceInfo;

    DiscoveredRover(NsdServiceInfo nsdServiceInfo) {

        fNsdServiceInfo = nsdServiceInfo;
    }

    public NsdServiceInfo getNsdServiceInfo() {
        return fNsdServiceInfo;
    }

    @Override
    public String toString() {
        return String.format("Rover \"%s\"\n%s:%d",
                fNsdServiceInfo.getServiceName(),
                fNsdServiceInfo.getHost().getHostAddress(),
                fNsdServiceInfo.getPort());
    }
}
