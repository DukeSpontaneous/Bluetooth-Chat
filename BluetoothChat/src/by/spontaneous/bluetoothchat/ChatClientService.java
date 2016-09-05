package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;

public class ChatClientService extends ChatService
{
	public class LocalBinder extends Binder
	{
		ChatClientService getService()
		{
			return ChatClientService.this;
		}
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}	

	/** Thread клиентского ожидания новых сообщений от сервера. */
	private Communication mConnectedThread;

	/** Внешний целевой BluetoothDevice-сервер (null для самого сервера). */
	private BluetoothDevice mMasterDevice;

	@Override
	public void onCreate()
	{
		super.onCreate();
		Toast.makeText(getBaseContext(), "ChatClientService onCreate()", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		disconnect();
		Toast.makeText(getBaseContext(), "ChatClientService onDestroy()", Toast.LENGTH_LONG).show();
	}

	// TODO: по идее это должен быть метод приведения к состоянию...
	/** Обрыв Thread'а ChatClientService и обунление мессенджера. */
	public void disconnect()
	{
		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;

			aMessenger = null;
		}
	}

	public void setMasterDevice(BluetoothDevice mDevice)
	{
		mMasterDevice = mDevice;
	}

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

	/** Класс потока клиента, принимающий сообщения подключенного сервера. */
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
			
			transferResponseToUIThread(null, MessageCode.QUIT);
		};
	}

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
	
	// Client-реализация IChatClient для ChatActivity
	/**
	 * Client-реализация одного из методов интерфейса IChatClient, отвечающего
	 * за создание потока связи с приложением, запущенном в режиме Server.
	 * Возврат false закрывает ChatActivity при попытке подключения к
	 * ChatClientService.
	 */
	@Override
	public boolean connectToServer(Messenger selectedMessenger)
	{
		disconnect();

		if (selectedMessenger != null)
		{
			// Socket связи с mMasterDevice (сервером).
			BluetoothSocket serverSocket;

			aMessenger = selectedMessenger;

			try
			{
				// MY_UUID is the app's UUID string, also used by the server
				// code

				UUID myid = UUID.fromString(getResources().getString(R.string.service_uuid));
				/*
				 * ParcelUuid[] uuids = device.getUuids();
				 * 
				 * boolean is_contained = false;
				 * 
				 * for (ParcelUuid uuid : uuids) {
				 * //Toast.makeText(getBaseContext(), uuid.getUuid().toString(),
				 * Toast.LENGTH_LONG).show(); if (uuid.getUuid() == myid) {
				 * is_contained = true; break; } }
				 * 
				 * if (is_contained == false) { Toast.makeText(getBaseContext(),
				 * "SDP-сервис не найден!", Toast.LENGTH_LONG).show(); return
				 * false; }
				 */
				serverSocket = mMasterDevice.createRfcommSocketToServiceRecord(myid);
				serverSocket.connect();
			}
			catch (IOException e)
			{
				// Основная ошибка здесь: попытка подключения к устройству, на
				// котором не поднят нужный сервис Service Discovery Protocol
				// (SDP)
				Toast.makeText(getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				// disconnect();
				return false;
			}

			syncAddConnectedClient(serverSocket);
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
		disconnect();
	}
}