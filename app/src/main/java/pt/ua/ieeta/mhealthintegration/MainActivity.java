package pt.ua.ieeta.mhealthintegration;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import Bio.Library.namespace.BioLib;

public class MainActivity extends Activity {

    private String[] device_address = {"00:23:FE:00:07:05", "00:23:FE:00:06:92", "00:23:FE:00:0B:61"};
    private int dataFreq=0;
    private BioLib lib;
    private String access_token = null;
    Globals g = Globals.getInstance();
    private TextView heartValue;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    // For ECG
    private LinearLayout chartLyt;
    private GraphicalView chartView;
    private boolean flag;
    private int count=0;
    private byte[] allbytes = new byte[2500];
    private boolean toSave = false;
    private boolean toSaveHR = false;
    XYSeries series = new XYSeries("ECG");
    private boolean omh = false;


    private final Handler dataHandler = new Handler(){
        @Override
        public void handleMessage(Message msg)
        {
            //Log.d("Connected to device", "IN");
            switch (msg.what)
            {
                case BioLib.MESSAGE_READ:
                    //Log.d("OnHANDLER - RECEIVED: " ,""+ msg.arg1);
                    break;
                case BioLib.MESSAGE_DATA_UPDATED:
                    BioLib.Output out = (BioLib.Output) msg.obj;
                    Log.d("OnHANDLER - Battery", "" +out.battery);

                    heartValue.setText("" + out.pulse);
                    if (toSaveHR) {
                        Log.d("OnHANDLER - Pulse", "" + out.pulse);
                        toSaveHR = false;
                        try {
                            //TODO: FIX ME
                            if(new heartRateSendtoDBTask().execute(""+out.pulse).get()) {
                                Toast toast = Toast.makeText(getApplicationContext(), "Heart Rate saved", Toast.LENGTH_LONG);
                                toast.show();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (ExecutionException e) {
                            e.printStackTrace();
                        }
                    }

                    break;
                case BioLib.MESSAGE_ECG_STREAM:
                    byte[][] ecg = (byte[][]) msg.obj;
                    byte[] ecg_data = ecg[0];
                    int [] data = new int [500];
                    for(int i=0;i<500;i++){
                        data[i]=ecg_data[i] & 0xFF;
                    }

                    updateECGChart(ecg_data);

                    //TODO: Here send data to AsyncTask

                    if (toSave && dataFreq < 5) {
                        dataFreq++;
                        Log.d("OnHANDLER - ECG",Arrays.toString(data));
                        new ecgSendtoDBTask().execute(Arrays.toString(data));
                    }

                    break;
            }
        }
    };

    private void updateECGChart(byte[] ecg) {



        chartLyt = (LinearLayout) findViewById(R.id.chart);

        if(!flag)
        {
            Button bt = (Button) findViewById(R.id.pushECG);
            bt.setVisibility(View.VISIBLE);
            Button bt2 = (Button) findViewById(R.id.pushHeartRate);
            bt2.setVisibility(View.VISIBLE);
            TextView ecg_text = (TextView) findViewById(R.id.ecgView);
            ecg_text.setVisibility(View.VISIBLE);
            TextView heart_text = (TextView) findViewById(R.id.heartView);
            heart_text.setVisibility(View.VISIBLE);
            heartValue.setVisibility(View.VISIBLE);

            if (chartView != null)
                chartView.clearFocus();
        }
        int x=0;

        int y=count*500;

        if (count>3) {
            y = 2000;
            for(x=0;x<2000;x++)
                allbytes[x] = allbytes[x+500];
        }

        series.clear();
        for(x=0;x<500;x++)
        {
            //if(x%2==0){
            allbytes[y]=ecg[x];
            y++;
            //}
        }
        count++;


        for (x = 0; x < 2500; x++) {
            series.add(x, allbytes[x] & 0xFF);
            int z = allbytes[x]&0xFF;
            //Log.d("ECGVALUES", "" + z);
        }

        XYSeriesRenderer renderer = new XYSeriesRenderer();
        renderer.setLineWidth(0.2f);
        renderer.setColor(Color.RED);
        renderer.setDisplayBoundingPoints(true);
        //renderer.setPointStyle(PointStyle.CIRCLE);
        renderer.setPointStrokeWidth(0.5f);
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
        dataset.addSeries(series);

        XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
        mRenderer.addSeriesRenderer(renderer);

        mRenderer.setMarginsColor(Color.argb(0x00, 0xff, 0x00, 0x00)); // transparent margins

        mRenderer.setPanEnabled(false, false);
        mRenderer.setYAxisMax(170);
        mRenderer.setYAxisMin(100);
        mRenderer.setShowGrid(true); // we show the grid

        chartView= ChartFactory.getLineChartView(getApplicationContext(), dataset, mRenderer);
        chartLyt.removeAllViews();
        chartLyt.addView(chartView, 0);

        flag=false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fillSpinner();
        heartValue = (TextView) findViewById(R.id.heartValue);
        pref = getSharedPreferences("info", MODE_PRIVATE);

    }

    private void fillSpinner() {
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.devices_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

    }

    public void connectToDevice(View v) {
        Log.d("connectToDevice", "IN");
        Spinner MySpinner = (Spinner)findViewById(R.id.spinner);
        Integer indexValue = MySpinner.getSelectedItemPosition();
        String address = device_address[indexValue];
        Log.d("connectToDevice:","address("+address+")");

        try {

            lib = new BioLib(this, dataHandler);

            lib.Connect(address,5);

        }catch (Exception e) {
            e.printStackTrace();
            Toast toast = Toast.makeText(this.getApplicationContext(), "Connection with device " + MySpinner.getSelectedItem().toString() + " is not available, Device should be Paired, Try again", Toast.LENGTH_LONG);
            toast.show();
            Log.e("connectToDevice","Connection failed!");

        }
    }

    public void goToHistory(View v) {
        if (checkLogin()) {
            Intent intent = new Intent(this, heartRateActivity.class);
            startActivity(intent);
        }
    }

    public boolean checkLogin() {

        try{
            access_token = g.getToken();
        }catch(Exception e){
            e.printStackTrace();
        }
        if(access_token == null) {

            Toast toast = Toast.makeText(this.getApplicationContext(), "You Should Login first", Toast.LENGTH_LONG);
            toast.show();
            Intent intent = new Intent(this, Login.class);
            startActivity(intent);
        }else
            return true;
        return false;

    }

    public void pushToResourceECG(View v) {
        if (checkLogin()) {
            int episode = 0;
            try {
                episode = pref.getInt(g.getUsername(), 0);
            }catch(Exception e){

            }
            if (episode == 0) {
                editor = pref.edit();
                editor.putInt(g.getUsername(),1);
                editor.commit();
            }else {
                editor = pref.edit();
                editor.putInt(g.getUsername(),pref.getInt(g.getUsername(), 0) + 1);
                editor.commit();
            }
            Log.d("pushToResource", "IN");
            toSave = true;
            dataFreq = 0;
            //new ecgSendtoDBTask().execute("[239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 171, 171, 239, 239, 239, 239, 239, 239, 210, 36, 5, 1, 2, 189, 239, 239, 239, 239, 66, 9, 1, 0, 0, 111, 235, 239, 239, 239, 53, 7, 1, 0, 0, 141, 239, 239, 239, 239, 65, 9, 1, 0, 0, 128, 238, 239, 239, 233, 46, 6, 1, 0, 0, 114, 236, 239, 239, 239, 60, 8, 1, 0, 0, 0, 78, 137, 35, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 85, 232, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 239, 208, 231, 239, 239, 239, 239, 73, 10, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]");
        }

    }

    public void pushToResourceHR(View v) {
        if(checkLogin()) {
            Log.d("pushToResource", "IN");
            toSaveHR = true;
            dataFreq = 0;
            new heartRateSendtoDBTask().execute("66");
        }
    }

    private class ecgSendtoDBTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {

            Log.d("ON AsyncTask","IN");
            JSONObject json=new JSONObject();
            JSONArray values = new JSONArray();

            String domain = getResources().getString(R.string.server_ip);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

            String dateString = sdf.format(new Date());

            JSONObject header = null;
            JSONObject data = null;
            JSONObject provenance = null;
            JSONObject schema_id = null;
            JSONObject time_frame = null;
            JSONObject ecg_data = null;
            JSONObject body = null;
            Log.d("json array",params[0]);
            try {
                provenance  = new JSONObject().put("source_name","MHealthIntegration-App").put("source_creation_date_time",dateString+"Z").put ("modality","sensed");
                schema_id = new JSONObject().put("namespace", "omh").put("name","ecg").put("version","1.0");
                header = new JSONObject().put("id", UUID.randomUUID().toString()).
                        put("acquisition_provenance",provenance).
                        put("schema_id",schema_id);


                time_frame = new JSONObject().put("date_time",dateString+"Z");
                JSONArray array = new JSONArray(params[0]);

                ecg_data = new JSONObject().put("unit","uV").
                        put("values",array).put("part_number",dataFreq).put("episode", pref.getInt(g.getUsername(), 0));

                body = new JSONObject().put("effective_time_frame", time_frame).put("ecg_data", ecg_data);

                data = new JSONObject().put("header", header).
                        put("body", body);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                URL url = new URL("http://"+domain+":8083/v1.0.M1/dataPoints");
                HttpURLConnection conn = null;
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization","Bearer "+g.getToken());
                conn.setRequestProperty("Content-Type", "application/json");
                conn.connect();


                String input = data.toString();
                Log.d("PUT DataPoint:",input);

                OutputStream os=null;

                os = conn.getOutputStream();
                os.write(input.getBytes());
                os.flush();
                if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                    System.out.println("Error:"+conn.getResponseCode());
                    return false;
                }
                else{
                    System.out.println("created with Sucess");
                }
                conn.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

    }

    private class heartRateSendtoDBTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {

            Log.d("ON AsyncTask","IN");
            JSONObject json=new JSONObject();
            JSONArray values = new JSONArray();

            String domain = getResources().getString(R.string.server_ip);
            StringBuilder received = new StringBuilder();

            if (omh) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

                String dateString = sdf.format(new Date());

                JSONObject data = null;
                try {
                    JSONObject header = null;
                    JSONObject provenance = null;
                    JSONObject schema_id = null;
                    JSONObject time_frame = null;
                    JSONObject heart_rate = null;
                    JSONObject body = null;
                    provenance  = new JSONObject().put("source_name","MHealthIntegration-App").put("source_creation_date_time",dateString+"Z").put ("modality","sensed");
                    schema_id = new JSONObject().put("namespace", "omh").put("name","heart-rate").put("version","1.0");

                    header = new JSONObject().put("id", UUID.randomUUID().toString()).
                            put("acquisition_provenance",provenance).
                            put("schema_id",schema_id).put("user_id", g.getUsername());

                    time_frame = new JSONObject().put("date_time",dateString+"Z");
                    heart_rate = new JSONObject().put("unit","beats/min").
                            put("value",Integer.parseInt(params[0]));

                    body = new JSONObject().put("effective_time_frame", time_frame).put("heart_rate", heart_rate);

                    data = new JSONObject().put("header", header).
                            put("body", body);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                try {
                    URL url = new URL("http://"+domain+":8083/v1.0.M1/dataPoints");
                    HttpURLConnection conn = null;
                    conn = (HttpURLConnection) url.openConnection();

                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Authorization","Bearer "+g.getToken());
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.connect();

                    String input = data.toString();
                    Log.d("PUT DataPoint:",input);

                    OutputStream os=null;
                    os = conn.getOutputStream();
                    os.write(input.getBytes());
                    os.flush();

                    if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                        System.out.println("Error:"+conn.getResponseCode());
                        return false;
                    }
                    else{
                        System.out.println("created with Success");
                    }

                    conn.disconnect();
                } catch (ProtocolException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

                String dateString = sdf.format(new Date());

                JSONObject data = null;
                try {
                    JSONObject code = null;
                    JSONObject subject = null;
                    JSONObject valueQuantity = null;
                    JSONArray coding = null;
                    coding = new JSONArray().put(0,new JSONObject().put("system", "http://loinc.org").put("code", "8867-4")
                            .put("display", "heart_rate"));

                    code  = new JSONObject().put("coding", coding).put("text","heart_rate");

                    subject = new JSONObject().put( "reference", "Patient/" + g.getUsername());

                    valueQuantity = new JSONObject().put("value", Integer.parseInt(params[0])).put("unit", "{beats}/min")
                            .put("system", "http://unitsofmeasure.org").put("code", "{beats}/min");


                    data = new JSONObject().put("resourceType","Observation").put("status","final").put("code", code)
                            .put("subject", subject).put("valueQuantity", valueQuantity).put("effectiveDateTime",dateString);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                try {
                    URL url = new URL("http://"+ domain+":8080/hapi-fhir-jpaserver-example/baseDstu2/Observation");
                    HttpURLConnection conn = null;
                    conn = (HttpURLConnection) url.openConnection();

                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Authorization","Bearer "+g.getToken());
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.connect();

                    String input = data.toString();
                    Log.d("PUT DataPoint:",input);

                    OutputStream os=null;
                    os = conn.getOutputStream();
                    os.write(input.getBytes());
                    os.flush();

                    if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                        System.out.println("Error:"+conn.getResponseCode());
                        return false;
                    }
                    else{
                        System.out.println("created with Success");
                    }

                    conn.disconnect();
                } catch (ProtocolException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }

        }

    }
}

