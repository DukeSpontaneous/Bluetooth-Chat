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

	/** Thread серверного ожидания новых клиентов. */
	private AcceptThread mAcceptThread;

	/** Класс потока сервера, составляющего список входящих подключений. */
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
					transferToast("Ожидание нового клиента...");
					clientSocket = tServerSocket.accept();
					transferToast("Подключен новый клиент!");
				}
				catch (IOException e)
				{
					// TODO: ...но его генерирует и закрытие Activity... o_0

					transferToast("Ошибка ожидания нового клиента: " + e.getMessage());
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

				// TODO: скорее всего излишняя операция для перестраховки
				aConnectionThread.clear();
				aConfirmationMap.clear();
			}
			catch (IOException e)
			{
				transferToast("Ошибка ServerSocket.cancel(): " + e.getMessage());
			}
			transferResponseToUIThread(null, GUIRequestCode._QUIT);
		}
	}

	/** Метод инициализации пары сокет-нить прослушки подключений сервера. */
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
			Toast.makeText(getBaseContext(), "Ошибка создания AcceptThread: " + e.getMessage(), Toast.LENGTH_LONG)
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
	 * Метод уничтожения Accept-пары Сокет-Нить прослушки подключений сервера.
	 */
	private void destroyAcceptThread()
	{
		// Остановить нить приёма новых клиентов
		if (mAcceptThread != null)
		{
			mAcceptThread.cancel();
			mAcceptThread = null;

			Toast.makeText(getBaseContext(), "AcceptThread успешно завершён!", Toast.LENGTH_LONG)
					.show();
		}
	}

	// Server-реализация IChatClient для ChatActivity
	/**
	 * Server-реализация одного из методов интерфейса IChatClient, отвечающего
	 * за создание потока связи с приложением, запущенном в режиме Server.
	 * Возврат false закрывает ChatActivity при попытке подключения к
	 * ChatClientService.
	 */
	@Override
	public boolean connectToServer(Messenger selectedMessenger)
	{
		if (selectedMessenger != null)
		{
			aMessenger = selectedMessenger;

			// Попытка запустить сервер
			return createAcceptThread();
		}
		else
		{
			Toast.makeText(getBaseContext(), "Ошибка: selectedMessenger == null", Toast.LENGTH_LONG).show();
			return false;
		}
	}

	@Override
	public void sendResponse(String msg)
	{
		// Формирование сообщения согласно выбранному протоколу
		broadcasting(null, new MessagePacket(MessageCode.__TEXT, ++aLastOutputMsgNumber, msg));
	}

	@Override
	public void closeChatClient()
	{
		destroyAcceptThread();
		aMessenger = null;
	}
}