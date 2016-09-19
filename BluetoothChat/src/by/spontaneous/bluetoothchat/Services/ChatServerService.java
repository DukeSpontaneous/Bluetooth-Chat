package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;
import by.spontaneous.bluetoothchat.R;

public final class ChatServerService extends ChatService
{
	public final class LocalBinder extends Binder
	{
		public final ChatServerService getService()
		{
			return ChatServerService.this;
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
		Toast.makeText(getBaseContext(), "ChatServerService onCreate()", Toast.LENGTH_SHORT).show();
	};

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		destroyAcceptThread();
		Toast.makeText(getBaseContext(), "ChatServerService onDestroy()", Toast.LENGTH_SHORT).show();
	};

	/** Thread ���������� �������� ����� ��������. */
	private AcceptThread mAcceptThread;

	/** ����� ������ �������, ������������� ������ �������� �����������. */
	private class AcceptThread extends Thread
	{
		private final BluetoothServerSocket tServerSocket;

		public AcceptThread(BluetoothServerSocket socket)
		{
			tServerSocket = socket;
		};

		public void run()
		{
			while (true)
			{
				transferToast("�������� ������ �������...");
				try
				{
					final BluetoothSocket clientSocket = tServerSocket.accept();
					syncAddConnectionSocket(clientSocket);
				}
				catch (IOException e)
				{
					// �������� ����� �� ����� �������� (ServerSocket ���
					// ������?)
					if (e.getMessage() == "[JSR82] accept: Connection is not created (failed or aborted).")
						return;

					// �������������� ����� �� ����� ��������...
					transferToast(e.getMessage());
					break;
				}
				transferToast("��������� ����� ������!");
			}
			transferToast("������: �������� ����� �������� �������� ����������!");
			transferResponseToUIThread(GUIRequestCode._QUIT, null);
			this.cancel();
		};

		/** ��������� ServerSocket ����������� ���������. */
		private void cancel()
		{
			try
			{
				tServerSocket.close();
			}
			catch (IOException e)
			{
				transferToast("������ ServerSocket.cancel(): " + e.getMessage());
			}
			transferResponseToUIThread(GUIRequestCode._QUIT, null);
			transferCut(this);
		};
	};

	/** ����� ������������� ���� �����-���� ��������� ����������� �������. */
	private boolean createAcceptThread()
	{
		this.destroyAcceptThread();

		BluetoothServerSocket acceptServerSocket;
		try
		{
			final BluetoothAdapter bAdapter = BluetoothAdapter.getDefaultAdapter();

			acceptServerSocket = bAdapter.listenUsingRfcommWithServiceRecord(
					getResources().getString(R.string.service_name),
					UUID.fromString(getResources().getString(R.string.service_uuid)));
		}
		catch (IOException e)
		{
			Toast.makeText(getBaseContext(), "������ �������� AcceptThread: " + e.getMessage(), Toast.LENGTH_LONG)
					.show();
			transferResponseToUIThread(GUIRequestCode._QUIT, null);
			return false;
		}

		mAcceptThread = new AcceptThread(acceptServerSocket);
		mAcceptThread.setDaemon(true);
		mAcceptThread.start();

		return true;
	};

	/**
	 * ����� ����������� Accept-���� �����-���� ��������� ����������� �������.
	 */
	private void destroyAcceptThread()
	{
		// ���������� ���� ����� ����� ��������
		if (mAcceptThread != null)
		{
			mAcceptThread.cancel();
			mAcceptThread = null;

			Toast.makeText(getBaseContext(), "AcceptThread ������� ��������!", Toast.LENGTH_SHORT).show();
		}
	};

	// Server-���������� IChatClient ��� ChatActivity
	/** ��������� Messanger ����� � UI. ���������� false, ���� �������� null. */
	@Override
	public boolean updateMessenger(Messenger selectedMessenger)
	{
		return super.updateMessanger(selectedMessenger);
	};

	/**
	 * Server-���������� ������ �� ������� ���������� IChatClient, �����������
	 * �� �������� ������ ����� � �����������, ���������� � ������ Server.
	 * ������� false ��������� ChatActivity ��� ������� ����������� �
	 * ChatClientService.
	 */
	@Override
	public boolean startConnection(Messenger selectedMessenger)
	{
		if (super.startConnection(selectedMessenger))
		{
			// ������� ��������� ������
			return createAcceptThread();
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
		destroyAcceptThread();
		super.stopConnection();
	};
}