package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

	/** Socket связи с сервером. */
	private BluetoothSocket mServerSocket;

	/** Thread клиентского ожидания новых сообщений от сервера. */
	private ConnectedThread mConnectedThread;

	/** ID последнего исходящего сообщения. */
	private int mLastOutputMsgNumber = 0;
	/** Содержание последнего исходящего сообщения. */
	private String mConfirmationMessage;
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

			try
			{
				if (mServerSocket != null)
				{
					mServerSocket.close();
				}
			}
			catch (IOException e)
			{
				Toast.makeText(getBaseContext(), "Ошибка ChatClientService: " + e.getMessage(), Toast.LENGTH_LONG)
						.show();
			}

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
		// TODO: ожидать подтверждение нужно где-то тут... но тут
		// всплывает пробелма с широковещанием...
		// Серия неподтверждений будет расцениваться как потеря связи...

		// Отправленное сообщение
		mConfirmationMessage = packet.message;

		try
		{
			synchronized (mServerSocket)
			{
				final OutputStream outStream = mServerSocket.getOutputStream();
				outStream.write(packet.bytes);
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

	/**
	 * Класс потока клиента, принимающего сообщения от сервера, к которому он
	 * подключен.
	 */
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
				transferToast("Ошибка BluetoothSocket.get...Stream: " + e.getMessage());
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
					synchronized (mServerSocket)
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
					if(packet.id == mLastOutputMsgNumber)
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

			this.cancel();			
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel()
		{
			try
			{
				tSocket.close();
				transferToast("Соединение с сервером потеряно. Socket закрыт успешно.");
			}
			catch (IOException e)
			{
				transferToast("Закрытие Thread'а:  ошибка закрытия Socket'а сервера: " + e.getMessage());
			}
			transferResponseToUIThread(null, MessageCode.QUIT);
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
				mServerSocket = mMasterDevice.createRfcommSocketToServiceRecord(myid);
				mServerSocket.connect();
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

			mConnectedThread = new ConnectedThread(mServerSocket);
			mConnectedThread.setDaemon(true);
			mConnectedThread.start();

			return true;
		}
		else
		{
			Toast.makeText(getBaseContext(), "Ошибка ChatClientService: provider == null", Toast.LENGTH_LONG).show();
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
	public void close()
	{
		disconnect();
	}
}