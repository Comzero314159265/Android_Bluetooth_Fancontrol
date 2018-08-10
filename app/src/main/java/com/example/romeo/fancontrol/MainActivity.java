package com.example.romeo.fancontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {

    public static final String DEVICE_NAME = "device_name";
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_WRITE = 3;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final String TAG = "BluetoothSimple";
    public static final String TOAST = "toast";

    private StringBuilder sb = new StringBuilder();
    boolean isConnected = false;

    BluetoothAdapter mBluetoothAdapter = null;
    BluetoothService mChatService = null;
    String mConnectedDeviceName = null;
    String sbprint2;
    ArrayList<String> arr_list;

    ListView listDataIncoming;

    Button btn1;
    Button btn2;
    Button btn3;
    Button btnStop;
    Button btnConnect;
    Button btnAuto;
    Button btnManual;

    TextView txtMode;
    TextView txtStatus;
    TextView txtTemp;
    TextView txtWind;

    void setStatus(String msg){
        txtStatus.setText(msg);
    }

    @SuppressLint("HandlerLeak")
    public final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Log.i(MainActivity.TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case 0:
                        case 1:
                            MainActivity.this.setStatus("ไม่ได้เชื่อมต่อ บลูทูธ");
                            MainActivity.this.btnConnect.setText("Connect");
                            return;
                        case 2:
                            MainActivity.this.setStatus("กำลังเชื่อมต่อ...");
                            MainActivity.this.isConnected = false;
                            return;
                        case 3:
                            MainActivity.this.setStatus("เชื่อมต่อกับ " + MainActivity.this.mConnectedDeviceName);
                            MainActivity.this.isConnected = true;
                            MainActivity.this.btnConnect.setText("DisConnect");
                            return;
                        default:
                            return;
                    }
                case 2:
                    String readMessage = new String((byte[])msg.obj, 0, msg.arg1);
                    txtTemp = (TextView) findViewById(R.id.txtTemp);
                    txtWind = (TextView) findViewById(R.id.txtWind);
                    MainActivity.this.sb.append(readMessage);
                    if (MainActivity.this.sb.indexOf("\r\n") > 0) {
                        String sbprint = MainActivity.this.sb.substring(MainActivity.this.sb.lastIndexOf("T") + 1, MainActivity.this.sb.lastIndexOf("/"));
                        String sbprint1 = MainActivity.this.sb.substring(MainActivity.this.sb.lastIndexOf("/") + 1);
                        MainActivity.this.sb.delete(0, MainActivity.this.sb.length());
                        MainActivity.this.txtTemp.setText(sbprint + " องศา");
                        MainActivity.this.txtWind.setText(sbprint1);
                        return;
                    }
                    return;
                case 3:
                    String writeMessage = new String((byte[])msg.obj);
                    return;
                case 4:
                    MainActivity.this.mConnectedDeviceName = msg.getData().getString(MainActivity.DEVICE_NAME);
                    Toast.makeText(MainActivity.this.getApplicationContext(), "Connected to " + MainActivity.this.mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    return;
                case 5:
                    Toast.makeText(MainActivity.this.getApplicationContext(), msg.getData().getString(MainActivity.TOAST), Toast.LENGTH_SHORT).show();
                    return;
                default:
                    return;
            }
        }
    };


    class Level1 implements OnClickListener {
        Level1() {
        }

        public void onClick(View v) {
            MainActivity.this.sendMessage("S1\n");
        }
    }

    class Level2 implements OnClickListener {
        Level2() {
        }

        public void onClick(View v) {
            MainActivity.this.sendMessage("S2\n");
        }
    }

    class Level3 implements OnClickListener {
        Level3() {
        }

        public void onClick(View v) {
            MainActivity.this.sendMessage("S3\n");
        }
    }

    class Stop implements OnClickListener {
        Stop() {
        }

        public void onClick(View v) {
            MainActivity.this.sendMessage("S0\n");
        }
    }

    class Manual implements OnClickListener {
        Manual() {
        }

        public void onClick(View v) {
//            Toast.makeText(MainActivity.this,"MANUAL",Toast.LENGTH_SHORT).show();
            txtMode.setText("MANUAL");
            MainActivity.this.sendMessage("M\n");
        }
    }

    class Auto implements OnClickListener {
        Auto() {
        }
        public void onClick(View v) {
//            Toast.makeText(MainActivity.this,"AUTO",Toast.LENGTH_SHORT).show();
            txtMode.setText("AUTO");
            MainActivity.this.sendMessage("A\n");
        }
    }

    class ConnectListenner implements OnClickListener {
        ConnectListenner() {
        }

        public void onClick(View v) {
            if (MainActivity.this.isConnected) {
                MainActivity.this.mChatService.stop();
                MainActivity.this.mChatService = new BluetoothService(MainActivity.this, MainActivity.this.mHandler);
                MainActivity.this.isConnected = false;
                return;
            }
            MainActivity.this.startActivityForResult(new Intent(MainActivity.this, SelectDevice.class), 1);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (this.mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public void onStart() {
        super.onStart();
        if (!this.mBluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE"), 3);
        } else if (this.mChatService == null) {
            setupChat();
        }
    }

    public synchronized void onResume() {
        super.onResume();
        if (this.mChatService != null && this.mChatService.getState() == 0) {
            this.mChatService.start();
        }
    }


    public void setupChat() {
        this.txtStatus = (TextView) findViewById(R.id.tvStatus);
        this.arr_list = new ArrayList();
        this.btn1 = (Button) findViewById(R.id.btn1);
        this.btn1.setOnClickListener(new Level1());
        this.btn2 = (Button) findViewById(R.id.btn2);
        this.btn2.setOnClickListener(new Level2());
        this.btn3 = (Button) findViewById(R.id.btn3);
        this.btn3.setOnClickListener(new Level3());
        this.btnStop = (Button) findViewById(R.id.btStop);
        this.btnStop.setOnClickListener(new Stop());
        this.btnManual = (Button) findViewById(R.id.btManual);
        this.btnManual.setOnClickListener(new Manual());
        this.btnAuto = (Button) findViewById(R.id.btAuto);
        this.btnAuto.setOnClickListener(new Auto());
        this.btnConnect = (Button) findViewById(R.id.btConnect);
        this.btnConnect.setOnClickListener(new ConnectListenner());
        this.mChatService = new BluetoothService(this, this.mHandler);
        this.txtMode = (TextView) findViewById(R.id.txtMode);
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.mChatService != null) {
            this.mChatService.stop();
        }
    }

    public void sendMessage(String message) {
        if (this.mChatService.getState() != 3) {
            Toast.makeText(this, "Device is not connected", Toast.LENGTH_SHORT).show();
        } else if (message.length() > 0) {
            this.mChatService.write(message.getBytes());
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 1:
                if (resultCode == -1) {
                    connectDevice(data, true);
                    return;
                }
                return;
            case 2:
                if (resultCode == -1) {
                    connectDevice(data, false);
                    return;
                }
                return;
            case 3:
                if (resultCode == -1) {
                    setupChat();
                    return;
                }
                Toast.makeText(this, "Bluetooth was not enabled. Leaving Bluetooth Chat", Toast.LENGTH_SHORT).show();
                finish();
                return;
            default:
                return;
        }
    }

    public void connectDevice(Intent data, boolean secure) {
        Log.i(TAG, "connectDevice: "+secure);
        String address = data.getExtras().getString(SelectDevice.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mChatService.connect(device,secure);
    }



}
