package ca.simark.roverdroid;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Vector;

public class BrowserActivity extends AppCompatActivity implements NsdManager.DiscoveryListener, AdapterView.OnItemClickListener {
    public static final String TAG = BrowserActivity.class.getSimpleName();

    private NsdManager fNsdService;

    private Vector<DiscoveredRover> fDiscoveredRovers = new Vector<>();
    private ArrayAdapter<DiscoveredRover> fDiscoveredRoverArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        ListView discoveredRoverList = (ListView) findViewById(R.id.discovered_rover_list);
        fDiscoveredRoverArrayAdapter = new ArrayAdapter<>(this, R.layout.discovered_rover_row, fDiscoveredRovers);
        discoveredRoverList.setAdapter(fDiscoveredRoverArrayAdapter);

        discoveredRoverList.setOnItemClickListener(this);

        fNsdService = (NsdManager) getSystemService(Context.NSD_SERVICE);
        Log.d(TAG, "Service: " + fNsdService);

        if (fNsdService == null) {
            Toast.makeText(this, "Error getting auto-discovery service.", Toast.LENGTH_LONG).show();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (fNsdService != null) {
            fNsdService.discoverServices("_rover._tcp", NsdManager.PROTOCOL_DNS_SD, this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (fNsdService != null) {
            fNsdService.stopServiceDiscovery(this);
        }
    }

    @Override
    public void onStartDiscoveryFailed(String s, int i) {
        Log.d(TAG, "onStartDiscoveryFailed " + i);
    }

    @Override
    public void onStopDiscoveryFailed(String s, int i) {
        Log.d(TAG, "onStopDiscoveryFailed " + i);
    }

    @Override
    public void onDiscoveryStarted(String s) {
        Log.d(TAG, "onDiscoveryStarted");
    }

    @Override
    public void onDiscoveryStopped(String s) {
        Log.d(TAG, "onDiscoveryStopped");
    }

    @Override
    public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
        Log.d(TAG, "onServiceFound " + nsdServiceInfo);

        fNsdService.resolveService(nsdServiceInfo, new ResolveServiceListener());
    }

    @Override
    public void onServiceLost(final NsdServiceInfo nsdServiceInfo) {
        Log.d(TAG, "onServiceLost " + nsdServiceInfo);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (fDiscoveredRoverArrayAdapter) {

                    for (int i = 0; i < fDiscoveredRoverArrayAdapter.getCount(); i++) {
                        DiscoveredRover rover = fDiscoveredRoverArrayAdapter.getItem(i);

                        if (nsdServiceInfoEquals(nsdServiceInfo, rover.getNsdServiceInfo())) {
                            Log.d(TAG, "TRUE");
                            fDiscoveredRoverArrayAdapter.remove(rover);
                            break;
                        }
                    }
                }
            }
        });
    }

    private boolean nsdServiceInfoEquals(NsdServiceInfo a, NsdServiceInfo b) {
        if (!a.getServiceName().equals(b.getServiceName())) {
            return false;
        }

        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        DiscoveredRover rover = fDiscoveredRovers.get(i);
        Toast.makeText(this, "You clicked on " + rover.toString(), Toast.LENGTH_LONG).show();
    }

    class ResolveServiceListener implements NsdManager.ResolveListener {

        @Override
        public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {
            Log.d(TAG, "onResolveFailed " + i);
        }

        @Override
        public void onServiceResolved(final NsdServiceInfo nsdServiceInfo) {
            Log.d(TAG, "onServiceResolved " + nsdServiceInfo);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    synchronized (fDiscoveredRoverArrayAdapter) {
                        fDiscoveredRoverArrayAdapter.add(new DiscoveredRover(nsdServiceInfo));
                    }

                }
            });
        }
    }
}
