package com.derrick.googlemap;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.derrick.googlemap.models.Direction;
import com.derrick.googlemap.models.Leg;
import com.derrick.googlemap.models.Route;
import com.derrick.googlemap.models.RouteSummary;
import com.derrick.googlemap.network.ApiClient;
import com.derrick.googlemap.network.ApiInterface;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.PolyUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private static final String TAG = MapsActivity.class.getSimpleName();
    private CameraPosition mCameraPosition;

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient mFusedLocationProviderClient;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final float DEFAULT_ZOOM = 15.5f;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    // Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";


    /**
     * The formatted location address.
     */
    private String mAddressOutput;

    /**
     * Receiver registered with this activity to get the response from FetchAddressIntentService.
     */
    private AddressResultReceiver mResultReceiver;

    private ArrayList<Marker> mRouteMarkerList = new ArrayList<>();
    private Polyline mRoutePolyline;


    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        mResultReceiver = new AddressResultReceiver(new Handler());

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;


        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout) findViewById(R.id.map), false);

                TextView title = ((TextView) infoWindow.findViewById(R.id.title));
                title.setText(marker.getTitle());

                TextView snippet = ((TextView) infoWindow.findViewById(R.id.snippet));
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Prompt the user for permission.
        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();

                            Log.d(TAG, "@Log lat" + mLastKnownLocation.getLatitude());
                            Log.d(TAG, "@Log long" + mLastKnownLocation.getLongitude());


                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));

                            // Determine whether a Geocoder is available.
                            if (!Geocoder.isPresent()) {
                                Toast.makeText(getApplicationContext(), getString(R.string.no_geocoder_available), Toast.LENGTH_LONG).show();
                                return;
                            }

                            // service after fetching the location.
                            startIntentService();


                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void startIntentService() {
        // Create an intent for passing to the intent service responsible for fetching the address.
        Intent intent = new Intent(this, FetchAddressIntentService.class);

        // Pass the result receiver as an extra to the service.
        intent.putExtra(Constants.RECEIVER, mResultReceiver);

        // Pass the location data as an extra to the service.
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastKnownLocation);

        // Start the service. If the service isn't already running, it is instantiated and started
        // (creating a process for it if needed); if it is running then it remains running. The
        // service kills itself automatically once all intents are processed.
        startService(intent);
    }


    /**
     * Prompts the user for permission to use the device location.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    /**
     * Receiver for data sent from FetchAddressIntentService.
     */
    private class AddressResultReceiver extends ResultReceiver {
        AddressResultReceiver(Handler handler) {
            super(handler);
        }

        /**
         * Receives data sent from FetchAddressIntentService and updates the UI in MainActivity.
         */
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string or an error message sent from the intent service.
            mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);

            Log.d(TAG, "@Current address::" + mAddressOutput);

            // Add a marker if an address was found.
            if (resultCode == Constants.SUCCESS_RESULT) {
                //Adding the marker
                /*mMap.addMarker(new MarkerOptions().position(new LatLng(mLastKnownLocation.getLatitude(),
                        mLastKnownLocation.getLongitude())).title(mAddressOutput));*/

                // String formattedAddress = getFormattedAddress(mAddressOutput);
                String formattedAddress = mLastKnownLocation.getLatitude() + "," + mLastKnownLocation.getLongitude();

                // Calling direction API
                ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
                apiInterface.getTopRatedMovies(formattedAddress, getString(R.string.default_destination), getString(R.string.google_maps_key)).enqueue(new Callback<Direction>() {
                    @Override
                    public void onResponse(Call<Direction> call, Response<Direction> response) {


                        if (response.body().getStatus().equals("OK")) {
                            Log.d(TAG, "@Current response routes::" + response.body().getRoutes().toString());

                            Leg leg = response.body().getRoutes().get(0).getLegs().get(0);
                            Route route = response.body().getRoutes().get(0);

                            String startName = leg.getStartAddress();
                            String endName = leg.getEndAddress();
                            Double startLat = leg.getStartLocation().getLat();
                            Double startLng = leg.getStartLocation().getLng();
                            Double endLat = leg.getEndLocation().getLat();
                            Double endLng = leg.getEndLocation().getLng();
                            String overviewPolyline = route.getOverviewPolyline().getPoints();

                            RouteSummary routeSummary = new RouteSummary(startName, endName, startLat, startLng, endLat, endLng, overviewPolyline);

                            setMarkersAndRoute(routeSummary);

                        }

                    }

                    @Override
                    public void onFailure(Call<Direction> call, Throwable t) {

                    }
                });


            }

        }
    }

    private void setMarkersAndRoute(RouteSummary route) {
        LatLng startLatLng = new LatLng(route.getStartLat(), route.getStartLng());
        MarkerOptions startMarkerOptions = new MarkerOptions().position(startLatLng).title(route.getStartName());

        LatLng endLatLng = new LatLng(route.getEndLat(), route.getEndLng());
        MarkerOptions endMarkerOptions = new MarkerOptions().position(endLatLng).title(route.getEndName());

        Marker startMarker = mMap.addMarker(startMarkerOptions);
        Marker endMarker = mMap.addMarker(endMarkerOptions);

        mRouteMarkerList.add(startMarker);
        mRouteMarkerList.add(endMarker);

        mRouteMarkerList.add(startMarker);
        mRouteMarkerList.add(endMarker);

        PolylineOptions polylineOptions = drawRoute();

        List<LatLng> pointsList = PolyUtil.decode(route.getOverviewPolyline());

        for (LatLng point : pointsList) {
            polylineOptions.add(point);
        }

        mRoutePolyline = mMap.addPolyline(polylineOptions);
        mMap.animateCamera(autoZoomLevel(mRouteMarkerList));
    }

    private PolylineOptions drawRoute() {
        PolylineOptions polylineOptions = new PolylineOptions();

        polylineOptions.width(5);

        polylineOptions.geodesic(true);

        polylineOptions.color(ContextCompat.getColor(this, R.color.blue));

        return polylineOptions;
    }


    private String getFormattedAddress(String mAddressOutput) {
        String[] stringList = mAddressOutput.split(",");
        StringBuilder sb = new StringBuilder();
        for (String x : stringList) {
            sb.append(x.trim());
            sb.append("+");
        }
        Log.d(TAG, "@Current formatted:" + sb.substring(0, sb.length() - 1).replaceAll(" ", "+"));

        return sb.substring(0, sb.length() - 1);
    }

    private void showToast(String string) {
        Toast.makeText(getApplicationContext(), string, Toast.LENGTH_LONG).show();
    }


    private CameraUpdate autoZoomLevel(List<Marker> markerList) {
        if (markerList != null && markerList.size() == 1) {
            Double latitude = markerList.get(0).getPosition().latitude;
            Double longitude = markerList.get(0).getPosition().longitude;
            LatLng latLng = new LatLng(latitude, longitude);
            return CameraUpdateFactory.newLatLngZoom(latLng, 17.0f);
        } else {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();

            for (Marker marker : markerList) {
                builder.include(marker.getPosition());
            }

            LatLngBounds bounds = builder.build();

            int padding = 200; // offset from edges of the map in pixels

            return CameraUpdateFactory.newLatLngBounds(bounds, padding);
        }
    }


}
