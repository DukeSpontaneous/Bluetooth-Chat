package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
			transferResponseToUIThread(null, MessageCode.QUIT);
			return false;
		}

		acceptThread = new AcceptThread(serverSocket);
		acceptThread.setDaemon(true);
		acceptThread.start();

		return true;
	}

	/**
	 * ����� ����������� Accept-���� �����-���� ��������� ����������� �������.
	 */
	public void destroyServer()
	{
		// ���������� ���� ����� ����� ��������
		if (acceptThread != null)
		{
			acceptThread.cancel();
			acceptThread = null;

			Toast.makeText(getBaseContext(), "Server - AcceptThread: AcceptThread ������� ��������!", Toast.LENGTH_LONG)
					.show();
		}

		// � ���� ����� ������ ��������������� � �������... ��� �� ��� ������...
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
	}

	/**
	 * ����� ������������������� ���������� ConnectedThread'�� � ������ �������.
	 */
	private void manageConnectedSocket(BluetoothSocket socket)
	{
		// TODO: �������� ����� ����������� ������ ������� setName()
		ConnectedThread thread = new ConnectedThread(socket);

		synchronized (connectedThreads)
		{
			connectedThreads.add(thread);
			list.add(socket);
		}

		thread.setDaemon(true);
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
					transferToast("AcceptThread: serverSocket.accept() �����!");
					socket = serverSocket.accept();
					transferToast("AcceptThread: serverSocket.accept() ������!");

				}
				catch (IOException e)
				{
					// TODO: ����� �� �������� ���������, �� ����������� ��������������...
					// ��������� ������ ����� IOException ���������...
					// ...�� ��� ���������� � �������� Activity... o_0
					
					transferToast("AcceptThread: ������ serverSocket.accept()" + e.getMessage());
					transferResponseToUIThread(null, MessageCode.QUIT);
					return;
				}
				// If a connection was accepted
				if (socket != null)
				{
					// Do work to manage the connection (in a separate thread)
					manageConnectedSocket(socket);

					transferToast("AcceptThread: ����������� ���������!");
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

				transferToast("Server - AcceptThread: ��� ConnectedThread ������� ���������!");

				connectedThreads.clear();
				list.clear();
			}
			catch (IOException e)
			{
				transferToast("Server - AcceptThread: ������ serverSocket.cancel()" + e.getMessage());
			}
		}

	}

	/**
	 * ����� �������� ��������� ��������, ����������� ��� ���� ��������, �����
	 * �����������.
	 */
	private void broadcasting(BluetoothSocket socket, byte[] bytes)
	{
		// TODO: ������� ������������� ����� ���-�� ���... �� ���
		// ��������� �������� � ��������������...
		// ����� ��������������� ����� ������������� ��� ������ �����...

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
						Toast.makeText(getBaseContext(), "Stream.write IOException:" + e.getMessage(),
								Toast.LENGTH_LONG).show();
						
						// TODO: ��� ������� ������ ����� � ��������...
						// ...������� � ������� ���� �����-������...
					}
				}
			}
		}
	}

	/** ����� ������� �������, ����������� ��������� ������������ ��������. */
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
				transferToast("ChatServerService: ������ BluetoothSocket.get...Stream: " + e.getMessage());
			}

			tInStream = tmpIn;
			tOutStream = tmpOut;
		}

		public void run()
		{
			// ��������� ��������� �� InputStream �� ������� ������������
			// ����������
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

					// ��������� ���� ��������, ����� �����������
					broadcasting(tSocket, buffer);
				}
				catch (IOException e)
				{
					transferToast("ChatServerService: ������ while (true){...} " + e.getMessage());
					break;
				}
			}

			synchronized (connectedThreads)
			{
				connectedThreads.remove(this);
				list.remove(tSocket);
			}

			this.cancel();
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel()
		{
			try
			{
				tSocket.close();
			}
			catch (IOException e)
			{
				transferToast("Server - ConnectedThread: cancel() " + e.getMessage());
			}
		}
	}

	/** �������� ������� ����������� Messenger'� � Thread'� UI. */
	private synchronized void transferResponseToUIThread(String str, MessageCode code)
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
		if (selectedMessenger != null)
		{
			messenger = selectedMessenger;
			return true;
		}
		else
		{
			Toast.makeText(getBaseContext(), "Server - setMessenger: ������ provider == null", Toast.LENGTH_LONG)
					.show();
			return false;
		}
	}

	@Override
	public void sendResponse(byte[] resp)
	{
		broadcasting(null, resp);
	}

	@Override
	public void close()
	{
		destroyServer();
	}
}