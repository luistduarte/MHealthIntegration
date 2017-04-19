package pt.ua.ieeta.mhealthintegration;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.TokenResponse;

public class Login extends Activity {

    Globals g = Globals.getInstance();
    private Button login,cancel;
    private EditText username,password;
    private String usernameSrt,passwordStr;
    private static final String USED_INTENT = "USED_INTENT";
    public static final String LOG_TAG = "AppAuthSample";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
    }
    public void doLogin(View v) {

        username = (EditText)findViewById(R.id.username);
        password = (EditText)findViewById(R.id.password);

        String usernameStr = username.getText().toString();
        String passwordStr = password.getText().toString();
        Boolean test = false;
        try {
           test = new checkCredentialsDBTask().execute(usernameStr, passwordStr).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        if (test) {
            Toast toast = Toast.makeText(this.getApplicationContext(), "Logged In with " + usernameStr, Toast.LENGTH_LONG);
            toast.show();
            this.finish();
        }
        else {
            Toast toast = Toast.makeText(this.getApplicationContext(), "Try again, invalid credentials", Toast.LENGTH_LONG);
            toast.show();

        }

    }

    public void doLoginWithGoogle(View v) {

        AuthorizationServiceConfiguration serviceConfiguration = new AuthorizationServiceConfiguration(
                Uri.parse("https://accounts.google.com/o/oauth2/v2/auth") /* auth endpoint */,
                Uri.parse("https://www.googleapis.com/oauth2/v4/token") /* token endpoint */
        );

        String clientId = "115796305506-36qbrke6h2b0mo6u37frs02frj68jcil.apps.googleusercontent.com";
        Uri redirectUri = Uri.parse("pt.ua.ieeta.mhealthintegration:/oauth2callback");
        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(
                serviceConfiguration,
                clientId,
                AuthorizationRequest.RESPONSE_TYPE_CODE,
                redirectUri
        );
        builder.setScopes("profile");
        AuthorizationRequest request = builder.build();

        AuthorizationService authorizationService = new AuthorizationService(v.getContext());

        String action = "pt.ua.ieeta.mhealthintegration.HANDLE_AUTHORIZATION_RESPONSE";
        Intent postAuthorizationIntent = new Intent(action);
        PendingIntent pendingIntent = PendingIntent.getActivity(v.getContext(), request.hashCode(), postAuthorizationIntent, 0);
        authorizationService.performAuthorizationRequest(request, pendingIntent);

    }

    public void doCancel(View v) {
        this.finish();
    }
    private class checkCredentialsDBTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {

            Log.d("ON AsyncTask","IN"+params[0]);
            JSONObject json=new JSONObject();
            JSONArray values = new JSONArray();

            String domain = getResources().getString(R.string.server_ip);
            StringBuilder received = new StringBuilder();
            try {

                URL url = new URL("http://"+domain+":8082/oauth/token");
                Map<String,Object> requestParams = new LinkedHashMap<>();
                requestParams.put("grant_type", "password");
                requestParams.put("username", params[0]);
                requestParams.put("password", params[1]);
                //requestParams.put("username", "testUser");
                //requestParams.put("password", "testUserPassword");


                StringBuilder postData = new StringBuilder();
                for (Map.Entry<String,Object> param : requestParams.entrySet()) {
                    if (postData.length() != 0) postData.append('&');
                    postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                    postData.append('=');
                    postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
                }
                byte[] postDataBytes = postData.toString().getBytes("UTF-8");

                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept","application/json");
                conn.setRequestProperty("Authorization","Basic dGVzdENsaWVudDp0ZXN0Q2xpZW50U2VjcmV0");
                conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
                conn.setDoOutput(true);
                conn.getOutputStream().write(postDataBytes);

                Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));


                for (int c; (c = in.read()) >= 0;)
                    received.append(Character.toChars(c));

                conn.disconnect();

                Log.d("ON AsyncTask-AFTERLOGIN", received.toString());

                String accessToken = new JSONObject(received.toString()).get("access_token").toString();


                Log.d("ON AsyncTask - token:",accessToken);
                g.setData(accessToken);
                g.setUsername(params[0]);

                if (! accessToken.equals(""))return true;
                else return false;
            } catch (JSONException e) {
                Toast toast = Toast.makeText(getApplicationContext(), "Wrong Credentials", Toast.LENGTH_LONG);
                toast.show();
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

    }

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radio_fhir:
                if (checked)
                    Log.d("ratioButton", "fhir");
                    g.setEnv("fhir");
                break;
            case R.id.radio_omh:
                if (checked)
                    Log.d("ratioButton", "omh");
                    g.setEnv("omh");
                break;
        }
    }
    @Override
    protected void onNewIntent(Intent intent) {
        checkIntent(intent);
    }
    private void checkIntent(@Nullable Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case "pt.ua.ieeta.mhealthintegration.HANDLE_AUTHORIZATION_RESPONSE":
                        if (!intent.hasExtra(USED_INTENT)) {
                            handleAuthorizationResponse(intent);
                            intent.putExtra(USED_INTENT, true);
                        }
                        break;
                    default:
                        // do nothing
                }
            }

        }
    }

    private void handleAuthorizationResponse(@NonNull Intent intent) {
        AuthorizationResponse response = AuthorizationResponse.fromIntent(intent);
        AuthorizationException error = AuthorizationException.fromIntent(intent);
        final AuthState authState = new AuthState(response, error);
        if (response != null) {
            Log.i(LOG_TAG, String.format("Handled Authorization Response %s ", authState.toJsonString()));
            AuthorizationService service = new AuthorizationService(this);
            service.performTokenRequest(response.createTokenExchangeRequest(), new AuthorizationService.TokenResponseCallback() {
                @Override
                public void onTokenRequestCompleted(@Nullable TokenResponse tokenResponse, @Nullable AuthorizationException exception) {
                    if (exception != null) {
                        Log.w(LOG_TAG, "Token Exchange failed", exception);
                    } else {
                        if (tokenResponse != null) {
                            authState.update(tokenResponse, exception);
                            Log.i(LOG_TAG, String.format("Token Response [ Access Token: %s, ID Token: %s , TokenResponse: %s ]", tokenResponse.accessToken, tokenResponse.idToken, tokenResponse.toJsonString()));
                            g.setData(tokenResponse.accessToken);


                        }
                    }
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkIntent(getIntent());
    }

    private class checkCredentialsDBTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {


        }

    }
}
