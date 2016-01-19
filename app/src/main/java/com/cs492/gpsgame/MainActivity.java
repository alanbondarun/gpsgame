package com.cs492.gpsgame;

import android.Manifest;
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
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.nearest_place_layout);

        // initialize position list
        positionList = new ArrayList<>();
        positionList.add(new Position(36.374128, 127.365497, "CSBuilding"));
        positionList.add(new Position(36.373670, 127.356673, "Armkwan"));
        positionList.add(new Position(36.372988, 127.359924, "Taewoolkwan"));
        positionList.add(new Position(36.368425, 127.356792, "Heemangkwan"));
        positionList.add(new Position(36.370870, 127.355730, "Nanumkwan"));
        positionList.add(new Position(36.372300, 127.361566, "SportsComplex"));
        positionList.add(new Position(36.369561, 127.362435, "Library"));
        positionList.add(new Position(36.368187, 127.363785, "KIBuilding"));
        positionList.add(new Position(36.368801, 127.365580, "EEBuilding"));
        positionList.add(new Position(36.365907, 127.363670, "MainGate"));
        positionList.add(new Position(36.364153, 127.358863, "Backdoor"));
        positionList.add(new Position(36.371060, 127.366749, "Sejongkwan"));
        positionList.add(new Position(36.367442, 127.360111, "CoffeeBean"));

        // initialize variables for layout
        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);
        txtNearestSpot = (TextView) findViewById(R.id.txtNearestSpot);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchData();
                //requestingLocationUpdates = true;
                //startLocationUpdates();
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
            txtNearestSpot.setText("no network connection available.");
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
                    JSONObject jsonObject = locationArray.getJSONObject(i);
                    output = jsonObject.toString();
                    Log.d(TAG, output);
                }
            }
            catch (org.json.JSONException e)
            {
                Log.d(TAG, "JSONEXCEPTION BRO.......try harder man");
            }
            txtNearestSpot.setText(output);
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
    protected void onSaveInstanceState(Bundle savedInstanceState)
    {
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
}
