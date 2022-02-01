package com.example.socket;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
//import java.util.HashMap;
//import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private ServerSocket serverSocket;
    private Socket tempClientSocket;
    Thread serverThread = null;
    public static final int SERVER_PORT = 3003;
    private LinearLayout msgList;
    private Handler handler;
    private int greenColor;
    private String respondData = "";
    private String readResult ="";
    private String number;
    private int countResponceSMS;
    private String responceMsg ="";
    private String message;

    String SENT = "SMS_SENT";
    String DELIVERED = "SMS_DELIVERED";
    PendingIntent sentPI, deliveredPI;
    BroadcastReceiver smsSentReceiver, smsDeliveredReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("SMS Socket");
        greenColor = ContextCompat.getColor(this, R.color.teal_700);
        handler = new Handler();
        msgList = findViewById(R.id.msgList);

        sentPI = PendingIntent.getBroadcast(this,0, new Intent(SENT),0);
        deliveredPI = PendingIntent.getBroadcast(this,0,new Intent(DELIVERED),0);

    }
    public TextView textView(String message, int color) {

        if (null == message || message.trim().isEmpty()) {
            message = "<Empty Message>";
        }
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setText(message + " [" + getTime() +"]");
        tv.setTextSize(20);
        tv.setPadding(0, 5, 0, 0);
        return tv;
    }
    public void showMessage(final String message, final int color) {
        handler.post(new Runnable() {
            @Override
            public void run() { msgList.addView(textView(message, color));
            }
        });
    }
    public void onClick(View view) {

        if (view.getId() == R.id.start_server) {
            msgList.removeAllViews();
            showMessage("Server Started.", Color.BLACK);
            this.serverThread = new Thread(new ServerThread());
            this.serverThread.start();
            return;
        }
        if (view.getId() == R.id.stop_server) {
            killServer();
        }
    }
    private  void killServer(){
        try {
            tempClientSocket.close();
            showMessage("Server Stoped : ", Color.RED);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private void sendMessage(final String message) {
        try {
            if (null != tempClientSocket) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        PrintWriter out = null;
                        try {
                            out = new PrintWriter(new BufferedWriter(
                                    new OutputStreamWriter(tempClientSocket.getOutputStream())),
                                    true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        out.println(message);
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    class ServerThread implements Runnable {

        public void run() {
            Socket socket;
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
//                findViewById(R.id.start_server).setVisibility(View.GONE);
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Starting Server : " + e.getMessage(), Color.RED);
            }
            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket = serverSocket.accept();
                        CommunicationThread commThread = new CommunicationThread(socket);
                        new Thread(commThread).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                        showMessage("Error Communicating to Client :" + e.getMessage(), Color.RED);
                    }
                }
            }
        }
    }

    class CommunicationThread implements Runnable {

        public Socket clientSocket;

        private BufferedReader input;

        public CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            tempClientSocket = clientSocket;
            try {
                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));


            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Connecting to Client!!", Color.RED);
            }
            showMessage("Connected to Client!!", greenColor);

        }

        public void run() {
            readResult = "";
            while (!Thread.currentThread().isInterrupted()) {
                try {
                   String read = input.readLine();

                    if(read.contains("message") || read.contains("phone") )
                    {
                        readResult += read;
                        Thread.interrupted();
                        read = "Client Disconnected";
                        showMessage("Client : " + read, Color.RED);
                        break;
                    }
                    tempClientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            filterSMSData();

        }

    }
    private void filterSMSData(){
        try {
            JSONArray jsonArray = new JSONArray(readResult);
            countResponceSMS = jsonArray.length();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject SMSObs = new JSONObject(jsonArray.getString(i));
                number = SMSObs.getString("phone");
                message = SMSObs.getString("message");
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    if(checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED){
                        showMessage("PERMISSION_GRANTED", Color.BLACK);
                        sendSMS();
                    }else
                    {
                        showMessage("Permission for sending sms are not enabled", Color.RED);
                        requestPermissions(new String[] {Manifest.permission.SEND_SMS},1);
                        break;
                    }
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != serverThread) {
            sendMessage("Disconnect");
            serverThread.interrupt();
            serverThread = null;
        }
    }
    @Override
    protected void onResume(){
        super.onResume();

        smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        respondData = "SMS sent";
                        showMessage("SMS sent",greenColor);
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        respondData = "Failed to send SMS >> RESULT_ERROR_GENERIC_FAILURE";
                        showMessage("RESULT_ERROR_GENERIC_FAILURE",Color.RED);
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        respondData = "Failed to send SMS >> RESULT_ERROR_NO_SERVICE";
                        showMessage("RESULT_ERROR_NO_SERVICE",Color.RED);
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        respondData = "Failed to send SMS >> RESULT_ERROR_NULL_PDU";
                        showMessage("RESULT_ERROR_NULL_PDU",Color.RED);
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        respondData = "Failed to send SMS >> RESULT_ERROR_RADIO_OFF";
                        showMessage("RESULT_ERROR_RADIO_OFF",Color.RED);
                        break;
                }
                filterResponceData();
            }
        };

        smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        showMessage("SMS delivered",greenColor);
                        break;
                    case Activity.RESULT_CANCELED:
                        showMessage("RESULT_CANCELED",greenColor);
                        break;
                }
            }
        };
        registerReceiver(smsSentReceiver, new IntentFilter(SENT));
        registerReceiver(smsDeliveredReceiver, new IntentFilter(DELIVERED));

    }
    private void filterResponceData(){
        try {
            JSONArray jsonArray = new JSONArray(readResult);
            if(jsonArray.length() == countResponceSMS)
                responceMsg += "['"+respondData;
            else
                responceMsg += "','"+respondData;

            if (countResponceSMS == 1){
                responceMsg += "']";

                JSONArray ArryMsg = new JSONArray(responceMsg);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject SMSObs = new JSONObject(jsonArray.getString(i));
                    String id = SMSObs.getString("message_id");
                    postResponce(ArryMsg.getString(i),id);
                }
            }else
            {
                countResponceSMS --;
            }



        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
    private  void postResponce(String status, String id){

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="http://192.168.11.10:8186/message/"+id+"/"+status;

// Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.PUT, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                       showMessage("Successfully posted in the server id= "+id+" status="+status, Color.BLUE);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                showMessage("Posting status in the server didn't work! id= "+id+" status="+status, Color.RED);
            }
        })
        {
            protected Map<String, String> getParams(){
                Map<String, String> paramVal = new HashMap<>();
                paramVal.put("status",status);
                return  paramVal;
            }
        };

        queue.add(stringRequest);

    }
    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(smsDeliveredReceiver);
        unregisterReceiver(smsSentReceiver);

    }

    private void sendSMS(){
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null,message,sentPI,deliveredPI);
            showMessage("[ Trying to send : " + message+", to ( "+ number+" ) ]", greenColor);
        } catch (Exception e){
            e.printStackTrace();
            showMessage("[ Fail to sent : " + message+", to "+ number+" ]", greenColor);
        }
    }
}