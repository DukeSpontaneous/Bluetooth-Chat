package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

public class ChatClientService extends Service implements IChatClient
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

	/** Messenger взаимодействия с GUI. */
	private Messenger mMessenger;

	/** Thread клиентского ожидания новых сообщений от сервера. */
	private SyntheticThread mConnectedThread;

	/** ID последнего исходящего сообщения. */
	private volatile int mLastOutputMsgNumber = 0;
	/** Содержание последнего исходящего сообщения. */
	private volatile String mConfirmationMessage;
	/** Список клиентов, не подтвердивших доставку последнего сообщения. */
	private volatile boolean mWaitingConfirmation = false;

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

			mMessenger = null;
		}
	}

	public void setMasterDevice(BluetoothDevice mDevice)
	{
		mMasterDevice = mDevice;
	}

	/**
	 * Пока этот метод создан просто для параллельности с ChatSercverService,
	 * где broadcast используется множественно.
	 */
	private void unicasting(MessagePacket packet)
	{
		// Отправленное сообщение
		mConfirmationMessage = packet.message;

		try
		{
			synchronized (mConnectedThread)
			{
				mConnectedThread.tOutStream.write(packet.bytes);
			}
			mWaitingConfirmation = true;
			transferResponseToUIThread(null, MessageCode.WAITING);
		}
		catch (IOException e)
		{
			// Это признак потери связи с сервером.
			Toast.makeText(getBaseContext(), "Stream.write IOException:" + e.getMessage(), Toast.LENGTH_LONG).show();
			transferResponseToUIThread(null, MessageCode.QUIT);
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
					transferToast("Ошибка прослушки сервера:" + e.getMessage());
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
					synchronized (mConnectedThread)
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
						transferToast("Ошибка Клиента: ID последнего принятого сообщения >= анализируемому!");
						continue;
					}

					transferResponseToUIThread(packet.message, packet.code);

					tLastInputMsgNumber = packet.id;
					break;
				}
				case CONFIRMATION:
				{
					if (packet.id == mLastOutputMsgNumber)
					{
						if (mWaitingConfirmation == true)
						{
							mWaitingConfirmation = false;
							transferResponseToUIThread(null, MessageCode.CONFIRMATION);
						}
						else
						{
							Toast.makeText(getBaseContext(), "Предупреждение: пришло не ожидаемое подтверждение!",
									Toast.LENGTH_LONG).show();
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
			super.cancel();
			
			transferResponseToUIThread(null, MessageCode.QUIT);
		};
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

			mMessenger = selectedMessenger;

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

			mConnectedThread = new SyntheticThread(serverSocket);
			mConnectedThread.setDaemon(true);
			mConnectedThread.start();

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
		unicasting(new MessagePacket(MessageCode.MESSAGE, ++mLastOutputMsgNumber, msg));
	}

	@Override
	public void closeChatClient()
	{
		disconnect();
	}
}