package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;

public class ChatServerService extends ChatService
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

	/** Socket приёма новых подключений. */
	private BluetoothServerSocket mServerSocket;

	/** Thread серверного ожидания новых клиентов. */
	private AcceptThread mAcceptThread;

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

				for (Communication thread : aConnectionThread)
				{
					thread.cancel();
				}

				// TODO: скорее всего излишняя операция для перестраховки
				aConnectionThread.clear();
				aConfirmationHashMap.clear();
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
								// TODO: обработка истечения времени ожидания подтверждения доставки.
							}
						}, 1000);
						*/					
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
					synchronized (aConnectionThread)
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
					syncCheckIncomingConfirmation(packet);
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
						transferToast("Ошибка: ложный идентификатор подтверждения доставки!");
					}
				}
				else
				{
					transferToast("Ошибка: неожиданное соединение ложно подтвердило доставку!");
				}
			}
		};

		@Override
		public void cancel()
		{
			// Прослушка остановлена: удалить это подключение из списков
			syncRemoveConnectedClient(this);

			super.cancel();
		}
	};
	
	
	/** Метод синхронизированного добавления клиентов в списки сервера. */
	private void syncAddConnectedClient(BluetoothSocket socket)
	{
		// TODO: возможно стоит подписывать потоки методом setName()
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

	// TODO: Вообще на данный момент Thread'ы занимаются самоликвидацией и
	// даже очищают от себя список Thread'ов и список ожидания o_0.
	// Вообще я сейчас задумался о том, чтобы перевести их на полное
	// самообслуживание через статические члены Communication...
	/** Метод синхронизированного удаления клиентов из списков сервера. */
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
					// Если есть другие ожидаемые подтверждения доставки
					if (!waitedPacketsConfirm.isEmpty())
					{
						confirmationEmpty = false;
						break;
					}
				}

				if (confirmationEmpty == true)
				{
					// Если нет ожидаемых подтверждений доставки
					transferResponseToUIThread(null, MessageCode.CONFIRMATION);
				}
			}
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
		broadcasting(null, new MessagePacket(MessageCode.MESSAGE, ++aLastOutputMsgNumber, msg));
	}

	@Override
	public void closeChatClient()
	{
		destroyServer();
	}
}