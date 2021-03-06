package tech.khash.weathercompare;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import tech.khash.weathercompare.model.Constant;
import tech.khash.weathercompare.model.Loc;
import tech.khash.weathercompare.utilities.HelperFunctions;
import tech.khash.weathercompare.utilities.SaveLoadList;

public class AddLocationActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleMap.OnCameraIdleListener {

    private static final String TAG = AddLocationActivity.class.getSimpleName();

    //TODO: check against duplicate names

    private GoogleMap mMap;
    private LatLng latLngCamera;
    private LatLng latLngUser;
    private EditText editTextName;
    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient mFusedLocationProviderClient;
    //That is, the last-known location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    //DEFAULTS FOR NOW
    private final LatLng DEFAULT_LAT_LNG_VANCOUVER = new LatLng(49.273367, -123.102950);
    private final float DEFAULT_ZOOM = 14.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_location);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //enable up
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        editTextName = findViewById(R.id.edit_text_name);
        Button saveButton = findViewById(R.id.button_save_location);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveLocation();
            }
        });

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

    }//onCreate


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

        //set the camera idle listener to get the location
        mMap.setOnCameraIdleListener(this);

        //disable map toolbar
        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setZoomControlsEnabled(true); //for emulator since cant zoom out with mouse
        uiSettings.setCompassEnabled(true);

        //for testing, we are moving camera to Vancouver, Science center
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LAT_LNG_VANCOUVER, DEFAULT_ZOOM));
    }//onMapReady

    @Override
    public void onCameraIdle() {
        Log.d(TAG, "onCameraIdle Called");
        //get LatLng
         if (isMapReady()) {
             latLngCamera = mMap.getCameraPosition().target;
         } else {
             Log.d(TAG, "Map is null from onCameraIdle");
         }
    }//onCameraIdle

    /**
     * Inflates the menu, and adds items to the action bar if it is present.
     *
     * @param menu Menu to inflate.
     * @return Returns true if the menu inflated.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.add_location_menu, menu);

        //find the search item
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            setupSearch(searchItem);
        }//if
        return true;
    }//onCreateOptionsMenu

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_find_me:
                findMeMap();
                return true;
            case R.id.action_search:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }//switch
    }//onOptionsItemSelected

    /*------------------------------------------------------------------------------------------
                    ---------------    HELPER METHODS    ---------------
    ------------------------------------------------------------------------------------------*/

    //Helper method for find me
    private void findMeMap() {
        //check for permission first and ask it if needed
        if (HelperFunctions.checkLocationPermission(this)) {
            //we have permission. Check map first and enable my location if necessary
            if (isMapReady()) {
                getDeviceLocation();
            } else {
                HelperFunctions.showToast(this, getResources().getString(R.string.map_not_ready));
            }
        } else {
            //don't have permission, ask for it
            askLocationPermission(this, this);
        }
    }//findMeMap

    /**
     * When we request permission, this fets called back with the results.
     * We figure out if the user has granted the permission, or not and act accordingly
     * @param requestCode : request code int we used when requesting the permission
     * @param permissions : the permission we requested
     * @param grantResults : results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case Constant.LOCATION_PERMISSION_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted,
                    getDeviceLocation();
                    return;
                } else {
                    // permission denied, show the message
                    HelperFunctions.showToast(this, getResources().getString(R.string.location_permission_denied));
                    return;
                }
        }//switch
    }//onRequestPermissionsResult

    //Helper method for getting my location (Permission has already been checked)
    private void getDeviceLocation() {
        try {
            if (HelperFunctions.checkLocationPermission(this)) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            //get the result and save it
                            mLastKnownLocation = task.getResult();
                            setUserLocation();
                            moveCamera(latLngUser, 14.0f);
                        } else {
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(DEFAULT_LAT_LNG_VANCOUVER, DEFAULT_ZOOM));
                        }
                    }
                })
                        .addOnFailureListener(this, new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                            }
                        });
            }

        } catch (Exception e) {
        }//try-catch
    }//getDeviceLocation

    //Sets the latlng of the user from get last location
    private void    setUserLocation() {
        if (mLastKnownLocation == null) {
            return;
        }
        try {
            latLngUser = new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
        } catch (Exception e) {
        }//try-catch
    }//setUserLocation

    //Helper method for moving camera to specific location, with specific zoom
    private void moveCamera(LatLng latLng, float zoom) {
        //check map first
        if (!isMapReady()) {
            HelperFunctions.showToast(this, getResources().getString(R.string.map_not_ready));
            return;
        }//map not ready
        //use try catch in case there is something wrong with the input
        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        } catch (Exception e) {
        }
    }//moveCamera

    //helper method for saving the location
    private void saveLocation() {
        String name = editTextName.getText().toString().trim();
        if (name == null || name.isEmpty()) {
            HelperFunctions.showToast(this, getResources().getString(R.string.name_required_toast));
            return;
        }

        if (latLngCamera == null) {
            HelperFunctions.showToast(this, getResources().getString(R.string.error_getting_location_toast));
            return;
        }

        //create Loc object
        Loc loc = new Loc(name, latLngCamera);
        //add it to the list
        SaveLoadList.addToLocList(this, loc);

        HelperFunctions.showToast(this, "\"" + name + "\"" + " " + getString(R.string.location_added_successfully_toast));

        //return to sender
        Intent returnIntent = new Intent();
        //we return the string name of the loc
        returnIntent.putExtra(Constant.INTENT_EXTRA_LOC_NAME, name);
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }//saveLocation

    //Helper method for setting up app bar search item
    private void setupSearch(MenuItem searchItem) {
        //create a SearchView object using the search menu item
        SearchView searchView = (SearchView) searchItem.getActionView();
        //add hint
        searchView.setQueryHint(getString(R.string.enter_address_hint));
        //closes the keyboard when the user clicks the search button
        searchView.setIconifiedByDefault(true);
        //get a reference to the search box, so we can change the input type to cap words
        int id1 = searchView.getContext().getResources().
                getIdentifier("android:id/search_src_text", null, null);
        EditText searchEditText = searchView.findViewById(id1);
        searchEditText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        // use this method for search process
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            /* Called when the user submits the query. This could be due to a key press on the
                keyboard or due to pressing a submit button. The listener can override the standard
                behavior by returning true to indicate that it has handled the submit request.
                Otherwise return false to let the SearchView handle the submission by launching
                any associated intent. */
            @Override
            public boolean onQueryTextSubmit(String query) {
                // use this method when query submitted
                searchAddress(query);
                return false;
            }

            //Called when the query text is changed by the user.
            @Override
            public boolean onQueryTextChange(String newText) {
                // use this method for auto complete search process
                return false;
            }
        });//query text change listener
    }//setupSearch

    //Helper method for searching the address
    private void searchAddress(String query) {
        //check for geocoder availability
        if (!Geocoder.isPresent()) {
            HelperFunctions.showToast(this, getString(R.string.geocoder_not_available_toast));
            return;
        }
        //Now we know it is available, Create geocoder to retrieve the location
        // responses will be localized for the given Locale. (A Locale object represents a specific geographical,
        // political, or cultural region. An operation that requires a Locale to perform its task is called locale-sensitive )

        //create localized geocoder
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());


        try {
            //the second parameter is the number of max results, here we set it to 3
            List<Address> addresses = geocoder.getFromLocationName(query, 3);
            //check to make sure we got results
            if (addresses.size() < 1) {
                HelperFunctions.showToast(this, getString(R.string.no_results_found_toast));
                return;
            }//if

            //check the map first
            if (mMap == null) {
                return;
            }

            //make a builder to include all points
            LatLngBounds.Builder builder = new LatLngBounds.Builder();

            //clear the map
            mMap.clear();

            //go through all the results and put them on map
            int counter = 0;
            for (Address result : addresses) {
                LatLng latLng = new LatLng(result.getLatitude(), result.getLongitude());
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(latLng));
                //include the marker
                builder.include(latLng);
                counter++;
            }//for

            //don't need to set bounds if there is only one result. Just move the camera
            if (counter <= 1) {
                Address address = addresses.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                moveCamera(latLng, DEFAULT_ZOOM);
                //add the name to the filed
                String featureName = address.getFeatureName();
                if (featureName != null && !featureName.isEmpty()) {
                    editTextName.setText(query);
                }
                return;
            }

            //since we have more than one results, we want to show them all, so we need the builder
            //build the bounds builder
            LatLngBounds bounds = builder.build();
            //Setting the width and height of your screen (if not, sometimes the app crashes)
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            int padding = (int) (width * 0.2); // offset from edges of the map 20% of screen

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding));//this is the pixel padding

        } catch (IOException e) {
        }//try/catch
    }//searchAddress

    private boolean isMapReady() {
        return mMap != null;
    }//isMapReady

    /**
     * Helper method for showing a message to the user informing them about the benefits of turning on their
     * location. and also can direct them to the location settings of their phone
     */
    private void askLocationPermission(final Context context, final Activity activity) {
        //Create a dialog to inform the user about this feature's permission
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        //Chain together various setter methods to set the dialogConfirmation characteristics
        builder.setMessage(R.string.permission_required_text_dialog).setTitle(R.string.permission_required_title_dialog);
        // Add the buttons. We can call helper methods from inside the onClick if we need to
        builder.setPositiveButton(R.string.permission_required_yes_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {

                /*  We need to differentiate if it is the first time we are asking or not
                    If it is, we just ask permission,
                    If it is not, then we will check rationale (it returns false the very first time
                 */
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                boolean firstTime = sharedPreferences.getBoolean(Constant.PREF_KEY_FIRST_TIME_LOCATION, true);
                if (firstTime) {
                    //we dont need to check rationale, just ask
                    if (ContextCompat.checkSelfPermission(context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(activity,
                                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                                Constant.LOCATION_PERMISSION_REQUEST_CODE);
                        //set the boolean to false, we only run this the very first time
                        sharedPreferences.edit().putBoolean(Constant.PREF_KEY_FIRST_TIME_LOCATION, false).apply();
                    }//need permission
                } else {
                    //this is not the first time anymore, so we check rationale
                    if (ContextCompat.checkSelfPermission(context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                        /*
                        Here we check to see if they have selected "never ask again". If that is the
                        case, then shouldShowRequestPermissionRationale will return false. If that
                        is false, and the build version is higher than 23 (that feature is only
                        available to >= 23 then send them to the
                         */
                        if (Build.VERSION.SDK_INT >= 23 && !(activity.shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION))) {
                            //This is the case when the user checked the box, so we send them to the settings
                            HelperFunctions.openPermissionSettings(activity);
                        } else {
                            ActivityCompat.requestPermissions(activity,
                                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                                    Constant.LOCATION_PERMISSION_REQUEST_CODE);
                        }

                    } else {
                        //this is the case that the user has never denied permission, so we ask for it
                        ActivityCompat.requestPermissions(activity,
                                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                                Constant.LOCATION_PERMISSION_REQUEST_CODE);
                    }//if-else build version
                }//if-else first time
            }//positive button
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                //set the layout to visible
                HelperFunctions.showToast(getApplicationContext(),
                        getResources().getString(R.string.location_permission_denied));
            }
        });
        //build and show dialog
        builder.create().show();
    }//askLocationPermission

}//AddLocationActivity
