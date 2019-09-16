package tech.khash.weathercompare;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import tech.khash.weathercompare.adapter.WeatherListAdapterToday;
import tech.khash.weathercompare.model.Constant;
import tech.khash.weathercompare.model.Loc;
import tech.khash.weathercompare.model.Weather;
import tech.khash.weathercompare.utilities.HelperFunctions;
import tech.khash.weathercompare.utilities.NetworkCallsUtils;
import tech.khash.weathercompare.utilities.SaveLoadList;

public class TodayActivity extends AppCompatActivity {

    private static final String TAG = TodayActivity.class.getSimpleName();

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private WeatherListAdapterToday adapter;
    private Loc currentLoc;

    private ArrayList<Weather> weatherArrayList;
    private ArrayList<Loc> locArrayList;

    private Boolean isDay;
    private boolean deviceLocation = false;
    private int tracker;

    private static final int ALERT_CODE_NO_RESULT = 1;
    private static final int ALERT_CODE_MULTIPLE_RESULTS = 2;
    private static final int ALERT_CODE_NO_GEOCODER = 3;
    private static final int ALERT_CODE_UNABLE_FIND_DEVICE = 4;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_today);

        progressBar = findViewById(R.id.progress_bar);
        recyclerView = findViewById(R.id.recycler_view);

        //get the loc id from intent extra
        if (getIntent().hasExtra(Constant.INTENT_EXTRA_LOC_NAME)) {
            String id = getIntent().getStringExtra(Constant.INTENT_EXTRA_LOC_NAME);
            if (!TextUtils.isEmpty(id)) {
                //get the corresponding loc
                Loc loc = SaveLoadList.getLocFromDb(this, id);
                if (loc != null) {
                    currentLoc = loc;
                    setTitle(currentLoc.getName());
                }//null-loc
            }//empty string
        }//has extra

        tracker = 0;

        locArrayList = SaveLoadList.loadLocList(this);

        calculateIsDay();

    }//onCreate

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu Called");
        getMenuInflater().inflate(R.menu.today_menu, menu);

        //find the search item
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            setupSearch(searchItem);
        }//if

        //return true since we have managed it
        return true;
    }//onCreateOptionsMenu

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //get the size of array
        switch (item.getItemId()) {
            case R.id.action_find_me:
                findMe();
                return true;
            case R.id.action_saved_locations:
                showSavedLocations();
                return true;
            case R.id.action_forecast:
                Intent forecastIntent = new Intent(TodayActivity.this, ForecastActivity.class);
                forecastIntent.putExtra(Constant.INTENT_EXTRA_LOC_NAME, currentLoc.getName());
                startActivity(forecastIntent);
                return true;
            case R.id.action_add_locations:
                //TODO: change this for results
                Intent intent = new Intent(TodayActivity.this, AddLocationActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_refresh:
                refresh();
            default:
                return super.onOptionsItemSelected(item);
        }//switch
    }//onOptionsItemSelected

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

    private void searchAddress(String query) {
        //TODO: show dialog for going to add location if no result or more than 1
        //check for geocoder availability
        if (!Geocoder.isPresent()) {
            Log.d(TAG, "Geocoder not available - searchAddress");
            showAddLocationDialog(ALERT_CODE_NO_GEOCODER);
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
                showAddLocationDialog(ALERT_CODE_NO_RESULT);
                Log.d(TAG, "No results - searchAddress");
                return;
            }//if

            //go through all the results and put them on map
            int counter = 0;
            for (Address result : addresses) {
                LatLng latLng = new LatLng(result.getLatitude(), result.getLongitude());
                counter++;
            }//for

            //don't need to set bounds if there is only one result. Just move the camera
            if (counter <= 1) {
                Address address = addresses.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());

                //pass to helper method to set it up
                setUserLocation(latLng);
            } else {
                //more than one result
                showAddLocationDialog(ALERT_CODE_MULTIPLE_RESULTS);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error getting location", e);
        }//try/catch

    }//openSearch

    private void findMe() {
        //check for permission first and ask it if needed
        if (HelperFunctions.checkLocationPermission(this)) {
            //we have permission, get the user's location
            getDeviceLocation();
        } else {
            //don't have permission, ask for it
            HelperFunctions.askLocationPermission(this, this);
        }
    }//findMe

    private void getDeviceLocation() {
        // Construct a FusedLocationProviderClient.
        FusedLocationProviderClient fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(this);
        try {
            if (HelperFunctions.checkLocationPermission(this)) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            //get the result and save it
                            Location location = task.getResult();
                            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                            setUserLocation(latLng);
                        } else {
                            showAddLocationDialog(ALERT_CODE_UNABLE_FIND_DEVICE);
                            Log.e(TAG, "Exception: %s", task.getException());
                        }
                    }
                })
                        .addOnFailureListener(this, new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "Failed to get the last know location", e);
                                showAddLocationDialog(ALERT_CODE_UNABLE_FIND_DEVICE);
                            }
                        });
            } else {
                //permission denied (should never happen since we have already checked it before this call
                Log.wtf(TAG, "Location permission denied from getDeviceLocation");
                return;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error finding my location from getDeviceLocation", e);
        }//try-catch

    }//getDeviceLocation

    private void setUserLocation(LatLng latLng) {
        if (latLng == null) {
            Log.d(TAG, "setUserLocation - location is null");
            return;
        }

        //set the boolean to true
        deviceLocation = true;

        //create a new Loc
        final Loc loc = new Loc(latLng);

        Log.d(TAG, "USER LOCATION: " + latLng.toString());

        //get the AW code and set all URLs
        progressBar.setVisibility(View.VISIBLE);

        //first we need to get the codeURL and then get the code
        URL locationCodeUrl = loc.getLocationCodeUrlAW();
        if (locationCodeUrl == null) {
            Log.d(TAG, "setUserLocation - codeURL = null");
            return;
        }

        NetworkCallsUtils.NetworkCallAccuWeatherCode networkCallAccuWeatherCode = new
                NetworkCallsUtils.NetworkCallAccuWeatherCode(new NetworkCallsUtils.NetworkCallAccuWeatherCode.AsyncResponse() {
            /**
             *  This gets called when the code is ready from the background network
             *  call and we set the data on loc
             * @param output : HashMap containing key and name
             */
            @Override
            public void processFinish(HashMap<String, String> output) {
                if (output == null) {
                    Log.d(TAG, "processFinish - null response");

                    loc.setAllUrls();

                    currentLoc = loc;

                } else {
                    //set key and name
                    String key = output.get(Constant.AW_KEY);
                    String name = output.get(Constant.AW_NAME);

                    //set the key
                    loc.setKeyAW(key);

                    //set all urls
                    loc.setAllUrls();

                    //set current loc
                    currentLoc = loc;

                    //set the title
                    //TODO: in case this fails, we will set it in WB (maybe remove this)
                    if (!TextUtils.isEmpty(name)) {
                        setTitle(name);
                    } else {
                        setTitle("Device Location");
                    }
                }

                //check to see if we need to calculate isDay first, otherwise get all weather
                if (isDay == null) {
                    calculateIsDay();
                } else {
                    getAllWeather();
                }

            }//processFinish

        });
        networkCallAccuWeatherCode.execute(locationCodeUrl);

    }//setUserLocation

    private void showSavedLocations() {
        showLocListDialog(this, locArrayList);
    }//showSavedLocations

    private void refresh() {

    }//refresh

    /*------------------------------------------------------------------------------------------
                    ---------------    HELPER METHODS    ---------------
    ------------------------------------------------------------------------------------------*/


    private void calculateIsDay() {
        if (currentLoc == null) {
            Log.d(TAG, "calculateIsDay - currentLoc is null");
            return;
        }//null loc

        //make the progress bar visible
        progressBar.setVisibility(View.VISIBLE);

        URL sinriseSunsetUrl = currentLoc.getSunriseSunsetUrl();
        if (sinriseSunsetUrl == null) {
            Log.d(TAG, "calculateIsDay - currentUrl = null");
            //TODO: manage this case
        } else {
            NetworkCallsUtils.SunriseSunsetTask sunriseSunsetTask = new
                    NetworkCallsUtils.SunriseSunsetTask(new NetworkCallsUtils.SunriseSunsetTask.AsyncResponse() {
                @Override
                public void processFinished(Boolean result) {
                    isDay = result;
                    getAllWeather();
                }
            });
            sunriseSunsetTask.execute(sinriseSunsetUrl);
        }

    }//calculateIsDay

    private void getAllWeather() {

        if (currentLoc == null) {
            Log.d(TAG, "getAllWeather - currentLoc is null");
            //remove progress bar if it is running
            if (progressBar.getVisibility() == View.VISIBLE) {
                progressBar.setVisibility(View.GONE);
            }
            return;
        }//null loc

        //show progress bar if it is not visible
        if (progressBar.getVisibility() != View.VISIBLE) {
            progressBar.setVisibility(View.VISIBLE);
        }

        //refresh the array list
        weatherArrayList = new ArrayList<>();

        kickOffAccuWeather();
        kickOffDarkSky();
        kickOffWeatherBit();
        kickOffWeatherUnlocked();

    }//getAllWeather

    private void kickOffAccuWeather() {
        //check for null loc
        if (currentLoc == null) {
            Log.d(TAG, "kickOffAccuWeather - currentLoc = null");
            return;
        }

        if (!currentLoc.hasKeyAW()) {
            Log.d(TAG, "kickOffAccuWeather - currentLoc.hasKey = false");
            if (tracker == 3) {
                //remove progress bar and reset the tracker
                progressBar.setVisibility(View.GONE);
                tracker = 0;
                //start adapter
                updateAdapter();
            } else {
                //this means this is part the group load and just increment
                tracker++;
            }
            return;
        }

        //get the location code
        URL todayUrlAW = currentLoc.getTodayUrlAW();

        if (todayUrlAW == null) {
            Log.d(TAG, "kickOffAccuWeather - currentUrl = null");
            if (tracker == 3) {
                //remove progress bar and reset the tracker
                progressBar.setVisibility(View.GONE);
                tracker = 0;
                //start adapter
                updateAdapter();
            } else {
                //this means this is part the group load and just increment
                tracker++;
            }
        } else {
            Log.d(TAG, "Current URL - AW : " + todayUrlAW.toString());
            NetworkCallsUtils.AccuWeatherTodayTask accuWeatherTodayTask = new
                    NetworkCallsUtils.AccuWeatherTodayTask(new NetworkCallsUtils.AccuWeatherTodayTask.AsyncResponse() {
                @Override
                public void processFinish(Weather output) {
                    //only add if it is not null
                    if (output != null) {
                        weatherArrayList.add(output);
                    }
                    //if it is 3 (meaning all tasks are finished), remove, otherwise increment
                    if (tracker == 3) {
                        //remove progress bar and reset the tracker
                        progressBar.setVisibility(View.GONE);
                        tracker = 0;
                        //start adapter
                        updateAdapter();
                    } else {
                        //this means this is part the group load and just increment
                        tracker++;
                    }

                }//processFinish
            });
            accuWeatherTodayTask.execute(todayUrlAW);
        }//if-else URL
    }//kickOffAccuWeather

    private void kickOffDarkSky() {
        //check for null loc
        if (currentLoc == null) {
            Log.d(TAG, "kickOffDarkSky - currentLoc = null");
            return;
        }

        URL todayUrlDS = currentLoc.getForecastUrlDS();

        if (todayUrlDS == null) {
            Log.d(TAG, "kickOffDarkSky - forecastUrl = null");
        } else {
            Log.d(TAG, "Forecast URL - DS : " + todayUrlDS.toString());
            NetworkCallsUtils.DarkSkyTodayTask darskSkyTodayTask = new
                    NetworkCallsUtils.DarkSkyTodayTask(new NetworkCallsUtils.DarkSkyTodayTask.AsyncResponse() {
                @Override
                public void processFinish(Weather output) {
                    //only add if it is not null
                    if (output != null) {
                        weatherArrayList.add(output);
                    }
                    //if it is 3 (meaning all tasks are finished), remove, otherwise increment
                    if (tracker == 3) {
                        //remove progress bar and reset the tracker
                        progressBar.setVisibility(View.GONE);
                        tracker = 0;
                        //start adapter
                        updateAdapter();
                    } else {
                        //this means this is part the group load and just increment
                        tracker++;
                    }

                }//processFinish
            });
            darskSkyTodayTask.execute(todayUrlDS);
        }//if-else URL
    }//kickOffDarkSky

    private void kickOffWeatherBit() {
        //check for null loc
        if (currentLoc == null) {
            Log.d(TAG, "kickOffWeatherBit - currentLoc = null");
            return;
        }

        URL todayUrlWB = currentLoc.getForecastUrlWB();

        if (todayUrlWB == null) {
            Log.d(TAG, "kickOffWeatherBit - todayURL = null");
        } else {
            Log.d(TAG, "Today URL - WB : " + todayUrlWB.toString());
            NetworkCallsUtils.WeatherBitTodayTask weatherBitTodayTask = new
                    NetworkCallsUtils.WeatherBitTodayTask(new NetworkCallsUtils.WeatherBitTodayTask.AsyncResponse() {
                @Override
                public void processFinish(Weather output) {
                    //only add if it is not null
                    if (output != null) {
                        weatherArrayList.add(output);
                    }

                    //is this is from device location, set the name
                    //TODO: testing
                    if (deviceLocation) {
                        setTitle(output.getCityName());
                    }

                    //if it is 3 (meaning all tasks are finished), remove, otherwise increment
                    if (tracker == 3) {
                        //remove progress bar and reset the tracker
                        progressBar.setVisibility(View.GONE);
                        tracker = 0;
                        //start adapter
                        updateAdapter();
                    } else {
                        //this means this is part the group load and just increment
                        tracker++;
                    }

                }//processFinish
            });
            weatherBitTodayTask.execute(todayUrlWB);
        }//if-else URL
    }//kickOffWeatherBit

    private void kickOffWeatherUnlocked() {
        //check for null loc
        if (currentLoc == null) {
            Log.d(TAG, "kickOffWeatherUnlocked - currentLoc = null");
            return;
        }

        URL todayUrlWU = currentLoc.getForecastUrlWU();

        if (todayUrlWU == null) {
            Log.d(TAG, "kickOffWeatherUnlocked - todayURL = null");
        } else {
            Log.d(TAG, "Today URL - WU : " + todayUrlWU.toString());
            NetworkCallsUtils.WeatherUnlockedTodayTask weatherUnlockedTodayTask = new
                    NetworkCallsUtils.WeatherUnlockedTodayTask(new NetworkCallsUtils.WeatherUnlockedTodayTask.AsyncResponse() {
                @Override
                public void processFinish(Weather output) {
                    //only add if it is not null
                    if (output != null) {
                        weatherArrayList.add(output);
                    }
                    //if it is 3 (meaning all tasks are finished), remove, otherwise increment
                    if (tracker == 3) {
                        //remove progress bar and reset the tracker
                        progressBar.setVisibility(View.GONE);
                        tracker = 0;
                        //start adapter
                        updateAdapter();
                    } else {
                        //this means this is part the group load and just increment
                        tracker++;
                    }

                }//processFinish
            });
            weatherUnlockedTodayTask.execute(todayUrlWU);
        }//if-else URL
    }//kickOffWeatherUnlocked

    private void updateAdapter() {
        if (weatherArrayList == null || weatherArrayList.size() < 1) {
            Log.d(TAG, "updateAdapter - weatherArrayList null/empty");
            return;
        }

        if (isDay == null) {
            Log.d(TAG, "updateAdapter - isDay is null");
            adapter = new WeatherListAdapterToday(this, weatherArrayList, true);
        } else {
            adapter = new WeatherListAdapterToday(this, weatherArrayList, isDay);
        }

        // Connect the adapter with the RecyclerView.
        recyclerView.setAdapter(adapter);
        // Give the RecyclerView a horizontal layout manager.
        recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        //Add divider between items using the DividerItemDecoration
        DividerItemDecoration decoration = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.HORIZONTAL);
        recyclerView.addItemDecoration(decoration);
    }//updateAdapter

    /**
     * This creates a custom dialog showing all the saved city names for the user to choose, and
     * return the corresponding Loc object
     *
     * @param activity     : host activity
     * @param locArrayList : list of Locs
     */
    private void showLocListDialog(final Activity activity, final ArrayList<Loc> locArrayList) {
        //check for empty list
        if (locArrayList == null || locArrayList.size() < 1) {
            Log.d(TAG, "showLocListDialog - locArrayList is null/empty ");
            HelperFunctions.showToast(activity.getBaseContext(), "No saved locations");
            return;
        }//if

        //create our name list
        ArrayList<String> namesArrayList = new ArrayList<>();
        for (Loc loc : locArrayList) {
            namesArrayList.add(loc.getName());
        }

        if (namesArrayList == null || locArrayList.size() < 1) {
            Log.d(TAG, "showLocListDialog - namesArrayList is null/empty ");
            return;
        }

        final Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.custom_dialog);


        ListView listView = dialog.findViewById(R.id.list_view);
        ArrayAdapter arrayAdapter = new ArrayAdapter(activity, R.layout.dialog_list_item,
                R.id.text_city_name, namesArrayList);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                //set current loc
                currentLoc = locArrayList.get(position);
                //get the weather
                getAllWeather();
                //set the title
                activity.setTitle(currentLoc.getName());
                //close dialog
                dialog.dismiss();
            }
        });

        dialog.show();
    }//showLocListDialog

    private void showAddLocationDialog(int alertCode) {

        //Instantiate an AlertDialog.Builder with its constructor
        AlertDialog.Builder builderQuestion = new AlertDialog.Builder(this);

        //set the title based on alert code
        switch (alertCode) {
            case ALERT_CODE_NO_RESULT:
                builderQuestion.setMessage("No results found." +
                        "\nWould you like to add location manually?").setTitle("No results");
                break;
            case ALERT_CODE_MULTIPLE_RESULTS:
                builderQuestion.setMessage("Multiple results were found." +
                        "\nWould you like to add location manually?").setTitle("Multiple results");
                break;
            case ALERT_CODE_NO_GEOCODER:
                builderQuestion.setMessage("Unable to finish search." +
                        "\nWould you like to add location manually?").setTitle("Error");
                break;
            case ALERT_CODE_UNABLE_FIND_DEVICE:
                builderQuestion.setMessage("Unable to locate device." +
                        "\nWould you like to add location manually?").setTitle("Error");
                break;
            default:
                return;

        }//switch

        // Add the buttons. We can call helper methods from inside the onClick if we need to
        builderQuestion.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(TodayActivity.this, AddLocationActivity.class);
                //TODO: add extra and do startActivityForResults
                startActivity(intent);
            }
        });
        builderQuestion.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //what happens on this click goes here.
                dialog.dismiss();
            }
        });

        final AlertDialog dialog = builderQuestion.create();
        dialog.show();
    }//showAddLocationDialog


}//TodayActivity
