package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;

public class ChatClientService extends ChatService
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

	/** Thread ����������� �������� ����� ��������� �� �������. */
	private Communication mConnectedThread;

	/** ������� ������� BluetoothDevice-������ (null ��� ������ �������). */
	private BluetoothDevice mMasterDevice;

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
		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;

			aMessenger = null;
		}
	}

	public void setMasterDevice(BluetoothDevice mDevice)
	{
		mMasterDevice = mDevice;
	}

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

	/** ����� ������ �������, ����������� ��������� ������������� �������. */
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
			
			transferResponseToUIThread(null, MessageCode.QUIT);
		};
	}

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
			// Socket ����� � mMasterDevice (��������).
			BluetoothSocket serverSocket;

			aMessenger = selectedMessenger;

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
				serverSocket = mMasterDevice.createRfcommSocketToServiceRecord(myid);
				serverSocket.connect();
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

			syncAddConnectedClient(serverSocket);
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
		disconnect();
	}
}