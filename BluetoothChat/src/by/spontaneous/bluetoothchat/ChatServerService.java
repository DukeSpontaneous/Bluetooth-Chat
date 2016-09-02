package by.spontaneous.bluetoothchat;

import java.io.IOException;
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

	/** ������ Thread'�� �������� �����������. */
	private final ArrayList<SyntheticThread> mClientThreads = new ArrayList<SyntheticThread>();

	/** ID ���������� ���������� ���������. */
	private volatile int mLastOutputMsgNumber = 0;
	/** ���������� ���������� ���������� ���������. */
	private volatile String mConfirmationMessage;
	// TODO: ����� �� ���-�� ������������� ����������� ����������� ������?
	/** ������ ��������, �� ������������� �������� ���������� ���������. */
	private final ArrayList<SyntheticThread> mWaitedClients = new ArrayList<SyntheticThread>();

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

		// ���� Thread �� �����-�� ������� �� ������ ���� ServerSocket
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
	private void syncAddConnectedClient(BluetoothSocket socket)
	{
		// TODO: �������� ����� ����������� ������ ������� setName()
		final SyntheticThread thread = new SyntheticThread(socket);

		synchronized (mClientThreads)
		{
			mClientThreads.add(thread);
		}

		thread.setDaemon(true);
		thread.start();
	}

	// TODO: ������ �� ������ ������ Thread'� ���������� ��������������� �
	// ���� ������� �� ���� ������ Thread'�� � ������ �������� o_0.
	// ������ � ������ ��������� � ���, ����� ��������� �� �� ������
	// ���������������� ����� ����������� ����� Communication...
	/** ����� ������������������� �������� �������� �� ������� �������. */
	private void syncRemoveConnectedClient(SyntheticThread targetThread)
	{
		targetThread.cancel();

		synchronized (mClientThreads)
		{
			mClientThreads.remove(targetThread);
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
			BluetoothSocket clientSocket;
			while (true)
			{
				clientSocket = null;
				try
				{
					transferToast("�������� ������ �������...");
					clientSocket = tServerSocket.accept();
					transferToast("��������� ����� ������!");
				}
				catch (IOException e)
				{
					// TODO: ����� �� �������� ���������, �� �����������
					// ��������������...
					// ��������� ������ ����� IOException ���������...
					// ...�� ��� ���������� � �������� Activity... o_0

					transferToast("������ �������� ������ �������: " + e.getMessage());
					transferResponseToUIThread(null, MessageCode.QUIT);
					return;
				}
				if (clientSocket != null)
				{
					syncAddConnectedClient(clientSocket);
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel()
		{
			try
			{
				tServerSocket.close();

				for (SyntheticThread thread : mClientThreads)
				{
					thread.cancel();
				}

				mClientThreads.clear();

				synchronized (mWaitedClients)
				{
					mWaitedClients.clear();
				}
			}
			catch (IOException e)
			{
				transferToast("������ ServerSocket.cancel(): " + e.getMessage());
			}
			transferResponseToUIThread(null, MessageCode.QUIT);
		}

	}

	/**
	 * ����� �������� ��������� ��������, ����������� ��� ���� ��������, �����
	 * �����������.
	 */
	private void broadcasting(SyntheticThread connected, MessagePacket packet)
	{
		// ������������ ���������
		mConfirmationMessage = packet.message;

		synchronized (mClientThreads)
		{
			// TODO: �������� ������ ����� �������� �� ������ Thread'��?
			for (SyntheticThread thread : mClientThreads)
			{
				if (thread != connected)
				{
					try
					{
						thread.tOutStream.write(packet.bytes);
						synchronized (mWaitedClients)
						{
							mWaitedClients.add(thread);
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
	private class SyntheticThread extends Communication
	{
		public SyntheticThread(BluetoothSocket socket)
		{
			super(socket);
		};

		@Override
		public void run()
		{
			// TODO: ������, �������, ��� ��������...
			final byte[] buffer = new byte[256];
			int bCount;

			// ���� ���������
			while (true)
			{
				try
				{
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
					synchronized (mClientThreads)
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
					broadcasting(this, packet);
					tLastInputMsgNumber = packet.id;
					break;
				}
				case CONFIRMATION:
				{
					if (packet.id == mLastOutputMsgNumber)
					{
						synchronized (mWaitedClients)
						{
							if (mWaitedClients.contains(this))
							{
								mWaitedClients.remove(this);
								if (mWaitedClients.isEmpty())
								{
									transferResponseToUIThread(null, MessageCode.CONFIRMATION);
								}
							}
							else
							{
								Toast.makeText(getBaseContext(), "��������������: ������ �� ��������� �������������!",
										Toast.LENGTH_LONG).show();
							}
						}
					}
					else
					{
						Toast.makeText(getBaseContext(), "��������������: ������ ���������� �������������!",
								Toast.LENGTH_LONG).show();
					}
					break;
				}
				default:
					break;
				}
			}
			// ����� ������������ ����� ���������

			// TODO: ...����� �� ����� ���� ������������� Thread?
			this.cancel();
		};

		/* Call this from the main activity to shutdown the connection */
		@Override
		public void cancel()
		{
			// ��������� �����������: ������� ���� ������ �� ������� �������
			synchronized (mClientThreads)
			{
				mClientThreads.remove(this);

				synchronized (mWaitedClients)
				{
					if (mWaitedClients.contains(this))
					{
						mWaitedClients.remove(this);
						if (mWaitedClients.isEmpty())
						{
							transferResponseToUIThread(null, MessageCode.CONFIRMATION);
						}
					}
				}
			}

			super.cancel();
		}
	};

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
			Toast.makeText(getBaseContext(), "������: selectedMessenger == null", Toast.LENGTH_LONG).show();
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
	public void closeChatClient()
	{
		destroyServer();
	}
}