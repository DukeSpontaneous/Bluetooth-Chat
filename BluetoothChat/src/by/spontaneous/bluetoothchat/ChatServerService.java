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
	private Messenger mMessenger;

	/** Socket приёма новых подключений. */
	private BluetoothServerSocket mServerSocket;

	/** Thread серверного ожидания новых клиентов. */
	private AcceptThread mAcceptThread;

	/** Список Socket'ов поднятых подключений. */
	private ArrayList<BluetoothSocket> mClientSockets = new ArrayList<BluetoothSocket>();

	/** Список Thread'ов поднятых подключений. */
	private final ArrayList<ConnectedThread> mClientThreads = new ArrayList<ConnectedThread>();

	/** ID последнего исходящего сообщения. */
	private int mLastOutputMsgNumber = 0;
	/** Содержание последнего исходящего сообщения. */
	private String mConfirmationMessage;
	/** Список клиентов, не подтвердивших доставку последнего сообщения. */
	private final ArrayList<BluetoothSocket> mWaitedSockets = new ArrayList<BluetoothSocket>();

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
			mServerSocket = bAdapter.listenUsingRfcommWithServiceRecord(getResources().getString(R.string.service_name),
					UUID.fromString(getResources().getString(R.string.service_uuid)));
		}
		catch (IOException e)
		{
			Toast.makeText(getBaseContext(), "Ошибка Server - createServer(): " + e.getMessage(), Toast.LENGTH_LONG)
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
	 * Метод уничтожения Accept-пары Сокет-Нить прослушки подключений сервера.
	 */
	public void destroyServer()
	{
		// Остановить нить приёма новых клиентов
		if (mAcceptThread != null)
		{
			mAcceptThread.cancel();
			mAcceptThread = null;

			Toast.makeText(getBaseContext(), "Server - AcceptThread: AcceptThread успешно завершён!", Toast.LENGTH_LONG)
					.show();
		}

		// А этот сокет вообще останавливается в кенселе... что он тут делает...
		if (mServerSocket != null)
		{
			try
			{
				mServerSocket.close();
				mServerSocket = null;
			}
			catch (IOException e)
			{
				Toast.makeText(getBaseContext(), "Ошибка ChatServerService serverSocket.close(): " + e.getMessage(),
						Toast.LENGTH_LONG).show();
			}
		}
	}

	/** Метод синхронизированного добавления клиентов в списки сервера. */
	private void addConnectedClient(BluetoothSocket socket)
	{
		// TODO: возможно стоит подписывать потоки методом setName()
		final ConnectedThread thread = new ConnectedThread(socket);

		synchronized (mClientSockets)
		{
			mClientThreads.add(thread);
			mClientSockets.add(socket);
		}

		thread.setDaemon(true);
		thread.start();
	}

	/** Метод синхронизированного удаления клиентов из списков сервера. */
	private void removeConnectedClient(ConnectedThread thread)
	{
		thread.cancel();
		
		synchronized (mClientSockets)
		{
			mClientSockets.remove(thread.tSocket);
			mClientThreads.remove(thread);
		}
	}

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
			BluetoothSocket socket = null;
			// Keep listening until exception occurs or a socket is returned
			while (true)
			{
				try
				{
					transferToast("AcceptThread: serverSocket.accept() начат!");
					socket = tServerSocket.accept();
					transferToast("AcceptThread: serverSocket.accept() принят!");

				}
				catch (IOException e)
				{
					// TODO: будто бы работает нормально, но конструкция
					// подозрительная...
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
					addConnectedClient(socket);

					transferToast("AcceptThread: подключение добавлено!");
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel()
		{
			try
			{
				tServerSocket.close();

				for (ConnectedThread thread : mClientThreads)
				{
					thread.cancel();
				}

				transferToast("Server - AcceptThread: все ConnectedThread успешно завершены!");

				mClientThreads.clear();
				mClientSockets.clear();
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
	private void broadcasting(BluetoothSocket socket, MessagePacket packet)
	{
		// TODO: ожидать подтверждение нужно где-то тут... но тут
		// всплывает пробелма с широковещанием...
		// Серия неподтверждений будет расцениваться как потеря связи...

		// Отправленное сообщение
		mConfirmationMessage = packet.message;

		synchronized (mClientSockets)
		{
			// TODO: возможно список нужно заменить на список Thread'ов?
			for (BluetoothSocket sock : mClientSockets)
			{
				if (socket != sock)
				{
					try
					{
						sock.getOutputStream().write(packet.bytes);
						synchronized (mWaitedSockets)
						{
							mWaitedSockets.add(sock);
						}
						transferResponseToUIThread(null, MessageCode.WAITING);
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

		private int tLastInputMsgNumber = 0;

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
			// Цикл прослушки
			while (true)
			{
				// TODO: Размер, конечно, под вопросом...
				final byte[] buffer = new byte[256];

				int bCount;
				try
				{
					// TODO: Возможно возвращённые bytes == 65356 это код
					// завершения передачи, но обычно возвращает число по
					// размеру буфера o_0, (даже если считал меньше).
					bCount = tInStream.read(buffer);
				}
				catch (IOException e)
				{
					transferToast("Ошибка прослушки клиента:" + e.getMessage());
					break;
				}				
				
				byte[] bPacket = Arrays.copyOfRange(buffer, 0, bCount);

				// Пакет придуманного прикладного протокола
				final MessagePacket packet = new MessagePacket(bPacket);

				if (!packet.checkHash())
				{
					transferToast("Ошибка: полученное сообщение повреждено!");
					continue;
				}

				switch (packet.code)
				{
				case MESSAGE:
				{
					// Сразу нужно отправить подтверждение в любом случае
					synchronized (mClientSockets)
					{
						try
						{							
							tOutStream.write(new MessagePacket(MessageCode.CONFIRMATION, packet.id, null).bytes);
						}
						catch (IOException e)
						{
							transferToast("Ошибка отправки CONFIRMATION: " + e.getMessage());
							continue;
						}
					}

					// TODO: вообще теоретически последовательность должна
					// быть непрерывна, за исключением случая, когда клиент
					// подключается к серверу, на котором до этого
					// происходил обмен сообщениями

					if (packet.id <= tLastInputMsgNumber)
					{
						transferToast("Ошибка Сервера: ID последнего принятого сообщения >= анализируемому!");
						continue;
					}

					transferResponseToUIThread(packet.message, packet.code);

					// Отправить всем клиентам, кроме отправителя
					broadcasting(tSocket, packet);
					tLastInputMsgNumber = packet.id;
					break;
				}
				case CONFIRMATION:
				{
					if (packet.id == mLastOutputMsgNumber)
					{
						synchronized (mWaitedSockets)
						{
							if (mWaitedSockets.contains(tSocket))
							{
								mWaitedSockets.remove(tSocket);
								if (mWaitedSockets.isEmpty())
								{
									transferResponseToUIThread(null, MessageCode.CONFIRMATION);
									continue;
								}
							}
							else
							{
								Toast.makeText(getBaseContext(), "Предупреждение: пришло не ожидаемое подтверждение!",
										Toast.LENGTH_LONG).show();
								continue;
							}
						}
					}
					else
					{
						Toast.makeText(getBaseContext(), "Предупреждение: пришло устаревшее подтверждение!",
								Toast.LENGTH_LONG).show();
						continue;
					}
					break;
				}
				default:
					break;
				}
			}
			// Конец бесконечного цикла прослушки

			// Если прослушка остановлена, то удалить этот клиент из списков
			// сервера
			synchronized (mClientSockets)
			{
				mClientThreads.remove(this);
				mClientSockets.remove(tSocket);
			}

			this.cancel();
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel()
		{
			try
			{
				tSocket.close();
				transferToast("Соединение с клиентом потеряно. Socket закрыт успешно.");
			}
			catch (IOException e)
			{
				transferToast("Закрытие Thread'а: ошибка закрытия Socket'а клиента: " + e.getMessage());
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
			mMessenger.send(msg);
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
			mMessenger = selectedMessenger;
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
	public void sendResponse(String msg)
	{
		// Формирование сообщения согласно выбранному протоколу
		broadcasting(null, new MessagePacket(MessageCode.MESSAGE, ++mLastOutputMsgNumber, msg));
	}

	@Override
	public void close()
	{
		destroyServer();
	}
}