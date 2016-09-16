package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;
import by.spontaneous.bluetoothchat.R;

public final class ChatClientService extends ChatService
{
	public final class LocalBinder extends Binder
	{
		public final ChatClientService getService()
		{
			return ChatClientService.this;
		}
	};

	private final IBinder mBinder = new LocalBinder();

	@Override
	public final IBinder onBind(Intent intent)
	{
		return mBinder;
	};

	@Override
	public void onCreate()
	{
		super.onCreate();
		Toast.makeText(getBaseContext(), "ChatClientService onCreate()", Toast.LENGTH_SHORT).show();
	};

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		Toast.makeText(getBaseContext(), "ChatClientService onDestroy()", Toast.LENGTH_SHORT).show();
	};

	/** ������� ������� BluetoothDevice-������ (null ��� ������ �������). */
	private BluetoothDevice mMasterDevice;

	public void setMasterDevice(BluetoothDevice mDevice)
	{
		mMasterDevice = mDevice;
	};

	// Client-���������� IChatClient ��� ChatActivity
	/** ��������� Messanger ����� � UI. ���������� false, ���� �������� null. */
	@Override
	public boolean updateMessenger(Messenger selectedMessenger)
	{
		return super.updateMessanger(selectedMessenger);
	};

	/**
	 * Client-���������� ������ �� ������� ���������� IChatClient, �����������
	 * �� �������� ������ ����� � �����������, ���������� � ������ Server.
	 * ������� false ��������� ChatActivity ��� ������� ����������� �
	 * ChatClientService.
	 */
	@Override
	public boolean startConnection(Messenger selectedMessenger)
	{
		if (super.startConnection(selectedMessenger))
		{
			try
			{
				UUID myid = UUID.fromString(getResources().getString(R.string.service_uuid));

				final BluetoothSocket serverSocket = mMasterDevice.createRfcommSocketToServiceRecord(myid);
				serverSocket.connect();
				syncAddConnectionSocket(serverSocket);
				return true;
			}
			catch (IOException e)
			{
				// �������� ������ �����: ������� ����������� � ����������, ��
				// ������� �� ������ ������ ������ Service Discovery Protocol
				// (SDP)
				Toast.makeText(getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				return false;
			}
		}
		else
		{
			return false;
		}
	};

	@Override
	public void sendResponse(String msg)
	{
		// ������������ ��������� �������� ���������� ���������
		broadcasting(null, new MessagePacket(MessageCode.__TEXT, ++aLastOutputMsgNumber, msg));
	};

	@Override
	public final void stopConnection()
	{
		mMasterDevice = null;
		super.stopConnection();
	};
}