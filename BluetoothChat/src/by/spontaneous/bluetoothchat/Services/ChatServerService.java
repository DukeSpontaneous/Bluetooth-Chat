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
			while (true)
			{
				transferToast("Ожидание нового клиента...");
				try
				{					
					final BluetoothSocket clientSocket = tServerSocket.accept();
					syncAddConnectedClient(clientSocket);					
				}
				catch (IOException e)
				{
					// Основной выход из цикла ожидания
					if(e.getMessage() == "[JSR82] accept: Connection is not created (failed or aborted).")
						return;
					
					// Непредвиденный выход из цикла ожидания...
					transferToast(e.getMessage());
					break;
				}
				transferToast("Подключен новый клиент!");
			}
			transferToast("Ошибка: ожидание новых клиентов аварийно прекращено!");
			transferResponseToUIThread(GUIRequestCode._QUIT, null);
			this.cancel();
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel()
		{
			try
			{
				tServerSocket.close();
			}
			catch (IOException e)
			{
				transferToast("Ошибка ServerSocket.cancel(): " + e.getMessage());
			}
			transferResponseToUIThread(GUIRequestCode._QUIT, null);
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
			transferResponseToUIThread(GUIRequestCode._QUIT, null);
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
	public final void closeChatClient()
	{		
		destroyAcceptThread();
		super.closeChatClient();
	}
}