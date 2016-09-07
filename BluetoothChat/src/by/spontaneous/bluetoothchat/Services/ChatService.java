package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.bluetooth.BluetoothSocket;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

abstract class ChatService extends Service implements IChatClient
{
	//protected final static String SERVICE_UUID = "565b2b42-b215-4717-a10b-f61bd445001f";
	//protected final static String SERVICE_NAME = "Testing Server";
	
	
	/** Список Thread'ов поднятых подключений. */
	protected final ArrayList<SocketThread> aConnectionThread = new ArrayList<SocketThread>();

	/** ID последнего исходящего сообщения. */
	protected volatile int aLastOutputMsgNumber = 0;

	/**
	 * Хэш-карта ожидаемых подтверждений доставки сообщений (поток, список
	 * пакетов). Опустошение этого списка может служить признаком разрешения
	 * отправки новых исходящих сообщений (в этой реализации программы дело
	 * обстоит именно таким образом).
	 */
	protected final HashMap<Thread, ArrayList<MessagePacket>> aConfirmationMap = new HashMap<Thread, ArrayList<MessagePacket>>();
	protected final HashMap<String, Integer> aLastInputMsgNumberMap = new HashMap<String, Integer>();

	private final Timer aConfirmationTimer = new Timer();

	// Блок взаимодействия с Thread'ами подключений.
	/**
	 * Метод отправки сообщения сервером, рассылающий его всем клиентам, кроме
	 * отправителя.
	 */
	protected final void broadcasting(Thread sender, MessagePacket packet)
	{
		synchronized (aConnectionThread)
		{
			for (SocketThread thread : aConnectionThread)
			{
				if (thread != sender)
				{
					synchronized (aConfirmationMap)
					{
						aConfirmationMap.get(thread).add(packet);
					}
					transferResponseToUIThread(null, GUIRequestCode._BLOCK);

					/*
					// Добавление заданий проверки доставки
					aConfirmationTimer.schedule(new TimerTask()
					{						
						public void run()
						{
							// TODO: обработка истечения времени ожидания
							// подтверждения доставки.
							synchronized (aConfirmationMap)
							{
								// Нет доступа к локальным переменным!
								if(aConfirmationMap.get(thread).contains(packet))
								{									
								}								
							}
						}
					}, 1000);
					*/

					thread.syncWrite(packet.bytes);
				}
			}
		}
	}

	/** Класс потоков сервера, принимающих сообщения подключенных клиентов. */
	private final class ConnectionThread extends SocketThread
	{
		public ConnectionThread(BluetoothSocket socket)
		{
			super(socket);

			final ArrayList<byte[]> bPackets = new ArrayList<byte[]>();
			// Знакомство нового клиента с сервером
			bPackets.add(new MessagePacket(MessageCode.__HELLO, aLastOutputMsgNumber, null).bytes);

			// Знакомство нового клиента с клиентами, подключившимися ранее
			synchronized (aLastInputMsgNumberMap)
			{
				Collection<String> keys = aLastInputMsgNumberMap.keySet();
				for (String key : keys)
					bPackets.add(new MessagePacket(key, aLastInputMsgNumberMap.get(key)).bytes);
			}
			this.syncWriteSeries(bPackets);
		};

