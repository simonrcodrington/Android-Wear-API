package com.example.simon.androidweardatalayer;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

//Mobile Profile
public class MainActivity extends AppCompatActivity implements
        DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    private Activity activity;
    private GoogleApiClient googleClient;
    private TextView messageContainer;

    //on successful connection to play services, add data listner
    public void onConnected(Bundle connectionHint) {
        Wearable.DataApi.addListener(googleClient, this);
    }

    //on resuming activity, reconnect play services
    public void onResume(){
        super.onResume();
        googleClient.connect();
    }

    //on suspended connection, remove play services
    public void onConnectionSuspended(int cause) {
        Wearable.DataApi.removeListener(googleClient, this);
    }

    //pause listener, disconnect play services
    public void onPause(){
        super.onPause();
        Wearable.DataApi.removeListener(googleClient, this);
        googleClient.disconnect();
    }

    //On failed connection to play services, remove the data listener
    public void onConnectionFailed(ConnectionResult result) {
        Wearable.DataApi.removeListener(googleClient, this);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.activity = this;

        //data layer
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        messageContainer = (TextView) findViewById(R.id.messageContainer);

    }


    //watches for data item
    public void onDataChanged(DataEventBuffer dataEvents) {
        for(DataEvent event: dataEvents){

            //data item changed
            if(event.getType() == DataEvent.TYPE_CHANGED){

                DataItem item = event.getDataItem();
                DataMapItem dataMapItem = DataMapItem.fromDataItem(item);

                //received initiation message, start the process!
                if(item.getUri().getPath().equals("/apiurl")){


                    String message = dataMapItem.getDataMap().getString("message");
                    messageContainer.setText(message);

                    //BUILD API ARGUMENTS
                    //populate our API information, in preparation for our API call
                    APIInformation apiInformation = setUpAPIInformation();


                    //EXECUTE ASYNC TASK
                    APIAsyncTask asyncTask = new APIAsyncTask();
                    asyncTask.execute(apiInformation);

                }
            }
        }
    }


    //checks to see if we are online (and can access the net)
    protected boolean isOnline(){
        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        boolean connected = false;
        if((networkInfo != null) && (networkInfo.isConnected())){
            connected = true;
        }

        return connected;
    }

    //populates information for our API
    protected APIInformation setUpAPIInformation(){

        APIInformation apiInformation = new APIInformation();

        apiInformation.setAPIEndpoint("http://www.worldtides.info/api");
        HashMap arguments = new HashMap<String, String>();

        arguments.put("key", "1d3d0a79-5d7d-48d3-9e80-a5383e53eba2");
        arguments.put("heights",""); //we want the heights only
        arguments.put("lat", "-34.057440"); //cronulla sydney
        arguments.put("lon", "151.152190"); //cronulla sydney
        arguments.put("step", "21600"); //6 hours
        arguments.put("length", "21600"); //6 hours

        //determine the next time period (after right now)
        Long currentTime = System.currentTimeMillis();
        String time = String.valueOf(currentTime / 1000L);
        arguments.put("start", time);

        apiInformation.setAPIArguments(arguments);
        apiInformation.setAPIUrl();

        return apiInformation;
    }

    //main async task to connect to our API and collect a response
    public class APIAsyncTask extends AsyncTask<APIInformation, String, HashMap> {

        //execute before we start
        protected void onPreExecute() {
            super.onPreExecute();
        }

        //execute background task
        protected HashMap doInBackground(APIInformation... params) {

            APIInformation apiInformation = params[0];
            boolean isOnline = isOnline();
            HashMap result;

            if(isOnline()){

                //perform a HTTP request
                APIUrlConnection apiUrlConnection = new APIUrlConnection();

                //get the result back and process
                result = apiUrlConnection.GetData(apiInformation.getAPIUrl());

            }else{
                //we're not online, flag the error
                result = new HashMap();
                result.put("type", "failure");
                result.put("data", "Not currrently online, can't connect to API");
            }
            return result;
        }

        //update progress
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
        }

        //Execute once we're done
        protected void onPostExecute(HashMap result) {
            super.onPostExecute(result);

            //build our message back to the wearable (either with data or a failure message)
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/responsemessage");
            putDataMapRequest.getDataMap().putLong("time", new Date().getTime());

            //success (we collected our data from the API)
            if(result.get("type") == "success"){
                //get the json response data string
                String data = (String) result.get("data");

                //create a new json object
                try{
                    JSONObject jsonObject = new JSONObject(data);
                    if(jsonObject.has("heights")){

                        JSONArray heights = (JSONArray) jsonObject.get("heights");
                        //loop through all 'heights' objects to get data

                        for(int i = 0; i < heights.length(); i++){
                            //get the specific object from the set
                            JSONObject heightObject = heights.getJSONObject(i);
                            //get our time and height values
                            Integer unixTime = Integer.parseInt(heightObject.getString("dt"));
                            String height = heightObject.getString("height");

                            //need to process the values to make them readable.
                            String heightTrimmed = height.substring(0, 5);

                            //convert date unix string to a human readable format
                            Date date = new Date(unixTime * 1000L);
                            DateFormat format = new SimpleDateFormat("dd/MM/yyyy hh:mm a");
                            String dateFormatted = format.format(date);

                            //add our data to be passed back to the wearable
                            putDataMapRequest.getDataMap().putString("unixTime", dateFormatted);
                            putDataMapRequest.getDataMap().putString("height", heightTrimmed);
                        }
                    }else{
                        Log.d("error", "there was no height parm returned from the API");
                        putDataMapRequest.getDataMap().putString("error", "There was an issue processing the JSON object returned from API");
                    }

                }catch(Exception e){
                    //couldn't create the JSON object
                    Log.d("error", "error creating the json object: " + e.getMessage());
                    putDataMapRequest.getDataMap().putString("error", "There was an issue processing the JSON object returned from API");
                }

            }
            //failure (couldn't connect to the API or collect data)
            else if(result.get("type") == "failure"){
                Log.d("error", "There was an issue connecting to the API.");
                putDataMapRequest.getDataMap().putString("error", result.get("error").toString());
            }

            //finalise our message and send it off (either success or failure)
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            putDataRequest.setUrgent();
            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(googleClient, putDataRequest);
        }
    }


}
