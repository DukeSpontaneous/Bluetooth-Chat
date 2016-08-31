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
	private Messenger mMessenger;

	/** Socket ����� ����� �����������. */
	private BluetoothServerSocket mServerSocket;

	/** Thread ���������� �������� ����� ��������. */
	private AcceptThread mAcceptThread;

	/** ������ Socket'�� �������� �����������. */
	private ArrayList<BluetoothSocket> mClientSockets = new ArrayList<BluetoothSocket>();

	/** ������ Thread'�� �������� �����������. */
	private final ArrayList<ConnectedThread> mClientThreads = new ArrayList<ConnectedThread>();

	/** ID ���������� ���������� ���������. */
	private int mLastOutputMsgNumber = 0;
	/** ���������� ���������� ���������� ���������. */
	private String mConfirmationMessage;
	/** ������ ��������, �� ������������� �������� ���������� ���������. */
	private final ArrayList<BluetoothSocket> mWaitedSockets = new ArrayList<BluetoothSocket>();

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
			mServerSocket = bAdapter.listenUsingRfcommWithServiceRecord(getResources().getString(R.string.service_name),
					UUID.fromString(getResources().getString(R.string.service_uuid)));
		}
		catch (IOException e)
		{
			Toast.makeText(getBaseContext(), "������ Server - createServer(): " + e.getMessage(), Toast.LENGTH_LONG)
					.show();
			transferResponseToUIThread(null, MessageCode.QUIT);
			return false;
		}

		mAcceptThread = new AcceptThread(mServerSocket);
		mAcceptThread.setDaemon(true);
		mAcceptThread.start();

		return true;
	}

	/**
	 * ����� ����������� Accept-���� �����-���� ��������� ����������� �������.
	 */
	public void destroyServer()
	{
		// ���������� ���� ����� ����� ��������
		if (mAcceptThread != null)
		{
			mAcceptThread.cancel();
			mAcceptThread = null;

			Toast.makeText(getBaseContext(), "Server - AcceptThread: AcceptThread ������� ��������!", Toast.LENGTH_LONG)
					.show();
		}

		// � ���� ����� ������ ��������������� � �������... ��� �� ��� ������...
		if (mServerSocket != null)
		{
			try
			{
				mServerSocket.close();
				mServerSocket = null;
			}
			catch (IOException e)
			{
				Toast.makeText(getBaseContext(), "������ ChatServerService serverSocket.close(): " + e.getMessage(),
						Toast.LENGTH_LONG).show();
			}
		}
	}

	/** ����� ������������������� ���������� �������� � ������ �������. */
	private void addConnectedClient(BluetoothSocket socket)
	{
		// TODO: �������� ����� ����������� ������ ������� setName()
		final ConnectedThread thread = new ConnectedThread(socket);

		synchronized (mClientSockets)
		{
			mClientThreads.add(thread);
			mClientSockets.add(socket);
		}

		thread.setDaemon(true);
		thread.start();
	}

	/** ����� ������������������� �������� �������� �� ������� �������. */
	private void removeConnectedClient(ConnectedThread thread)
	{
		thread.cancel();
		
		synchronized (mClientSockets)
		{
			mClientSockets.remove(thread.tSocket);
			mClientThreads.remove(thread);
		}
	}

	/** ����� ������ �������, ������������� ������ �������� �����������. */
	private class AcceptThread extends Thread
	{
		private final BluetoothServerSocket tServerSocket;

		public AcceptThread(BluetoothServerSocket socket)
		{
			tServerSocket = socket;
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
					socket = tServerSocket.accept();
					transferToast("AcceptThread: serverSocket.accept() ������!");

				}
				catch (IOException e)
				{
					// TODO: ����� �� �������� ���������, �� �����������
					// ��������������...
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
					addConnectedClient(socket);

					transferToast("AcceptThread: ����������� ���������!");
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel()
		{
			try
			{
				tServerSocket.close();

				for (ConnectedThread thread : mClientThreads)
				{
					thread.cancel();
				}

				transferToast("Server - AcceptThread: ��� ConnectedThread ������� ���������!");

				mClientThreads.clear();
				mClientSockets.clear();
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
	private void broadcasting(BluetoothSocket socket, MessagePacket packet)
	{
		// TODO: ������� ������������� ����� ���-�� ���... �� ���
		// ��������� �������� � ��������������...
		// ����� ��������������� ����� ������������� ��� ������ �����...

		// ������������ ���������
		mConfirmationMessage = packet.message;

		synchronized (mClientSockets)
		{
			// TODO: �������� ������ ����� �������� �� ������ Thread'��?
			for (BluetoothSocket sock : mClientSockets)
			{
				if (socket != sock)
				{
					try
					{
						sock.getOutputStream().write(packet.bytes);
						synchronized (mWaitedSockets)
						{
							mWaitedSockets.add(sock);
						}
						transferResponseToUIThread(null, MessageCode.WAITING);
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

		private int tLastInputMsgNumber = 0;

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
			// ���� ���������
			while (true)
			{
				// TODO: ������, �������, ��� ��������...
				final byte[] buffer = new byte[256];

				int bCount;
				try
				{
					// TODO: �������� ������������ bytes == 65356 ��� ���
					// ���������� ��������, �� ������ ���������� ����� ��
					// ������� ������ o_0, (���� ���� ������ ������).
					bCount = tInStream.read(buffer);
				}
				catch (IOException e)
				{
					transferToast("������ ��������� �������:" + e.getMessage());
					break;
				}				
				
				byte[] bPacket = Arrays.copyOfRange(buffer, 0, bCount);

				// ����� ������������ ����������� ���������
				final MessagePacket packet = new MessagePacket(bPacket);

				if (!packet.checkHash())
				{
					transferToast("������: ���������� ��������� ����������!");
					continue;
				}

				switch (packet.code)
				{
				case MESSAGE:
				{
					// ����� ����� ��������� ������������� � ����� ������
					synchronized (mClientSockets)
					{
						try
						{							
							tOutStream.write(new MessagePacket(MessageCode.CONFIRMATION, packet.id, null).bytes);
						}
						catch (IOException e)
						{
							transferToast("������ �������� CONFIRMATION: " + e.getMessage());
							continue;
						}
					}

					// TODO: ������ ������������ ������������������ ������
					// ���� ����������, �� ����������� ������, ����� ������
					// ������������ � �������, �� ������� �� �����
					// ���������� ����� �����������

					if (packet.id <= tLastInputMsgNumber)
					{
						transferToast("������ �������: ID ���������� ��������� ��������� >= ��������������!");
						continue;
					}

					transferResponseToUIThread(packet.message, packet.code);

					// ��������� ���� ��������, ����� �����������
					broadcasting(tSocket, packet);
					tLastInputMsgNumber = packet.id;
					break;
				}
				case CONFIRMATION:
				{
					if (packet.id == mLastOutputMsgNumber)
					{
						synchronized (mWaitedSockets)
						{
							if (mWaitedSockets.contains(tSocket))
							{
								mWaitedSockets.remove(tSocket);
								if (mWaitedSockets.isEmpty())
								{
									transferResponseToUIThread(null, MessageCode.CONFIRMATION);
									continue;
								}
							}
							else
							{
								Toast.makeText(getBaseContext(), "��������������: ������ �� ��������� �������������!",
										Toast.LENGTH_LONG).show();
								continue;
							}
						}
					}
					else
					{
						Toast.makeText(getBaseContext(), "��������������: ������ ���������� �������������!",
								Toast.LENGTH_LONG).show();
						continue;
					}
					break;
				}
				default:
					break;
				}
			}
			// ����� ������������ ����� ���������

			// ���� ��������� �����������, �� ������� ���� ������ �� �������
			// �������
			synchronized (mClientSockets)
			{
				mClientThreads.remove(this);
				mClientSockets.remove(tSocket);
			}

			this.cancel();
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel()
		{
			try
			{
				tSocket.close();
				transferToast("���������� � �������� ��������. Socket ������ �������.");
			}
			catch (IOException e)
			{
				transferToast("�������� Thread'�: ������ �������� Socket'� �������: " + e.getMessage());
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
			mMessenger.send(msg);
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
			mMessenger = selectedMessenger;
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
	public void sendResponse(String msg)
	{
		// ������������ ��������� �������� ���������� ���������
		broadcasting(null, new MessagePacket(MessageCode.MESSAGE, ++mLastOutputMsgNumber, msg));
	}

	@Override
	public void close()
	{
		destroyServer();
	}
}