package pt.ua.ieeta.mhealthintegration;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
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
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;


public class heartRateActivity extends Activity {

    private LinearLayout chartLyt;
    private GraphicalView chartView;
    private int number = 0;
    XYSeries series = new XYSeries("Heart Rate");
    StringBuffer response = new StringBuffer();
    Globals g = Globals.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate);
        fillChart();
    }

    public void fillChart() {


        try {
            if (new getHeartRatePoints().execute().get()) {
                int [] points;
                if(g.getEnv().equals("omh")) {
                    points = checkOutPoints();
                } else if (g.getEnv().equals("fhir")){
                    points = checkOutPointsFhir();
                } else {
                    points = checkOutPointsGoogleFit();
                }
                Log.d("Points to Fill", Arrays.toString(points));

                chartLyt = (LinearLayout) findViewById(R.id.chart);

                series.clear();
                // fill series with data

                for (int x = 0; x < number; x++) {
                    series.add(x, points[x]);
                }

                XYSeriesRenderer renderer = new XYSeriesRenderer();
                renderer.setLineWidth(0.2f);
                renderer.setColor(Color.RED);
                renderer.setDisplayBoundingPoints(true);
                renderer.setPointStyle(PointStyle.CIRCLE);
                renderer.setPointStrokeWidth(0.5f);
                XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
                dataset.addSeries(series);

                XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
                mRenderer.addSeriesRenderer(renderer);

                mRenderer.setMarginsColor(Color.argb(0x00, 0xff, 0x00, 0x00)); // transparent margins

                mRenderer.setPanEnabled(false, false);
                mRenderer.setYAxisMax(250);
                mRenderer.setYAxisMin(0);
                mRenderer.setShowGrid(true); // we show the grid

                chartView= ChartFactory.getLineChartView(getApplicationContext(), dataset, mRenderer);
                chartLyt.removeAllViews();
                chartLyt.addView(chartView, 0);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }


    }

    private class getHeartRatePoints extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {

            Log.d("ON AsyncTask","IN");
            JSONObject json=new JSONObject();
            JSONArray values = new JSONArray();

            String domain = getResources().getString(R.string.server_ip);
            StringBuilder received = new StringBuilder();


            if (g.getEnv().equals("omh")) {
                try {
                    String url = "http://" + domain + ":8083/v1.0.M1/dataPoints?schema_namespace=omh&schema_name=heart-rate&schema_version=1.0";

                    URL obj = new URL(url);
                    HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                    // optional default is GET
                    con.setRequestMethod("GET");

                    //add request header
                    con.setDoOutput(false);
                    con.setRequestProperty("Accept", "application/json");
                    con.setRequestProperty("Authorization", "Bearer " + g.getToken());
                    con.setRequestProperty("Content-Type", "application/json");

                    System.out.println(con.getHeaderFields().toString());
                    int responseCode = con.getResponseCode();
                    System.out.println("\nSending 'GET' request to URL : " + url);
                    System.out.println("Response Code : " + responseCode);

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(con.getInputStream()));
                    String inputLine;


                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
            else if (g.getEnv().equals("fhir")){
                try {
                    String url = "http://" + domain + ":8080/hapi-fhir-jpaserver-example/baseDstu2/Observation?code=8867-4&subject=Patient/" + g.getUsername()+"&_count=50";

                    URL obj = new URL(url);
                    HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                    // optional default is GET
                    con.setRequestMethod("GET");

                    //add request header
                    con.setDoOutput(false);
                    con.setRequestProperty("Accept", "application/json");
                    con.setRequestProperty("Authorization", "Bearer " + g.getToken());
                    con.setRequestProperty("Content-Type", "application/json");

                    System.out.println(con.getHeaderFields().toString());
                    int responseCode = con.getResponseCode();
                    System.out.println("\nSending 'GET' request to URL : " + url);
                    System.out.println("Response Code : " + responseCode);

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(con.getInputStream()));
                    String inputLine;


                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            } else {

                String dataStreamId = null;
                try {
                    JSONArray dsList = getDSList();
                    if (dsList != null) {
                        for (int i = 0; i < dsList.length(); i++) {
                            JSONObject ds = dsList.getJSONObject(i);
                            if (ds.getString("dataStreamName").equals("MyDataSource8")) {
                                dataStreamId = ds.getString("dataStreamId");
                            }
                        }
                    }


                String url = "https://www.googleapis.com/fitness/v1/users/me/dataSources/" + dataStreamId + "/datasets/0000000000000000000-" + getTimeInNano();
                URL obj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                // optional default is GET
                con.setRequestMethod("GET");

                //add request header
                con.setDoOutput(false);
                con.setRequestProperty("Accept", "application/json");
                con.setRequestProperty("Authorization", "Bearer " + g.getToken());
                con.setRequestProperty("Content-Type", "application/json");

                System.out.println(con.getHeaderFields().toString());
                int responseCode = con.getResponseCode();
                System.out.println("\nSending 'GET' request to URL : " + url);
                System.out.println("Response Code : " + responseCode);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;


                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
            } catch (JSONException e) {
                e.printStackTrace();
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

    private JSONArray getDSList() {
        try {
            String url = "https://www.googleapis.com/fitness/v1/users/me/dataSources";
            StringBuffer response = new StringBuffer();
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            con.setDoOutput(false);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + g.getToken());

            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;


            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            if (responseCode == 200) {
                JSONObject dsObj = new JSONObject(response.toString());
                JSONArray dsList = dsObj.getJSONArray("dataSource");
                return dsList;
            } else return null;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getTimeInNano() {
        String timeMillis = "" + System.currentTimeMillis();
        String timeInNano = timeMillis.substring(0, timeMillis.length() - 3);
        return timeInNano + "000000000";
    }

    public int [] checkOutPoints(){


        try {
            System.out.println("received:" + response.toString());
            JSONArray fromServer = new JSONArray(response.toString());

            if(fromServer.length() > 50)
                number = 25;
            else
                number = fromServer.length();

            System.out.println("tamanho:" + number);

            int [] points = new int [number];
            for (int i = 0; i<number; i++) {
                JSONObject dataPoint = fromServer.getJSONObject(i);
                points[i] = dataPoint.getJSONObject("body").getJSONObject("heart_rate").getInt("value");

            }
            return points;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public int [] checkOutPointsGoogleFit(){

        System.out.println("received:" + response.toString());
        try {
            JSONObject fromServer = new JSONObject(response.toString());
            JSONArray arrayPoints = fromServer.getJSONArray("point");


            if(arrayPoints.length() > 50)
                number = 25;
            else
                number = arrayPoints.length();

            System.out.println("tamanho:" + number);

            int [] points = new int [number];
            for (int i = 0; i<number; i++) {
                JSONObject dataPoint = arrayPoints.getJSONObject(i).getJSONArray("value").getJSONObject(0);
                points[i] = dataPoint.getInt("fpVal");

            }
            return points;

        } catch (JSONException e) {
            e.printStackTrace();
        }

        System.out.println("Missing parse points");
        return null;
    }

    public int [] checkOutPointsFhir(){


        try {
            System.out.println("received:" + response.toString());
            JSONObject fromServer = new JSONObject(response.toString());

            JSONArray entrys = fromServer.getJSONArray("entry");

            if(entrys.length() > 50)
                number = 25;
            else
                number = entrys.length();

            System.out.println("tamanho:" + number);

            int [] points = new int [number];
            for (int i = 0; i<number; i++) {
                JSONObject dataPoint = entrys.getJSONObject(i);
                points[i] = dataPoint.getJSONObject("resource").getJSONObject("valueQuantity").getInt("value");

            }
            return points;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void goBack(View v) {
        this.finish();
    }
}
