package com.example.romeo.fancontrol;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class SelectDevice extends AppCompatActivity implements View.OnClickListener{

    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    private static final String TAG = "DeviceListActivity";
    private Button btnScan;
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mDevicesArrayAdapter;

    private AdapterView.OnItemClickListener mClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> adapterView, View v, int arg2, long arg3) {
            SelectDevice.this.mBtAdapter.cancelDiscovery();
            String info = ((TextView) v).getText().toString();
            String address = "";
            if(info.length() > 17)
                address = info.substring(info.length() - 17);
            Intent intent = new Intent();
            intent.putExtra(SelectDevice.EXTRA_DEVICE_ADDRESS, address);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "onReceive: "+action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != 12) {
                    if (device.getName() == null || device.getName() == "")
                        SelectDevice.this.mDevicesArrayAdapter.add("Unknown" + " *\n" + device.getAddress());
                    else
                        SelectDevice.this.mDevicesArrayAdapter.add(device.getName() + " *\n" + device.getAddress());
                }
            } else if ("android.bluetooth.adapter.action.DISCOVERY_FINISHED".equals(action)) {
                if (SelectDevice.this.mDevicesArrayAdapter.getCount() == 0) {
                    SelectDevice.this.mDevicesArrayAdapter.add("No devices found");
                }
                btnScan.setText("Scan for devices");
                btnScan.setEnabled(true);
            }

            Parcelable[] uuidExtra =
                    intent.getParcelableArrayExtra("android.bluetooth.device.extra.UUID");

        }
    };

    @Override
    public void onClick(View view) {
        if (SelectDevice.this.mBtAdapter.isDiscovering()) {
            SelectDevice.this.mBtAdapter.cancelDiscovery();
            SelectDevice.this.btnScan.setText("Scan for devices");
            return;
        }
        doDiscovery();
        btnScan.setText("Scanning...");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_device);
//        setResult(0);

        this.btnScan = (Button) findViewById(R.id.btnScan);
        this.btnScan.setOnClickListener(this);
        this.mDevicesArrayAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1);
        this.mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        ListView pairedListView = (ListView) findViewById(R.id.lvDevice);

        if (mBtAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(getApplicationContext(),"Device doesn't support ",Toast.LENGTH_SHORT).show();
        }

        if (!mBtAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(),"bluetooth status off",Toast.LENGTH_SHORT).show();
        }

        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                this.mDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            this.mDevicesArrayAdapter.add("No devices have been paired");
        }
        pairedListView.setAdapter(this.mDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(this.mClickListener);

        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(mReceiver, new IntentFilter("android.bluetooth.device.action.UUID"));
        registerReceiver(mReceiver, new IntentFilter("android.bluetooth.adapter.action.DISCOVERY_FINISHED"));

    }

    protected void onDestroy() {
        super.onDestroy();
        if (this.mBtAdapter != null) {
            this.mBtAdapter.cancelDiscovery();
        }
        unregisterReceiver(mReceiver);
    }


    private void doDiscovery() {
        this.mDevicesArrayAdapter.clear();
        for (BluetoothDevice device : mBtAdapter.getBondedDevices()) {
            mDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
        }
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }
        mBtAdapter.startDiscovery();
    }


}
