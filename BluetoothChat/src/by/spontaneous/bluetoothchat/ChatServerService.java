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

	/** Messenger взаимодействия с GUI. */
	private Messenger messenger;

	/** Socket приёма новых подключений. */
	private BluetoothServerSocket serverSocket;

	/** Thread серверного ожидания новых клиентов. */
	private AcceptThread acceptThread;

	/** Список Socket'ов поднятых подключений. */
	private ArrayList<BluetoothSocket> list = new ArrayList<BluetoothSocket>();

	/** Список Thread'ов поднятых подключений. */
	private ArrayList<ConnectedThread> connectedThreads = new ArrayList<ConnectedThread>();

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

	/** Метод инициализации пары сокет-нить прослушки подключений сервера. */
	public boolean createServer(BluetoothAdapter bAdapter)
	{
		this.destroyServer();

		try
		{
			// MY_UUID is the app's UUID string, also used by the client
			// code
			serverSocket = bAdapter.listenUsingRfcommWithServiceRecord(getResources().getString(R.string.service_name),
					UUID.fromString(getResources().getString(R.string.service_uuid)));
		}
		catch (IOException e)
		{
			Toast.makeText(getBaseContext(), "Ошибка Server - createServer(): " + e.getMessage(), Toast.LENGTH_LONG)
					.show();
			transferResponseToUIThread(null, MessageCode.QUIT);
			return false;
		}

		acceptThread = new AcceptThread(serverSocket);
		acceptThread.setDaemon(true);
		acceptThread.start();

		return true;
	}

	/**
	 * Метод уничтожения Accept-пары Сокет-Нить прослушки подключений сервера.
	 */
	public void destroyServer()
	{
		// Остановить нить приёма новых клиентов
		if (acceptThread != null)
		{
			acceptThread.cancel();
			acceptThread = null;

			Toast.makeText(getBaseContext(), "Server - AcceptThread: AcceptThread успешно завершён!", Toast.LENGTH_LONG)
					.show();
		}

		// А этот сокет вообще останавливается в кенселе... что он тут делает...
		if (serverSocket != null)
		{
			try
			{
				serverSocket.close();
				serverSocket = null;
			}
			catch (IOException e)
			{
				Toast.makeText(getBaseContext(), "Ошибка ChatServerService serverSocket.close(): " + e.getMessage(),
						Toast.LENGTH_LONG).show();
			}
		}
	}

	/**
	 * Метод синхронизированного добавления ConnectedThread'ов в список сервера.
	 */
	private void manageConnectedSocket(BluetoothSocket socket)
	{
		// TODO: возможно стоит подписывать потоки методом setName()
		ConnectedThread thread = new ConnectedThread(socket);

		synchronized (connectedThreads)
		{
			connectedThreads.add(thread);
			list.add(socket);
		}

		thread.setDaemon(true);
		thread.start();
	}

	/** Класс потока сервера, составляющего список входящих подключений. */
	private class AcceptThread extends Thread
	{
		private final BluetoothServerSocket serverSocket;

		public AcceptThread(BluetoothServerSocket socket)
		{
			serverSocket = socket;
		}

		public void run()
		{
			BluetoothSocket socket = null;
			// Keep listening until exception occurs or a socket is returned
			while (true)
			{
				try
				{
					transferToast("AcceptThread: serverSocket.accept() начат!");
					socket = serverSocket.accept();
					transferToast("AcceptThread: serverSocket.accept() принят!");

				}
				catch (IOException e)
				{
					// TODO: будто бы работает нормально, но конструкция подозрительная...
					// Достижимо только через IOException прослушки...
					// ...но его генерирует и закрытие Activity... o_0
					
					transferToast("AcceptThread: ошибка serverSocket.accept()" + e.getMessage());
					transferResponseToUIThread(null, MessageCode.QUIT);
					return;
				}
				// If a connection was accepted
				if (socket != null)
				{
					// Do work to manage the connection (in a separate thread)
					manageConnectedSocket(socket);

					transferToast("AcceptThread: подключение добавлено!");
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel()
		{
			try
			{
				serverSocket.close();

				for (ConnectedThread thread : connectedThreads)
				{
					thread.cancel();
				}

				transferToast("Server - AcceptThread: все ConnectedThread успешно завершены!");

				connectedThreads.clear();
				list.clear();
			}
			catch (IOException e)
			{
				transferToast("Server - AcceptThread: ошибка serverSocket.cancel()" + e.getMessage());
			}
		}

	}

	/**
	 * Метод отправки сообщения сервером, рассылающий его всем клиентам, кроме
	 * отправителя.
	 */
	private void broadcasting(BluetoothSocket socket, byte[] bytes)
	{
		// TODO: ожидать подтверждение нужно где-то тут... но тут
		// всплывает пробелма с широковещанием...
		// Серия неподтверждений будет расцениваться как потеря связи...

		synchronized (connectedThreads)
		{
			for (BluetoothSocket sock : list)
			{
				if (socket != sock)
				{
					try
					{
						sock.getOutputStream().write(bytes);
					}
					catch (IOException e)
					{
						Toast.makeText(getBaseContext(), "Stream.write IOException:" + e.getMessage(),
								Toast.LENGTH_LONG).show();
						
						// TODO: Это признак потери связи с клиентом...
						// ...закрыть и удалить пару поток-соккет...
					}
				}
			}
		}
	}

	/** Класс потоков сервера, принимающих сообщения подключенных клиентов. */
	private class ConnectedThread extends Thread
	{
		private final BluetoothSocket tSocket;
		private final InputStream tInStream;
		private final OutputStream tOutStream;

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
				transferToast("ChatServerService: ошибка BluetoothSocket.get...Stream: " + e.getMessage());
			}

			tInStream = tmpIn;
			tOutStream = tmpOut;
		}

		public void run()
		{
			// Считывать сообщения из InputStream до первого выбрасывания
			// исключения
			while (true)
			{
				byte[] buffer = new byte[256]; // buffer store for the stream
				int bytes; // bytes returned from read()

				try
				{
					// Возможно bytes == 65356 это код завершения передачи
					// Возвращает число по размеру буфера o_0, даже если считал
					// меньше
					bytes = tInStream.read(buffer);

					// TODO: придумать прикладной протокол
					String msg = new String(Arrays.copyOfRange(buffer, 1, bytes - 1));
					transferResponseToUIThread(msg, MessageCode.fromId(buffer[0]));

					// Отправить всем клиентам, кроме отправителя
					broadcasting(tSocket, buffer);
				}
				catch (IOException e)
				{
					transferToast("ChatServerService: ошибка while (true){...} " + e.getMessage());
					break;
				}
			}

			synchronized (connectedThreads)
			{
				connectedThreads.remove(this);
				list.remove(tSocket);
			}

			this.cancel();
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel()
		{
			try
			{
				tSocket.close();
			}
			catch (IOException e)
			{
				transferToast("Server - ConnectedThread: cancel() " + e.getMessage());
			}
		}
	}

	/** Отправка запроса обработчику Messenger'а в Thread'е UI. */
	private synchronized void transferResponseToUIThread(String str, MessageCode code)
	{
		try
		{
			Message msg = Message.obtain(null, code.getId(), 0, 0);
			msg.obj = str;
			messenger.send(msg);
		}
		catch (RemoteException e)
		{
		}
	}

	/** Формирование запроса на вывод Toast в Thread'е UI. */
	private void transferToast(String str)
	{
		transferResponseToUIThread(str, MessageCode.TOAST);
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
			messenger = selectedMessenger;
			return true;
		}
		else
		{
			Toast.makeText(getBaseContext(), "Server - setMessenger: ошибка provider == null", Toast.LENGTH_LONG)
					.show();
			return false;
		}
	}

	@Override
	public void sendResponse(byte[] resp)
	{
		broadcasting(null, resp);
	}

	@Override
	public void close()
	{
		destroyServer();
	}
}