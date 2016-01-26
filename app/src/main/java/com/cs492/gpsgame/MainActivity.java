package com.cs492.gpsgame;

import android.Manifest;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.Image;
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
import android.widget.ImageButton;
import android.widget.ImageView;
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

import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

    // true if the map is ready
    private boolean mapIsReady = false;

    // true if the spots are successfully fetched from the server
    private boolean fetchedData = false;

    // user's team
    private String team = "";

    //user's location
    private Position playerPos = new Position(0,0,"player");
    private Position minSpot;

    // layout objects
    private Button btnBomb = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);

        setContentView(R.layout.nearest_place_layout);

        team = getIntent().getStringExtra("team");

        // initialize position list
        positionList = new ArrayList<>();
        requestBombLocation();

        //positionList.add(new Position(36.374128, 127.365497, "CSBuilding"));
        // prepare layout objects
        btnBomb = (Button) findViewById(R.id.btnBomb);
        if (team.equals("terrorist"))
        {
            btnBomb.setText("Plant a bomb");
            btnBomb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestPlantBomb();
                }
            });
        }
        else if (team.equals("counter"))
        {
            btnBomb.setText("Defuse the bomb");
            btnBomb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestDefuseBomb();
                }
            });
        }

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
        requestingLocationUpdates = false;
        stopLocationUpdates();

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
        if (!requestingLocationUpdates) {
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
        playerPos.latitude = location.getLatitude();
        playerPos.longitude = location.getLongitude();
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
                minSpot = pos;
            }
        }

        // update the position of google map to the user's current position
//        googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));

        // hide all markers except the marker closest to the user
        for (Marker m : markerHashMap.values()) {
            m.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
        }
        if (markerHashMap.get(minSpotName) != null) {
            markerHashMap.get(minSpotName).setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        }

        Log.d(TAG, "minSpotName = " + minSpotName);

        // update the user's marker
        userMarker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;

        this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(36.369561, 127.362435), 15));

        mapIsReady = true;
        if (fetchedData)
        {
            createMarker();
        }

        // create a marker for the user
        userMarker = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(36.369561, 127.362435))
                .title("You")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    private void createMarker()
    {
        if (mapIsReady && fetchedData)
        {
            // create markers for spots
            for (Position pos: positionList) {
                Marker marker = googleMap.addMarker(new MarkerOptions().position(new LatLng(pos.latitude, pos.longitude)).title(pos.name));
                marker.setVisible(true);
                markerHashMap.put(pos.name, marker);
            }
        }
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
            new DownloadWebpageTask().execute("request");
        }
        else
        {
            Log.d(TAG, "no network connection available.");
        }
    }

    private void requestBombLocation()
    {
        ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
        {
            Log.d(TAG, "request!");
            new DownloadWebpageTask().execute("request");
        }
        else
        {
            Log.d(TAG, "no network connection available.");
        }
    }

    private void requestPlantBomb()
    {
        ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
        {
            new DownloadWebpageTask().execute("plant");
        }
        else
        {
            Log.d(TAG, "no network connection available.");
        }
    }

    private void requestDefuseBomb()
    {
        ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
        {
            new DownloadWebpageTask().execute("defuse");
        }
        else
        {
            Log.d(TAG, "no network connection available.");
        }

    }

    private class DownloadWebpageTask extends AsyncTask<String, Void, String>
    {
        private String taskname = "";
        @Override
        protected String doInBackground(String... urls) {
            String urlStart = "http://143.248.233.58:8125/";

            try {
                if (urls[0] == "request")
                {
                    taskname = "request";
                    return downloadUrl(urlStart + urls[0]);
                }
                else if (urls[0] == "plant")
                {
                    taskname = "plant";
                    return plantBomb(urlStart + urls[0]);
                }
                else if (urls[0] == "defuse")
                {
                    taskname = "defuse";
                    return defuseBomb(urlStart + urls[0]);
                }
                return downloadUrl(urls[0]);
            } catch (IOException e) {
                return "Unable to retrieve web page. URL my be invalid.";
            }
        }
        @Override
        protected void onPostExecute(String result)
        {
            String output = "nothing";
            if (taskname == "plant")
            {
                Log.d(TAG, "onPostExecute with plant request");
            }
            if (taskname == "defuse")
            {
                Log.d(TAG, "onPostExecute with defuse request");
            }
            else if (taskname == "request") {
                Log.d(TAG, "onPostExecute with bomblocation request");
                try {
                    Log.d(TAG, "result = " + result);
                    JSONArray locationArray = new JSONArray(result);

                    for (int i = 0; i < locationArray.length(); i++) {
                        JSONObject locationObject = locationArray.getJSONObject(i);

                        double x = locationObject.getDouble("x");
                        double y = locationObject.getDouble("y");

                        positionList.add(new Position(x, y, "bomb" + (i+1)));
                        Log.d(TAG, "list length = " + positionList.size());
                    }
                } catch (org.json.JSONException e) {
                    Log.d(TAG, "JSONException in onPostExecute " + e);
                    e.printStackTrace();
                }

                fetchedData = true;
                if (mapIsReady)
                {
                    createMarker();
                }
            }
        }

        private String downloadUrl(String myurl) throws IOException
        {
            InputStream is = null;
            int len = 2000;
            String contentAsString = null;
            try
            {
                Log.d(TAG, "downloadUrl");
                URL url = new URL(myurl);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setReadTimeout(5000);
                conn.setConnectTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();
                int response = conn.getResponseCode();
                Log.d("debug message", "The response is: " + response);
                is = conn.getInputStream();
                contentAsString = readIt(is, len);
            }
            finally
            {
                if (is != null)
                {
                    is.close();
                }
            }
            return contentAsString;
        }

        private String plantBomb(String myurl) throws IOException
        {
            InputStream is = null;
            int len = 2000;
            String contentAsString = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(myurl);
                conn = (HttpURLConnection) url.openConnection();

                JSONObject coord = new JSONObject();
                JSONObject position = new JSONObject();
                position.put("x", playerPos.latitude);
                position.put("y", playerPos.longitude);

                coord.put("position", position);

                byte[] postData = coord.toString().getBytes();

                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("charset", "utf-8");
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(false);

                DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
                wr.write(postData);

                Log.d(TAG, "write!");
                wr.flush();
                wr.close();
                Log.d(TAG, "write.");
                int response = conn.getResponseCode();
                Log.d(TAG, "write...");
                Log.d("debugmessage", "The response is: " + response);
                is = conn.getInputStream();
                contentAsString = readIt(is, len);
            } catch (org.json.JSONException e) {
                Log.d(TAG, "jsonexception in plantbomb request sending");
            } finally {
                if (is != null) {
                    is.close();
                }
                Log.d(TAG, "finally is called....");
            }
            return contentAsString;
        }

        private String defuseBomb(String myurl) throws IOException
        {
            InputStream is = null;
            int len = 2000;
            try {
                URL url = new URL(myurl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                JSONObject coord = new JSONObject();
                JSONObject position = new JSONObject();
                position.put("x", minSpot.latitude);
                position.put("y", minSpot.longitude);
                coord.put("position", position);

                byte[] postData = coord.toString().getBytes();

                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("charset", "utf-8");
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(false);

                DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
                wr.write(postData);

                wr.flush();
                wr.close();

                int response = conn.getResponseCode();
                Log.d("debugmessage", "The response is: " + response);
                is = conn.getInputStream();
                String contentAsString = readIt(is, len);
                return contentAsString;
            } catch (org.json.JSONException e) {
                Log.d(TAG, "jsonexception in defusebomb request sending");
                return null;
            } finally {
                if (is != null) {
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
