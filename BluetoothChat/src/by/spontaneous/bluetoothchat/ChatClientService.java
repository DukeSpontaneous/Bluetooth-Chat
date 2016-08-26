package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
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
	 * ���� ���� ����� ������ ������ ��� �������������� � ChatSercverService,
	 * ��� broadcast ������������ ������������.
	 */
	private void unicasting(byte[] bytes)
	{
		// TODO: ������ ��� ��������� �� ������� ������������� ��������
		
		OutputStream outStream = null;

		try
		{
			outStream = socket.getOutputStream();
			outStream.write(bytes);
		}
		catch (IOException e)
		{
			// ��� ������� ������ ����� � ��������.
			Toast.makeText(getBaseContext(), "Stream.write IOException:" + e.getMessage(),
					Toast.LENGTH_LONG).show();
			transferResponseToUIThread(null, MessageCode.QUIT);
		}
	}

	/**
	 * ����� ������ �������, ������������ ��������� �� �������, � �������� ��
	 * ���������.
	 */
	private class ConnectedThread extends Thread
	{
		private final BluetoothSocket tSocket;
		private final InputStream tInStream;
		private final OutputStream tOutStream;

		public ConnectedThread(BluetoothSocket socket)
		{
			tSocket = socket;
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
				transferToast("ChatClientService: ������ BluetoothSocket.get...Stream: " + e.getMessage());
			}

			tInStream = tmpIn;
			tOutStream = tmpOut;
		}

		public void run()
		{
			// buffer[0] = (byte) MessageCode.MESSAGE_READ.getId();
			// write(buffer);

			// Keep listening to the InputStream until an exception occurs
			while (true)
			{
				byte[] buffer = new byte[256]; // buffer store for the stream
				int bytes; // bytes returned from read()

				try
				{
					// �������� bytes == 65356 ��� ��� ���������� ��������
					// ���������� ����� �� ������� ������ o_0, ���� ���� ������
					// ������
					bytes = tInStream.read(buffer);

					// TODO: ��������� ���������� ��������
					String msg = new String(Arrays.copyOfRange(buffer, 1, bytes - 1));
					transferResponseToUIThread(msg, MessageCode.fromId(buffer[0]));
				}
				catch (IOException e)
				{
					transferToast("ClientService: ConnectedThread IOException Stream.read():" + e.getMessage());
					transferResponseToUIThread(null, MessageCode.QUIT);
					return;
				}
			}
		}

		/* Call this from the main activity to send data to the remote device */
		public void write(byte[] bytes)
		{
			try
			{
				tOutStream.write(bytes);
			}
			catch (IOException e)
			{
				transferToast("Client - ConnectedThread: " + e.getMessage());
			}
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel()
		{
			try
			{
				tSocket.close();
				transferToast("Client - ConnectedThread: ������� ������!");
			}
			catch (IOException e)
			{
				transferToast("Client - ConnectedThread: ������ ��������! " + e.getMessage());
			}
		}
	}

	/** �������� ������� ����������� Messenger'� � Thread'� UI. */
	private void transferResponseToUIThread(String str, MessageCode code)
	{
		try
		{
			Message msg = Message.obtain(null, code.getId(), 0, 0);
			msg.obj = str;
			messenger.send(msg);
		}
		catch (RemoteException e)
		{
		}
	}

	/** ������������ ������� �� ����� Toast � Thread'� UI. */
	private void transferToast(String str)
	{
		transferResponseToUIThread(str, MessageCode.TOAST);
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

		if (selectedMessenger != null)
		{
			messenger = selectedMessenger;

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
				 * "SDP-������ �� ������!", Toast.LENGTH_LONG).show(); return
				 * false; }
				 */
				socket = masterDevice.createRfcommSocketToServiceRecord(myid);

				socket.connect();
			}
			catch (IOException e)
			{
				// �������� ������ �����: ������� ����������� � ����������, ��
				// ������� �� ������ ������ ������ Service Discovery Protocol
				// (SDP)
				Toast.makeText(getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				// disconnect();
				return false;
			}

			connectedThread = new ConnectedThread(socket);
			connectedThread.setDaemon(true);
			connectedThread.start();

			return true;
		}
		else
		{
			Toast.makeText(getBaseContext(), "������ ChatClientService: provider == null", Toast.LENGTH_LONG).show();
			return false;
		}
	}

	@Override
	public void sendResponse(byte[] resp)
	{
		unicasting(resp);
	}

	@Override
	public void close()
	{
		disconnect();
	}
}