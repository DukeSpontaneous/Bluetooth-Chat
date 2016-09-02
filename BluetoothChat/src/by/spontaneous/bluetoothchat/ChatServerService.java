package by.spontaneous.bluetoothchat;

import java.io.IOException;
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

	/** Список Thread'ов поднятых подключений. */
	private final ArrayList<SyntheticThread> mClientThreads = new ArrayList<SyntheticThread>();

	/** ID последнего исходящего сообщения. */
	private volatile int mLastOutputMsgNumber = 0;
	/** Содержание последнего исходящего сообщения. */
	private volatile String mConfirmationMessage;
	// TODO: нужно ли как-то дополнительно оговаривать атомарность членов?
	/** Список клиентов, не подтвердивших доставку последнего сообщения. */
	private final ArrayList<SyntheticThread> mWaitedClients = new ArrayList<SyntheticThread>();

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

		// Если Thread по какой-то причине не закрыл свой ServerSocket
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
	private void syncAddConnectedClient(BluetoothSocket socket)
	{
		// TODO: возможно стоит подписывать потоки методом setName()
		final SyntheticThread thread = new SyntheticThread(socket);

		synchronized (mClientThreads)
		{
			mClientThreads.add(thread);
		}

		thread.setDaemon(true);
		thread.start();
	}

	// TODO: Вообще на данный момент Thread'ы занимаются самоликвидацией и
	// даже очищают от себя список Thread'ов и список ожидания o_0.
	// Вообще я сейчас задумался о том, чтобы перевести их на полное
	// самообслуживание через статические члены Communication...
	/** Метод синхронизированного удаления клиентов из списков сервера. */
	private void syncRemoveConnectedClient(SyntheticThread targetThread)
	{
		targetThread.cancel();

		synchronized (mClientThreads)
		{
			mClientThreads.remove(targetThread);
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
					// TODO: будто бы работает нормально, но конструкция
					// подозрительная...
					// Достижимо только через IOException прослушки...
					// ...но его генерирует и закрытие Activity... o_0

					transferToast("Ошибка ожидания нового клиента: " + e.getMessage());
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

				for (SyntheticThread thread : mClientThreads)
				{
					thread.cancel();
				}

				mClientThreads.clear();

				synchronized (mWaitedClients)
				{
					mWaitedClients.clear();
				}
			}
			catch (IOException e)
			{
				transferToast("Ошибка ServerSocket.cancel(): " + e.getMessage());
			}
			transferResponseToUIThread(null, MessageCode.QUIT);
		}

	}

	/**
	 * Метод отправки сообщения сервером, рассылающий его всем клиентам, кроме
	 * отправителя.
	 */
	private void broadcasting(SyntheticThread connected, MessagePacket packet)
	{
		// Отправленное сообщение
		mConfirmationMessage = packet.message;

		synchronized (mClientThreads)
		{
			// TODO: возможно список нужно заменить на список Thread'ов?
			for (SyntheticThread thread : mClientThreads)
			{
				if (thread != connected)
				{
					try
					{
						thread.tOutStream.write(packet.bytes);
						synchronized (mWaitedClients)
						{
							mWaitedClients.add(thread);
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
	private class SyntheticThread extends Communication
	{
		public SyntheticThread(BluetoothSocket socket)
		{
			super(socket);
		};

		@Override
		public void run()
		{
			// TODO: Размер, конечно, под вопросом...
			final byte[] buffer = new byte[256];
			int bCount;

			// Цикл прослушки
			while (true)
			{
				try
				{
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
					synchronized (mClientThreads)
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
					broadcasting(this, packet);
					tLastInputMsgNumber = packet.id;
					break;
				}
				case CONFIRMATION:
				{
					if (packet.id == mLastOutputMsgNumber)
					{
						synchronized (mWaitedClients)
						{
							if (mWaitedClients.contains(this))
							{
								mWaitedClients.remove(this);
								if (mWaitedClients.isEmpty())
								{
									transferResponseToUIThread(null, MessageCode.CONFIRMATION);
								}
							}
							else
							{
								Toast.makeText(getBaseContext(), "Предупреждение: пришло не ожидаемое подтверждение!",
										Toast.LENGTH_LONG).show();
							}
						}
					}
					else
					{
						Toast.makeText(getBaseContext(), "Предупреждение: пришло устаревшее подтверждение!",
								Toast.LENGTH_LONG).show();
					}
					break;
				}
				default:
					break;
				}
			}
			// Конец бесконечного цикла прослушки

			// TODO: ...нужно ли здесь явно останавливать Thread?
			this.cancel();
		};

		/* Call this from the main activity to shutdown the connection */
		@Override
		public void cancel()
		{
			// Прослушка остановлена: удалить этот клиент из списков сервера
			synchronized (mClientThreads)
			{
				mClientThreads.remove(this);

				synchronized (mWaitedClients)
				{
					if (mWaitedClients.contains(this))
					{
						mWaitedClients.remove(this);
						if (mWaitedClients.isEmpty())
						{
							transferResponseToUIThread(null, MessageCode.CONFIRMATION);
						}
					}
				}
			}

			super.cancel();
		}
	};

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
			Toast.makeText(getBaseContext(), "Ошибка: selectedMessenger == null", Toast.LENGTH_LONG).show();
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
	public void closeChatClient()
	{
		destroyServer();
	}
}