package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;
import by.spontaneous.bluetoothchat.R;

public final class ChatClientService extends ChatService
{
	public final class LocalBinder extends Binder
	{
		public final ChatClientService getService()
		{
			return ChatClientService.this;
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
		Toast.makeText(getBaseContext(), "ChatClientService onCreate()", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		Toast.makeText(getBaseContext(), "ChatClientService onDestroy()", Toast.LENGTH_LONG).show();
	}

	/** Внешний целевой BluetoothDevice-сервер (null для самого сервера). */
	private BluetoothDevice mMasterDevice;

	public void setMasterDevice(BluetoothDevice mDevice)
	{
		mMasterDevice = mDevice;
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
		if (selectedMessenger != null)
		{
			aMessenger = selectedMessenger;

			try
			{
				UUID myid = UUID.fromString(getResources().getString(R.string.service_uuid));
				
				final BluetoothSocket serverSocket = mMasterDevice.createRfcommSocketToServiceRecord(myid);
				serverSocket.connect();
				syncAddConnectedClient(serverSocket);
				return true;
			}
			catch (IOException e)
			{
				// Основная ошибка здесь: попытка подключения к устройству, на
				// котором не поднят нужный сервис Service Discovery Protocol
				// (SDP)
				Toast.makeText(getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				return false;
			}
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
	public void closeChatClient()
	{
		mMasterDevice = null;
		aMessenger = null;
	}
}