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
import android.widget.Toast;

abstract class ChatService extends Service implements IChatClient
{
	/** Список Thread'ов поднятых подключений. */
	protected final ArrayList<ConnectionThread> aConnectionThread = new ArrayList<ConnectionThread>();

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

	// This class is thread-safe: multiple threads can share a single Timer
	// object without the need for external synchronization.
	private final Timer aConfirmationTimer = new Timer();

	// Блок взаимодействия с Thread'ами подключений.
	/**
	 * Метод отправки сообщения сервером, рассылающий его всем клиентам, кроме
	 * отправителя.
	 */
	protected final void broadcasting(final Thread sender, final MessagePacket packet)
	{
		synchronized (aConnectionThread)
		{
			for (final ConnectionThread thread : aConnectionThread)
			{
				if (thread != sender)
				{
					synchronized (aConfirmationMap)
					{
						aConfirmationMap.get(thread).add(packet);
					}
					transferResponseToUIThread(GUIRequestCode._BLOCK, null);

					// Добавление заданий проверки доставки
					aConfirmationTimer.schedule(new TimerTask()
					{
						private int retrys = 5;

						@Override
						public void run()
						{
							// Обработка истечения времени ожидания
							// подтверждения доставки.
							boolean isNotConfirmed;
							synchronized (aConfirmationMap)
							{
								isNotConfirmed = aConfirmationMap.get(thread).contains(packet);
							}

							if (isNotConfirmed == true)
							{
								if (retrys > 0)
								{
									transferToast("Переотправка... =[");
									try
									{
										thread.syncWrite(packet.bytes);
									}
									catch (IOException e)
									{
										transferToast("Ошибка переотправки: " + e.getMessage());
									}
									--retrys;
								}
								else
								{
									// Число попыток повторной отправки
									// превышает
									// устоновленный лимит
									transferToast("Превышено число попыток переотправки!");
									thread.cancel();

									this.cancel();
								}
							}
							else
							{
								this.cancel();
							}
						}

					}, 2000, 2000);

					try
					{
						thread.syncWrite(packet.bytes);
					}
					catch (IOException e)
					{
						transferToast("Ошибка итерации broadcasting(): " + e.getMessage());
					}
				}
			}
		}
	}

	/** Класс потоков сервера, принимающих сообщения подключенных клиентов. */
	private final class ConnectionThread extends SocketThread
	{
		public ConnectionThread(BluetoothSocket socket) throws IOException
		{
			super(socket);

			final ArrayList<byte[]> bPackets = new ArrayList<byte[]>();
			// Формирование своего HELLO-пакета
			bPackets.add(new MessagePacket(MessageCode.__HELLO, aLastOutputMsgNumber).bytes);

			// Формирование суррогатных HELLO-пакетов
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
					try
					{
						syncWrite(new MessagePacket(MessageCode.__CONFIRMATION, packet.id).bytes);
					}
					catch (IOException e)
					{
						transferToast("Ошибка отправки подтверждения: " + e.getMessage());
						break;
					}

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
					transferResponseToUIThread(GUIRequestCode._MESSAGE, packet.message);

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
				case __PING:
				{
					try
					{
						syncWrite(new MessagePacket(MessageCode.__PONG).bytes);
					}
					catch (IOException e)
					{
						transferToast("Ошибка отправки PONG: " + e.getMessage());
						break;
					}
				}
				case __PONG:
				{
					
				}
				case __GOODBYE:
				{
					syncGoodbye(packet);
					broadcasting(this, packet);
					transferToast("Клиент __GOODBYE!");
					break;
				}
				default:
					transferToast("Ошибка: неопределённый тип сообщения!");
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
						transferResponseToUIThread(GUIRequestCode._UNBLOCK, null);
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

		/** Расширение функции, учитывающее реализацию протокола обмена. */
		@Override
		protected void cancel()
		{
			// Прослушка остановлена: удалить это подключение из списков
			broadcasting(this, new MessagePacket(MessageCode.__GOODBYE, ++aLastOutputMsgNumber));
			syncRemoveConnectedClient(this);

			try
			{
				super.cancel();
				transferToast("Thread передачи данных успешно остановлен!");
			}
			catch (IOException e)
			{
				transferToast("Ошибка остановки SocketThread: " + e.getMessage());
			}
		}
	};

	/** Метод синхронизированного добавления клиентов в списки сервера. */
	protected final void syncAddConnectedClient(BluetoothSocket socket) throws IOException
	{
		// TODO: возможно стоит подписывать потоки методом setName()? Например в
		// нём можно хранить имя пользователя-отправителя.
		ConnectionThread thread = new ConnectionThread(socket);

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
					transferResponseToUIThread(GUIRequestCode._UNBLOCK, null);
				}
			}
		}
	}

	// Блок взаимодействия с GUI.
	/** Messenger взаимодействия с GUI. */
	protected Messenger aMessenger;

	// TODO: Нужно ли его синхронизировать из-за доступа к общему Messenger'у?
	/** Отправка запроса обработчику Messenger'а в Thread'е UI. */
	protected final synchronized void transferResponseToUIThread(GUIRequestCode code, String str)
	{
		if (aMessenger != null)
			try
			{
				Message msg = Message.obtain(null, code.getId(), 0, 0);
				msg.obj = str;
				aMessenger.send(msg);
			}
			catch (RemoteException e)
			{
				// TODO: пока нет извещений об исключениях Messenger'а
			}
		else
		{
			// TODO: пока нет извещений об исключениях Messenger'а
		}
	}

	/** Формирование запроса на вывод Toast в Thread'е UI. */
	protected final void transferToast(String str)
	{
		transferResponseToUIThread(GUIRequestCode._TOAST, "st: " + str);
	}

	// TODO: нет гарантий вызова метода при аварийной отвязке
	public void closeChatClient()
	{
		for (ConnectionThread thread : aConnectionThread)
			thread.cancel();

		aMessenger = null;

		aConfirmationTimer.cancel();

		final int tasksCount = aConfirmationTimer.purge();
		if (tasksCount > 0)
			Toast.makeText(getBaseContext(), "Заданий таймера подтверждения удалено: " + tasksCount, Toast.LENGTH_LONG)
					.show();
	}
}