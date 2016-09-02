package by.spontaneous.bluetoothchat;

import java.io.IOException;
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
	private Messenger mMessenger;

	/** Thread ����������� �������� ����� ��������� �� �������. */
	private SyntheticThread mConnectedThread;

	/** ID ���������� ���������� ���������. */
	private volatile int mLastOutputMsgNumber = 0;
	/** ���������� ���������� ���������� ���������. */
	private volatile String mConfirmationMessage;
	/** ������ ��������, �� ������������� �������� ���������� ���������. */
	private volatile boolean mWaitingConfirmation = false;

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

			mMessenger = null;
		}
	}

	public void setMasterDevice(BluetoothDevice mDevice)
	{
		mMasterDevice = mDevice;
	}

	/**
	 * ���� ���� ����� ������ ������ ��� �������������� � ChatSercverService,
	 * ��� broadcast ������������ ������������.
	 */
	private void unicasting(MessagePacket packet)
	{
		// ������������ ���������
		mConfirmationMessage = packet.message;

		try
		{
			synchronized (mConnectedThread)
			{
				mConnectedThread.tOutStream.write(packet.bytes);
			}
			mWaitingConfirmation = true;
			transferResponseToUIThread(null, MessageCode.WAITING);
		}
		catch (IOException e)
		{
			// ��� ������� ������ ����� � ��������.
			Toast.makeText(getBaseContext(), "Stream.write IOException:" + e.getMessage(), Toast.LENGTH_LONG).show();
			transferResponseToUIThread(null, MessageCode.QUIT);
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
					synchronized (mConnectedThread)
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

					tLastInputMsgNumber = packet.id;
					break;
				}
				case CONFIRMATION:
				{
					if (packet.id == mLastOutputMsgNumber)
					{
						if (mWaitingConfirmation == true)
						{
							mWaitingConfirmation = false;
							transferResponseToUIThread(null, MessageCode.CONFIRMATION);
						}
						else
						{
							Toast.makeText(getBaseContext(), "��������������: ������ �� ��������� �������������!",
									Toast.LENGTH_LONG).show();
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
			super.cancel();
			
			transferResponseToUIThread(null, MessageCode.QUIT);
		};
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

			mMessenger = selectedMessenger;

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

			mConnectedThread = new SyntheticThread(serverSocket);
			mConnectedThread.setDaemon(true);
			mConnectedThread.start();

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
		unicasting(new MessagePacket(MessageCode.MESSAGE, ++mLastOutputMsgNumber, msg));
	}

	@Override
	public void closeChatClient()
	{
		disconnect();
	}
}