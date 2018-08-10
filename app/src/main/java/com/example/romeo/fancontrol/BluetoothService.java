package com.example.romeo.fancontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.provider.SyncStateContract;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;


@SuppressLint("NewApi")
public class BluetoothService {
    private static final boolean f1D = true;

    private static final UUID MY_UUID_SECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String NAME_INSECURE = "BluetoothChatInsecure";
    private static final String NAME_SECURE = "BluetoothChatSecure";
    public static final int STATE_CONNECTED = 3;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_NONE = 0;
    private static final String TAG = "BluetoothChatService";
    private BluetoothAdapter mAdapter;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private Handler mHandler;
    private AcceptThread mInsecureAcceptThread;
    private AcceptThread mSecureAcceptThread;
    private int mState = 0;
    private int mNewState;


    public BluetoothService(Context context, Handler handler) {
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mState = STATE_NONE;
        this.mNewState = mState;
        this.mHandler = handler;
    }

    private synchronized void updateUserInterfaceTitle() {
        mState = getState();
        Log.d(TAG, "updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        mNewState = mState;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(1, mNewState, -1).sendToTarget();
    }

    private class AcceptThread extends  Thread{
        private BluetoothServerSocket mmServerSocket;
        private String mSocketType;
        boolean isRunning = true;


        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            this.mSocketType = secure ? "Secure" : "Insecure";

            try {
                if (secure) {
                    tmp = BluetoothService.this.mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
                }
                else {
                    tmp = BluetoothService.this.mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
                }
            }catch (IOException e){
                Log.e(TAG, "AcceptThread: "+e.getMessage());

            }

            Log.i(TAG, "AcceptThread: "+(tmp == null));
            this.mmServerSocket = tmp;
            mState = STATE_LISTEN;
        }

        public void run() {
            Log.d(BluetoothService.TAG, "Socket Type: " + this.mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            while (mState != STATE_CONNECTED && mmServerSocket != null && isRunning) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e2) {
                    Log.e(BluetoothService.TAG, "Socket Type: " + this.mSocketType + "accept() failed", e2);
                    break;
                }
            }

            if (socket != null) {
                synchronized (BluetoothService.this) {
                    switch (mState) {
                        case 0:
                        case STATE_CONNECTED:
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(BluetoothService.TAG, "Could not close unwanted socket", e);
                            }
                            break;
                        case 1:
                        case STATE_CONNECTING:
                            connected(socket, socket.getRemoteDevice(), mSocketType);
                            break;
                    }
                }
            }
            Log.i(BluetoothService.TAG, "END mAcceptThread, socket Type: " + this.mSocketType);
        }

        public void cancel() {
            try {
                mmServerSocket.close();
                mmServerSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "cancel: "+e.getMessage() );
            }
        }

        public void kill() {
            isRunning = false;
        }

    }

    private class ConnectThread extends Thread {
        // edit bluetoothsoc
        private BluetoothSocket mmSocket = null;
        private BluetoothDevice mmDevice = null;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            this.mmDevice = device;
            BluetoothSocket tmp = null;
            this.mSocketType = secure ? "Secure" : "Insecure";
                try {
                    if (secure) {
                        tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                    }
                    else {
                        tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                    }

                    try {
                        tmp.connect();
                    }catch (IOException ie){

                        try {
                            tmp = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket",new Class[]{int.class}).invoke(device,1);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        }


                    }

                } catch (IOException e) {
                    Log.e(BluetoothService.TAG, " err Socket Type: " + this.mSocketType + "create() failed", e);
                }


                mmSocket = tmp;
                mState = STATE_CONNECTING;
        }

        public void run() {
            Log.i(BluetoothService.TAG, "BEGIN mConnectThread SocketType:" + this.mSocketType);
            setName("ConnectThread" + this.mSocketType);
            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException e) {
                Log.d(TAG, "run here: "+e.getMessage());
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(BluetoothService.TAG, "unable to close() " + this.mSocketType + " socket during connection failure", e2);
                }

                connectionFailed();
                return;
            }

            synchronized (BluetoothService.this) {
               mConnectThread = null;
            }

            connected(mmSocket,mmDevice,mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "close() of connect " + this.mSocketType + " socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothSocket mmSocket;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(BluetoothService.TAG, "create ConnectedThread: " + socketType);
            this.mmSocket = socket;
            if(!socket.isConnected()) {
                try {
                    socket.connect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            InputStream tmpIn = null;
            OutputStream tmpOut = null;


            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "temp sockets not created", e);
            }


            this.mmInStream = tmpIn;
            this.mmOutStream = tmpOut;
            mState = STATE_CONNECTED;
        }


        public void run() {
            Log.i(BluetoothService.TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            while (mState == STATE_CONNECTED) {
                try {
                    bytes = mmInStream.read(buffer);
                    mHandler.obtainMessage(2 ,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(BluetoothService.TAG, "disconnected", e);
                    connectionLost();
                    return;
                }
            }
        }


        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                BluetoothService.this.mHandler.obtainMessage(3, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                this.mmSocket.close();
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "close() of connect socket failed", e);
            }
        }
    }


    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + this.mState + " -> " + state);
        this.mState = state;
        this.mHandler.obtainMessage(1, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return this.mState;
    }

    public synchronized void start() {
        Log.d(TAG, "start");
        if (this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }
        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }
        setState(this.STATE_LISTEN);


        if (this.mSecureAcceptThread == null) {
            this.mSecureAcceptThread = new AcceptThread(true);
            this.mSecureAcceptThread.start();
        }

        if (this.mInsecureAcceptThread == null) {
            this.mInsecureAcceptThread = new AcceptThread(false);
            this.mInsecureAcceptThread.start();
        }


    }


    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);
        if (this.mState == STATE_CONNECTING && this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }

        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }

        this.mConnectThread = new ConnectThread(device, secure);
        this.mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);
        if (this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }
        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }
        if (this.mSecureAcceptThread != null) {
            this.mSecureAcceptThread.cancel();
            this.mSecureAcceptThread = null;
        }
        if (this.mInsecureAcceptThread != null) {
            this.mInsecureAcceptThread.cancel();
            this.mInsecureAcceptThread = null;
        }
        this.mConnectedThread = new ConnectedThread(socket, socketType);
        this.mConnectedThread.start();
        Message msg = this.mHandler.obtainMessage(4);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
        setState(3);
    }

    public synchronized void stop() {
        Log.d(TAG, "stop");
        if (this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }
        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }
        if (this.mSecureAcceptThread != null) {
            this.mSecureAcceptThread.cancel();
            this.mSecureAcceptThread.kill();
            this.mSecureAcceptThread = null;
        }
        if (this.mInsecureAcceptThread != null) {
            this.mInsecureAcceptThread.cancel();
            this.mInsecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    public void write(byte[] out) {
        synchronized (this) {
            if (this.mState != 3) {
                return;
            }
            ConnectedThread r = this.mConnectedThread;
            r.write(out);
        }
    }

    private void connectionFailed() {
        Message msg = this.mHandler.obtainMessage(5);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);

        mState = STATE_NONE;

        updateUserInterfaceTitle();
        BluetoothService.this.start();
    }


    private void connectionLost() {
        Message msg = this.mHandler.obtainMessage(5);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;

        updateUserInterfaceTitle();
        BluetoothService.this.start();
    }


}
