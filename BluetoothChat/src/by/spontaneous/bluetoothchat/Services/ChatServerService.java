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
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public final IBinder onBind(Intent intent)
	{
		return mBinder;
	}

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
		destroyAcceptThread();
		Toast.makeText(getBaseContext(), "ChatServerService onDestroy()", Toast.LENGTH_LONG).show();
	}

	/** Thread ���������� �������� ����� ��������. */
	private AcceptThread mAcceptThread;

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
					transferResponseToUIThread(null, GUIRequestCode._QUIT);
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

				for (SocketThread thread : aConnectionThread)
				{
					thread.cancel();
				}

				// TODO: ������ ����� �������� �������� ��� �������������
				aConnectionThread.clear();
				aConfirmationMap.clear();
			}
			catch (IOException e)
			{
				transferToast("������ ServerSocket.cancel(): " + e.getMessage());
			}
			transferResponseToUIThread(null, GUIRequestCode._QUIT);
		}
	}

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
			transferResponseToUIThread(null, GUIRequestCode._QUIT);
			return false;
		}

		mAcceptThread = new AcceptThread(acceptServerSocket);
		mAcceptThread.setDaemon(true);
		mAcceptThread.start();

		return true;
	}

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

			Toast.makeText(getBaseContext(), "AcceptThread ������� ��������!", Toast.LENGTH_LONG)
					.show();
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

			// ������� ��������� ������
			return createAcceptThread();
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
		broadcasting(null, new MessagePacket(MessageCode.__TEXT, ++aLastOutputMsgNumber, msg));
	}

	@Override
	public void closeChatClient()
	{
		destroyAcceptThread();
		aMessenger = null;
	}
}