		/** Тело ConnectionThread (прослушка BluetoothSocket). */
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
					transferToast("Ошибка прослушки:" + e.getMessage());
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
				case __TEXT:
				{
					// Сразу нужно отправить подтверждение в любом случае
					syncWrite(new MessagePacket(MessageCode.__CONFIRMATION, packet.id, null).bytes);

					// TODO: вообще теоретически последовательность должна
					// быть непрерывна, за исключением случая, когда клиент
					// подключается к серверу, на котором до этого
					// происходил обмен сообщениями

					final int lastInputMsgID = aLastInputMsgNumberMap.get(packet.getSenderAddressString());
					if (packet.id <= lastInputMsgID)
					{
						transferToast("Ошибка: получен дубликат сообщения!");
						continue;
					}
					else if (packet.id > lastInputMsgID + 1)
					{
						transferToast("Предупреждение: пропуск в последовательности сообщений!");
					}
					transferResponseToUIThread(packet.message, GUIRequestCode._MESSAGE);

					// Отправить всем подключениям, кроме отправителя
					broadcasting(this, packet);
					aLastInputMsgNumberMap.put(packet.getSenderAddressString(), packet.id);
					break;
				}
				case __CONFIRMATION:
				{
					syncCheckIncomingConfirmation(packet);
					break;
				}
				case __HELLO:
				{
					syncHello(packet);
					broadcasting(this, packet);
					break;
				}
				case __GOODBYE:
				{
					syncGoodbye(packet);
					broadcasting(this, packet);
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
			synchronized (aConfirmationMap)
			{
				if (aConfirmationMap.containsKey(this))
				{
					boolean continueWaiting = false;
					boolean packetFound = false;
					for (MessagePacket mp : aConfirmationMap.get(this))
					{
						if (packet.id == mp.id)
						{
							aConfirmationMap.get(this).remove(mp);
							packetFound = true;
						}
						else
						{
							continueWaiting = true;
						}
					}

					if (!continueWaiting)
					{
						transferResponseToUIThread(null, GUIRequestCode._UNBLOCK);
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

		private final void syncHello(MessagePacket pack)
		{
			final String senderAddress = pack.getSenderAddressString();
			synchronized (aLastInputMsgNumberMap)
			{
				if (aLastInputMsgNumberMap.containsKey(senderAddress))
					transferToast("Предупреждение: получен повторный Hello!");
				aLastInputMsgNumberMap.put(pack.getSenderAddressString(), pack.id);
			}
		}

		private final void syncGoodbye(MessagePacket pack)
		{
			final String senderAddress = pack.getSenderAddressString();
			synchronized (aLastInputMsgNumberMap)
			{
				if (aLastInputMsgNumberMap.containsKey(senderAddress))
					aLastInputMsgNumberMap.remove(senderAddress);
			}
		}

		@Override
		protected void cancel()
		{
			// Прослушка остановлена: удалить это подключение из списков
			broadcasting(this, new MessagePacket(MessageCode.__GOODBYE, ++aLastOutputMsgNumber, null));
			syncRemoveConnectedClient(this);

			super.cancel();
			transferToast("Thread передачи данных успешно остановлен!");
		}
	};

	/** Метод синхронизированного добавления клиентов в списки сервера. */
	protected final void syncAddConnectedClient(BluetoothSocket socket)
	{
		// TODO: возможно стоит подписывать потоки методом setName()
		final SocketThread thread = new ConnectionThread(socket);

		synchronized (aConnectionThread)
		{
			aConnectionThread.add(thread);
		}

		synchronized (aConfirmationMap)
		{
			aConfirmationMap.put(thread, new ArrayList<MessagePacket>());
		}

		thread.setDaemon(true);
		thread.start();
	}

	/** Метод синхронизированного удаления клиентов из списков сервера. */
	private final void syncRemoveConnectedClient(SocketThread targetThread)
	{
		synchronized (aConnectionThread)
		{
			aConnectionThread.remove(targetThread);
		}

		synchronized (aConfirmationMap)
		{
			if (aConfirmationMap.containsKey(targetThread))
			{
				aConfirmationMap.remove(targetThread);

				boolean confirmationEmpty = true;
				for (ArrayList<MessagePacket> waitedPacketsConfirm : aConfirmationMap.values())
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
					transferResponseToUIThread(null, GUIRequestCode._UNBLOCK);
				}
			}
		}
	}

	// Блок взаимодействия с GUI.
	/** Messenger взаимодействия с GUI. */
	protected Messenger aMessenger;

	/** Отправка запроса обработчику Messenger'а в Thread'е UI. */
	protected final synchronized void transferResponseToUIThread(String str, GUIRequestCode code)
	{
		try
		{
			Message msg = Message.obtain(null, code.getId(), 0, 0);
			msg.obj = str;
			aMessenger.send(msg);
		}
		catch (RemoteException e)
		{
		}
	}

	/** Формирование запроса на вывод Toast в Thread'е UI. */
	protected final void transferToast(String str)
	{
		transferResponseToUIThread(str, GUIRequestCode._TOAST);
	}
}
