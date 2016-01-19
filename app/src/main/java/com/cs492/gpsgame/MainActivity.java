package com.cs492.gpsgame;

import android.Manifest;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        OnMapReadyCallback {
    public static final String TAG = "MainActivity";

    public class Position {
        double latitude;
        double longitude;
        String name;

        public Position(double _latitude, double _longitude, String _name) {
            latitude = _latitude;
            longitude = _longitude;
            name = _name;
        }
    }

    private ArrayList<Position> positionList;

    private Button btnStart;
    private Button btnStop;
    private TextView txtNearestSpot;

    private GoogleApiClient googleApiClient;

    private LocationRequest locationRequest = null;
    private boolean requestingLocationUpdates = false;

    private final static String KEY_REQUESTING_LOCATION_UPDATES = "KEY_REQUESTING_LOCATION_UPDATES";

    private MapFragment mapFragment;

    private GoogleMap googleMap;

    // a collection of pairs (the name of a marker for a spot, the marker object)
    private HashMap<String, Marker> markerHashMap;

    // marker of the user
    private Marker userMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.nearest_place_layout);

        // initialize position list
        positionList = new ArrayList<>();
        fetchData();
        //positionList.add(new Position(36.374128, 127.365497, "CSBuilding"));

        // initialize variables for layout
        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);
        txtNearestSpot = (TextView) findViewById(R.id.txtNearestSpot);


        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestingLocationUpdates = true;
                startLocationUpdates();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestingLocationUpdates = false;
                stopLocationUpdates();
                txtNearestSpot.setText("");
            }
        });

        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        updateValuesFromBundle(savedInstanceState);
        createLocationRequest();

        mapFragment =
                (MapFragment) getFragmentManager().findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);

        markerHashMap = new HashMap<>();
    }

    @Override
    protected void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (googleApiClient.isConnected() && requestingLocationUpdates)
        {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause()
    {
        stopLocationUpdates();
        super.onPause();
    }

    @Override
    protected void onStop() {
        googleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected");
        if (requestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location)
    {
        Log.d(TAG, "onLocationChanged: (" + location.getLatitude() + "," + location.getLongitude() + ")");
        updateUI(location);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Error code " + connectionResult.getErrorCode() + ": " + connectionResult.getErrorMessage());
    }

    protected void createLocationRequest() {
        if (locationRequest == null) {
            locationRequest = new LocationRequest();
            locationRequest.setInterval(1000);
            locationRequest.setFastestInterval(500);
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }
    }

    protected void startLocationUpdates()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Log.d(TAG, "permission check failed");
            return;
        }

        Log.d(TAG, "start location updates");
        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient, locationRequest, this
        );
        requestingLocationUpdates = true;
    }

    protected void stopLocationUpdates()
    {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                googleApiClient, this
        );
    }

    private void updateUI(Location location)
    {
        Log.d(TAG, "updating UI...");
        double minDistance = 99999999999.9;
        String minSpotName = "";
        for (Position pos: positionList)
        {
            double pos_dist = Math.sqrt(
                    Math.pow(location.getLongitude() - pos.longitude, 2)
                            + Math.pow(location.getLatitude() - pos.latitude, 2)
            );
            if (pos_dist < minDistance)
            {
                minDistance = pos_dist;
                minSpotName = pos.name;
            }
        }

        txtNearestSpot.setText(minSpotName);

        // update the position of google map to the user's current position
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));

        // hide all markers except the marker closest to the user
        for (Marker m: markerHashMap.values())
        {
            m.setVisible(false);
        }
        markerHashMap.get(minSpotName).setVisible(true);

        // update the user's marker
        userMarker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;

        this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(36.369561, 127.362435), 15));

        // create markers for spots
        for (Position pos: positionList) {
            Marker marker = googleMap.addMarker(new MarkerOptions().position(new LatLng(pos.latitude, pos.longitude)).title(pos.name));
            marker.setVisible(false);
            markerHashMap.put(pos.name, marker);
        }

        // create a marker for the user
        userMarker = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(36.369561, 127.362435))
                .title("You")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    private void updateValuesFromBundle(Bundle savedInstanceState)
    {
        if (savedInstanceState != null)
        {
            if (savedInstanceState.containsKey(KEY_REQUESTING_LOCATION_UPDATES))
            {
                requestingLocationUpdates = savedInstanceState.getBoolean(KEY_REQUESTING_LOCATION_UPDATES);
            }
        }
    }

    private void fetchData()
    {
        //check network connection
        ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
        {
            new DownloadWebpageTask().execute("http://143.248.233.58:8125/request");
        }
        else
        {
            Log.d(TAG, "no network connection available.");
        }
    }

    private class DownloadWebpageTask extends AsyncTask<String, Void, String>
    {
        @Override
        protected String doInBackground(String... urls) {
            try {
                return downloadUrl(urls[0]);
            } catch (IOException e) {
                return "Unable to retrieve web page. URL my be invalid.";
            }
        }
        @Override
        protected void onPostExecute(String result)
        {
            String output = "nothing";
            try {
                JSONObject locationListObject = new JSONObject(result);
                JSONArray locationArray = locationListObject.getJSONArray("LocationList");


                for (int i = 0; i< locationArray.length(); i++) {
                    JSONObject locationObject = locationArray.getJSONObject(i);

                    JSONObject positionObject = locationObject.getJSONObject("position");
                    double x = positionObject.getDouble("x");
                    double y = positionObject.getDouble("y");

                    String name = locationObject.getString("name");

                    positionList.add(new Position(x, y, name));
                    Log.d(TAG, "list length = " + positionList.size());
                }
            }
            catch (org.json.JSONException e)
            {
                Log.d(TAG, "JSONEXCEPTION BRO.......try harder man");
            }
        }

        private String downloadUrl(String myurl) throws IOException
        {
            InputStream is = null;
            int len = 2000;
            try
            {
                URL url = new URL(myurl);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();
                int response = conn.getResponseCode();
                Log.d("debugmessage", "The response is: " + response);
                is = conn.getInputStream();
                String contentAsString = readIt(is, len);
                return contentAsString;
            }
            finally
            {
                if (is != null)
                {
                    is.close();
                }
            }
        }

        public String readIt(InputStream stream, int len)throws IOException, UnsupportedEncodingException
        {
            Reader reader = null;
            reader = new InputStreamReader(stream, "UTF-8");
            char[] buffer = new char[len];
            reader.read(buffer);
            return new String(buffer);
        }
    }

}
