package com.sanchitsharma.skytales;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    public ForecastFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        //getMenuInflater()
        // getLayoutInflater()
        inflater.inflate(R.menu.fragmentmenurefresh, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    //class FetchWeatherTask extends AsyncTask<Void, Void, Void>{};

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if(id == R.id.action_refresh){
            FetchWeatherTask task = new FetchWeatherTask();
            task.execute("94043");

            return true;
        }




        return super.onOptionsItemSelected(item);
    }


//Start of JSON Parse code from gist Udacity

                /* The date/time conversion code is going to be moved outside the asynctask later,
         * so for convenience we're breaking it out into its own method now.
         */

    private String getReadableDateString(long time){
        // Because the API returns a unix timestamp (measured in seconds),
        // it must be converted to milliseconds in order to be converted to valid date.
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
        return shortenedDateFormat.format(time);
    }

    /**
     * Prepare the weather high/lows for presentation.
     */
    private String formatHighLows(double high, double low) {
        // For presentation, assume the user doesn't care about tenths of a degree.
        long roundedHigh = Math.round(high);
        long roundedLow = Math.round(low);

        String highLowStr = roundedHigh + "/" + roundedLow;
        return highLowStr;
    }


    //declare an ArrayList<String> to store the list of strings from JSON parse results.\

    ArrayList<String> forecastList = new ArrayList<String>();

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     *
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
            throws JSONException {

        // These are the names of the JSON objects that need to be extracted.
        final String OWM_LIST = "list";
        final String OWM_WEATHER = "weather";
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";
        final String OWM_DESCRIPTION = "main";

        JSONObject forecastJson = null;
        try {
            forecastJson = new JSONObject(forecastJsonStr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JSONArray weatherArray = null;
        try {
            weatherArray = forecastJson.getJSONArray(OWM_LIST);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // OWM returns daily forecasts based upon the local time of the city that is being
        // asked for, which means that we need to know the GMT offset to translate this data
        // properly.

        // Since this data is also sent in-order and the first day is always the
        // current day, we're going to take advantage of that to get a nice
        // normalized UTC date for all of our weather.

        Time dayTime = new Time();
        dayTime.setToNow();

        // we start at the day returned by local time. Otherwise this is a mess.
        int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

        // now we work exclusively in UTC
        dayTime = new Time();

        String[] resultStrs = new String[numDays];
        for (int i = 0; i < weatherArray.length(); i++) {
            // For now, using the format "Day, description, hi/low"
            String day = "";
            String description;
            String highAndLow;

            // Get the JSON object representing the day
            JSONObject dayForecast = null;
            try {
                dayForecast = weatherArray.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // The date/time is returned as a long.  We need to convert that
            // into something human-readable, since most people won't read "1400356800" as
            // "this saturday".
            long dateTime;
            // Cheating to convert this to UTC time, which is what we want anyhow
            dateTime = dayTime.setJulianDay(julianStartDay + i);
            day = getReadableDateString(dateTime);

            // description is in a child array called "weather", which is 1 element long.
            JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
            description = weatherObject.getString(OWM_DESCRIPTION);

            // Temperatures are in a child object called "temp".  Try not to name variables
            // "temp" when working with temperature.  It confuses everybody.
            JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
            double high = temperatureObject.getDouble(OWM_MAX);
            double low = temperatureObject.getDouble(OWM_MIN);

            highAndLow = formatHighLows(high, low);
            resultStrs[i] = day + " - " + description + " - " + highAndLow;
        }

        for (String s : resultStrs) {
            Log.v("LOG_Forecast", "Forecast entry: " + s);
        }

        for (String s : resultStrs)
        {
            forecastList.add(s);
        }

        Log.v("JSONPARSELOG:","result str is:"+resultStrs);

        Log.v("LIST UPDATE LOG:",forecastList.get(0).toString());


        return resultStrs;

    }

    //end of JSON Parse code from gist

    class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        @Override
        protected String[] doInBackground(String... params) {

            if(params.length == 0)
            {
                return null;
            }


            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            try {

                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                //URL url = new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=94043&mode=json&units=metric&cnt=7&appid=08c014d66f968f9bc4813c6e1b910c85");

                //using URI Builder for the url

                Uri.Builder builder = new Uri.Builder();
                builder.scheme("http")
                        .authority("api.openweathermap.org")
                        .appendPath("data")
                        .appendPath("2.5")
                        .appendPath("forecast")
                        .appendPath("daily")
                        .appendQueryParameter("q",params[0])
                        .appendQueryParameter("mode", "json")
                        .appendQueryParameter("units", "metric")
                        .appendQueryParameter("cnt", "7")
                        .appendQueryParameter("appid","08c014d66f968f9bc4813c6e1b910c85");

                URL weatherUrl = null;
                try {
                    weatherUrl = new URL(builder.build().toString());


                    Log.v("LOGTAGURL", weatherUrl.toString());

                    // Create the request to OpenWeatherMap, and open the connection
                    urlConnection = (HttpURLConnection) weatherUrl.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.connect();

                }
             catch (MalformedURLException e) {
                 Log.v("URLERROR", "URL not formed correctly by URI Builder Line 110 in FragmentForecast.java");

             }
                catch (ProtocolException e) {
                    Log.v("URLERROR","Protocol Error in URI builder URL 110");
                } catch (IOException e) {
                    Log.v("URLERROR", "IO Exception URL not formed correctly by URI Builder Line 110 in FragmentForecast.java");

                }

                // Read the input stream into a String
                InputStream inputStream = null;
                try {
                    inputStream = urlConnection.getInputStream();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                        // But it does make debugging a *lot* easier if you print out the completed
                        // buffer for debugging.
                        buffer.append(line + "\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    Log.v("JSON Error","Json returned empty string");
                    return null;
                }
                String networkJson = buffer.toString();
                forecastJsonStr = networkJson;
                Log.v("LOGTAGJSON2",networkJson);



            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
            }

            try{
                return getWeatherDataFromJson(forecastJsonStr,7);
            } catch (JSONException e) {
                Log.v("LOG_RETURN","Return function JSON Parse casued error");
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {

            adapter.clear();

            adapter.addAll(strings);

            //super.onPostExecute(strings);
        }
    }

    // end of boiler palte http code from github
    ArrayAdapter adapter;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        View rootView = inflater.inflate(R.layout.fragment_main,container,false);
        //View v1 = inflater.inf



        String[] forecast_data = {"Monday - Sunny - 99/88","Tuesday - Cloudy - 55/44"};

        //return inflater.inflate(R.layout.fragment_main, container, false);



        for (String s : forecast_data)
        {
            forecastList.add(s);
        }

        //forecast_data = {"Monday - Sunny - 99/88","Tuesday - Cloudy - 55/44"};


        adapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_forecast,R.id.list_item_forecast_textview,forecastList);

        // ListView l;
        //l.findViewById(R.id.listview_forecast);


        ListView forecast = (ListView) rootView.findViewById(R.id.listview_forecast);
        forecast.setAdapter(adapter);
        forecast.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = forecastList.get(position).toString();
                Toast toast = Toast.makeText(getContext(),"Weather is: " +forecast, Toast.LENGTH_LONG);
                toast.show();
            }
        });

        return rootView;



    }




}

