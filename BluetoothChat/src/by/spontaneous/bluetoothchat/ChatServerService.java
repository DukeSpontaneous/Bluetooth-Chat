package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

public class ChatServerService extends Service implements IChatClient
{
	public class LocalBinder extends Binder
	{
		ChatServerService getService()
		{
			return ChatServerService.this;
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

	/** Socket ����� ����� �����������. */
	private BluetoothServerSocket serverSocket;

	/** Thread ���������� �������� ����� ��������. */
	private AcceptThread acceptThread;

	/** ������ Socket'�� �������� �����������. */
	private ArrayList<BluetoothSocket> list = new ArrayList<BluetoothSocket>();

	/** ������ Thread'�� �������� �����������. */
	private ArrayList<ConnectedThread> connectedThreads = new ArrayList<ConnectedThread>();

	@Override
	public void onCreate()
	{
		super.onCreate();

		Toast.makeText(getBaseContext(), "ChatServerService onCreate()", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		destroyServer();

		Toast.makeText(getBaseContext(), "ChatServerService onDestroy()", Toast.LENGTH_LONG).show();
	}

	public void setMessenger(Messenger selectedMessenger)
	{
		messenger = selectedMessenger;
	}

	/** ����� ������������� ���� �����-���� ��������� ����������� �������. */
	public boolean createServer(BluetoothAdapter bAdapter)
	{
		this.destroyServer();

		try
		{
			// MY_UUID is the app's UUID string, also used by the client
			// code
			serverSocket = bAdapter.listenUsingRfcommWithServiceRecord(getResources().getString(R.string.service_name),
					UUID.fromString(getResources().getString(R.string.service_uuid)));
		}
		catch (IOException e)
		{
			Toast.makeText(getBaseContext(), "������ Server - createServer(): " + e.getMessage(), Toast.LENGTH_LONG)
					.show();
			return false;
		}

		acceptThread = new AcceptThread(serverSocket);
		acceptThread.start();

		return true;
	}

	/** ����� ����������� ���� �����-���� ��������� ����������� �������. */
	public void destroyServer()
	{
		if (serverSocket != null)
		{
			try
			{
				serverSocket.close();
				serverSocket = null;
			}
			catch (IOException e)
			{
				Toast.makeText(getBaseContext(), "������ ChatServerService serverSocket.close(): " + e.getMessage(),
						Toast.LENGTH_LONG).show();
			}
		}

		if (acceptThread != null)
		{
			acceptThread.cancel();
			acceptThread = null;

			Toast.makeText(getBaseContext(), "Server - AcceptThread: AcceptThread ������� ��������!", Toast.LENGTH_LONG)
					.show();
		}
	}

	private void manageConnectedSocket(BluetoothSocket socket)
	{
		ConnectedThread thread = new ConnectedThread(socket);

		synchronized (connectedThreads)
		{
			connectedThreads.add(thread);
			list.add(socket);
		}

		thread.start();
	}

	/** ����� ������ �������, ������������� ������ �������� �����������. */
	private class AcceptThread extends Thread
	{

		private final BluetoothServerSocket serverSocket;

		public AcceptThread(BluetoothServerSocket socket)
		{
			serverSocket = socket;
		}

		public void run()
		{
			BluetoothSocket socket = null;
			// Keep listening until exception occurs or a socket is returned
			while (true)
			{
				try
				{
					obtainToast("AcceptThread: serverSocket.accept() �����!");
					socket = serverSocket.accept();
					obtainToast("AcceptThread: serverSocket.accept() ������!");

				}
				catch (IOException e)
				{
					obtainToast("AcceptThread: ������ serverSocket.accept()" + e.getMessage());
					break;
				}
				// If a connection was accepted
				if (socket != null)
				{
					// Do work to manage the connection (in a separate thread)
					manageConnectedSocket(socket);

					obtainToast("AcceptThread: ����������� ���������!");
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel()
		{
			try
			{
				serverSocket.close();

				for (ConnectedThread thread : connectedThreads)
				{
					thread.cancel();
				}

				obtainToast("Server - AcceptThread: ��� ConnectedThread ������� ���������!");

				connectedThreads.clear();
				list.clear();
			}
			catch (IOException e)
			{
				obtainToast("Server - AcceptThread: ������ serverSocket.cancel()" + e.getMessage());
			}
		}

	}

	/**
	 * ����� �������� ��������� ��������, ����������� ��� ���� ��������, �����
	 * �����������.
	 */
	public void write(BluetoothSocket socket, byte[] bytes)
	{
		synchronized (connectedThreads)
		{
			for (BluetoothSocket sock : list)
			{
				if (socket != sock)
				{
					try
					{
						sock.getOutputStream().write(bytes);
					}
					catch (IOException e)
					{
						Toast.makeText(getBaseContext(),
								"ChatServerService: ������ write(BluetoothSocket socket, byte[] bytes):"
										+ e.getMessage(),
								Toast.LENGTH_LONG).show();
					}
				}
			}
		}
	}

	/** ����� ������� �������, ����������� ��������� ������������ ��������. */
	private class ConnectedThread extends Thread
	{
		private final BluetoothSocket socket;

		public ConnectedThread(BluetoothSocket sock)
		{
			socket = sock;
		}

		public void run()
		{
			InputStream inStream = null;
			//OutputStream outStream = null;

			try
			{
				inStream = socket.getInputStream();
				//outStream = socket.getOutputStream();
			}
			catch (IOException e)
			{
				obtainToast("Server - ConnectedThread: ������ BluetoothSocket.get...Stream: " + e.getMessage());
			}

			byte[] buffer = new byte[256]; // buffer store for the stream
			int bytes; // bytes returned from read()

			// ��������� ��������� �� InputStream �� ������� ������������ ����������
			while (true)
			{
				try
				{
					// ������ ��������� ������
					bytes = inStream.read(buffer);

					// ��������� the obtained bytes ��������� � ����� UI activity
					obtainMessage(buffer);
					write(socket, buffer);
				}
				catch (IOException e)
				{
					obtainToast("ChatServerService: ������ while (true){...} " + e.getMessage());
					break;
				}
			}

			synchronized (connectedThreads)
			{
				connectedThreads.remove(this);
				list.remove(socket);
			}

			this.cancel();
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel()
		{
			try
			{
				socket.close();
			}
			catch (IOException e)
			{
				obtainToast("Server - ConnectedThread: cancel() " + e.getMessage());
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

	// Server-���������� IChatClient ��� ChatActivity
	/**
	 * Server-���������� ������ �� ������� ���������� IChatClient, �����������
	 * �� �������� ������ ����� � �����������, ���������� � ������ Server.
	 * ������� false ��������� ChatActivity ��� ������� ����������� �
	 * ChatClientService.
	 */
	@Override
	public boolean connectToServer(Messenger selectedMessenger)
	{
		if (selectedMessenger == null)
		{
			Toast.makeText(getBaseContext(), "Server - connectToServer: ������ provider == null", Toast.LENGTH_LONG)
					.show();
			return false;
		}

		messenger = selectedMessenger;

		return true;
	}

	@Override
	public void sendMessage(String str)
	{
		write(null, str.getBytes());
	}

	@Override
	public void close()
	{
		destroyServer();
	}
}