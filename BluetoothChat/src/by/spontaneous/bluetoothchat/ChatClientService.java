package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

public class ChatClientService extends Service implements IChatClient
{
	public class LocalBinder extends Binder
	{
		ChatClientService getService()
		{
			return ChatClientService.this;
		}
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	/** Messenger �������������� � GUI. */
	private Messenger messenger;

	/** Socket ����� � ��������. */
	private BluetoothSocket socket;

	/** Thread ����������� �������� ����� ��������� �� �������. */
	private ConnectedThread connectedThread;

	/** ������� ������� BluetoothDevice-������ (null ��� ������ �������). */
	private BluetoothDevice masterDevice;

	@Override
	public void onCreate()
	{
		super.onCreate();

		Toast.makeText(getBaseContext(), "ChatClientService onCreate()", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		disconnect();

		Toast.makeText(getBaseContext(), "ChatClientService onDestroy()", Toast.LENGTH_LONG).show();
	}

	// TODO: �� ���� ��� ������ ���� ����� ���������� � ���������...
	/** ����� Thread'� ChatClientService � ��������� �����������. */
	public void disconnect()
	{
		if (connectedThread != null)
		{
			connectedThread.cancel();
			connectedThread = null;

			try
			{
				if (socket != null)
				{
					socket.close();
				}
			}
			catch (IOException e)
			{
				Toast.makeText(getBaseContext(), "������ ChatClientService: " + e.getMessage(), Toast.LENGTH_LONG)
						.show();
			}

			messenger = null;
		}
	}

	public void setMasterDevice(BluetoothDevice mDevice)
	{
		masterDevice = mDevice; 
	}
	
	/**
	 * ����� ������ �������, ������������ ��������� �� �������, � �������� ��
	 * ���������.
	 */
	private class ConnectedThread extends Thread
	{
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket)
		{
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try
			{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			}
			catch (IOException e)
			{
				obtainToast("ChatClientService: ������ BluetoothSocket.get...Stream: " + e.getMessage());
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run()
		{
			

			// buffer[0] = (byte) MessageCode.MESSAGE_READ.getId();
			// write(buffer);

			// Keep listening to the InputStream until an exception occurs
			while (true)
			{
				try
				{
					byte[] buffer = new byte[256]; // buffer store for the stream
					int bytes; // bytes returned from read()
					
					bytes = mmInStream.read(buffer);

					if (bytes == 65356)
					{
						obtainToast("Client - ConnectedThread: bytes == 65356");
						break;
					}

					// Send the obtained bytes to the UI activity
					// mHandler.obtainMessage( (int) buffer[0], bytes, -1,
					// buffer).sendToTarget();
					obtainMessage(buffer);
					
					// ��������� ������ ����� ������������� ����� �������
					// ��������� �� ��������
				}
				catch (IOException e)
				{
					obtainToast("ChatClientService: ������ while (true){...} " + e.getMessage());
					break;
				}
			}

			obtainToast("Client - ConnectedThread: run() ��������!");
		}

		/* Call this from the main activity to send data to the remote device */
		public void write(byte[] bytes)
		{
			try
			{
				mmOutStream.write(bytes);
			}
			catch (IOException e)
			{
				obtainToast("Client - ConnectedThread: " + e.getMessage());
			}
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel()
		{
			try
			{
				mmSocket.close();
				obtainToast("Client - ConnectedThread: ������� ������!");
			}
			catch (IOException e)
			{
				obtainToast("Client - ConnectedThread: ������ ��������! " + e.getMessage());
			}
		}
	}

	/** ������������ ������� �� ����� Toast � Thread'� UI. */
	private void obtainToast(String str)
	{
		try
		{
			Message msg = Message.obtain(null, 0, 0, 0);
			msg.obj = str;
			messenger.send(msg);
		}
		catch (RemoteException e)
		{
		}
	}

	/** ������������ ������� �� ����� Message � Thread'� UI. */
	private void obtainMessage(byte[] buffer)
	{
		try
		{
			Message msg = Message.obtain(null, 1, 0, 0);
			msg.obj = buffer;
			messenger.send(msg);
		}
		catch (RemoteException e)
		{
		}
	}

	// Client-���������� IChatClient ��� ChatActivity
	/**
	 * Client-���������� ������ �� ������� ���������� IChatClient, �����������
	 * �� �������� ������ ����� � �����������, ���������� � ������ Server.
	 * ������� false ��������� ChatActivity ��� ������� ����������� �
	 * ChatClientService.
	 */
	@Override
	public boolean connectToServer(Messenger selectedMessenger)
	{
		disconnect();

		if (selectedMessenger == null)
		{
			Toast.makeText(getBaseContext(), "������ ChatClientService: provider == null", Toast.LENGTH_LONG).show();
			return false;
		}

		try
		{
			// MY_UUID is the app's UUID string, also used by the server
			// code

			UUID myid = UUID.fromString(getResources().getString(R.string.service_uuid));

			/*
			 * ParcelUuid[] uuids = device.getUuids();
			 * 
			 * boolean is_contained = false;
			 * 
			 * for (ParcelUuid uuid : uuids) {
			 * //Toast.makeText(getBaseContext(), uuid.getUuid().toString(),
			 * Toast.LENGTH_LONG).show(); if (uuid.getUuid() == myid) {
			 * is_contained = true; break; } }
			 * 
			 * if (is_contained == false) { Toast.makeText(getBaseContext(),
			 * "SDP-������ �� ������!", Toast.LENGTH_LONG).show(); return false;
			 * }
			 */
			socket = masterDevice.createRfcommSocketToServiceRecord(myid);

			socket.connect();
		}
		catch (IOException e)
		{
			// �������� ������ �����: ������� ����������� � ����������, ��
			// ������� �� ������ ������ ������ Service Discovery Protocol (SDP)
			Toast.makeText(getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			// disconnect();
			return false;
		}

		// TODO: ����� ������ ���� ��� ��?
		// ...������������ ������ �� ������ ����...
		messenger = selectedMessenger;

		connectedThread = new ConnectedThread(socket);
		connectedThread.start();

		return true;
	}

	@Override
	public void sendMessage(String str)
	{
		OutputStream outStream = null;

		try
		{
			outStream = socket.getOutputStream();
			outStream.write(str.getBytes());
		}
		catch (IOException e)
		{
			Toast.makeText(getBaseContext(), "ChatClientService: ������ sendMessage(String str):" + e.getMessage(),
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void close()
	{
		disconnect();
	}
}