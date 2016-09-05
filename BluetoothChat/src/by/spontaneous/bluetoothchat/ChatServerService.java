package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;

public class ChatServerService extends ChatService
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

	/** Socket ����� ����� �����������. */
	private BluetoothServerSocket mServerSocket;

	/** Thread ���������� �������� ����� ��������. */
	private AcceptThread mAcceptThread;

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
					// TODO: ...�� ��� ���������� � �������� Activity... o_0

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

				for (Communication thread : aConnectionThread)
				{
					thread.cancel();
				}

				// TODO: ������ ����� �������� �������� ��� �������������
				aConnectionThread.clear();
				aConfirmationHashMap.clear();
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
	private void broadcasting(Communication sender, MessagePacket packet)
	{
		synchronized (aConnectionThread)
		{
			for (Communication thread : aConnectionThread)
			{
				if (thread != sender)
				{
					try
					{
						thread.tOutStream.write(packet.bytes);
						synchronized (aConfirmationHashMap)
						{
							ArrayList<MessagePacket> wList = aConfirmationHashMap.get(thread);
							wList.add(packet);
						}
						transferResponseToUIThread(null, MessageCode.WAITING);
						
						/*
						aConfirmationTimer.schedule(new TimerTask()
						{
							public void run()
							{
								// TODO: ��������� ��������� ������� �������� ������������� ��������.
							}
						}, 1000);
						*/					
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
					synchronized (aConnectionThread)
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
					syncCheckIncomingConfirmation(packet);
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

		private void syncCheckIncomingConfirmation(MessagePacket packet)
		{
			synchronized (aConfirmationHashMap)
			{
				if (aConfirmationHashMap.containsKey(this))
				{
					boolean continueWaiting = false;
					boolean packetFound = false;
					for (MessagePacket mp : aConfirmationHashMap.get(this))
					{
						if (packet.id == mp.id)
						{
							aConfirmationHashMap.get(this).remove(mp);
							packetFound = true;
						}
						else
						{
							continueWaiting = true;
						}
					}

					if (!continueWaiting)
					{
						transferResponseToUIThread(null, MessageCode.CONFIRMATION);
					}
					
					if (!packetFound)
					{
						transferToast("������: ������ ������������� ������������� ��������!");
					}
				}
				else
				{
					transferToast("������: ����������� ���������� ����� ����������� ��������!");
				}
			}
		};

		@Override
		public void cancel()
		{
			// ��������� �����������: ������� ��� ����������� �� �������
			syncRemoveConnectedClient(this);

			super.cancel();
		}
	};
	
	
	/** ����� ������������������� ���������� �������� � ������ �������. */
	private void syncAddConnectedClient(BluetoothSocket socket)
	{
		// TODO: �������� ����� ����������� ������ ������� setName()
		final SyntheticThread thread = new SyntheticThread(socket);

		synchronized (aConnectionThread)
		{
			aConnectionThread.add(thread);
		}

		synchronized (aConfirmationHashMap)
		{
			aConfirmationHashMap.put(thread, new ArrayList<MessagePacket>());
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
		synchronized (aConnectionThread)
		{
			aConnectionThread.remove(targetThread);
		}

		synchronized (aConfirmationHashMap)
		{
			if (aConfirmationHashMap.containsKey(targetThread))
			{
				aConfirmationHashMap.remove(targetThread);

				boolean confirmationEmpty = true;
				for (ArrayList<MessagePacket> waitedPacketsConfirm : aConfirmationHashMap.values())
				{
					// ���� ���� ������ ��������� ������������� ��������
					if (!waitedPacketsConfirm.isEmpty())
					{
						confirmationEmpty = false;
						break;
					}
				}

				if (confirmationEmpty == true)
				{
					// ���� ��� ��������� ������������� ��������
					transferResponseToUIThread(null, MessageCode.CONFIRMATION);
				}
			}
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
		if (selectedMessenger != null)
		{
			aMessenger = selectedMessenger;
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
		broadcasting(null, new MessagePacket(MessageCode.MESSAGE, ++aLastOutputMsgNumber, msg));
	}

	@Override
	public void closeChatClient()
	{
		destroyServer();
	}
}