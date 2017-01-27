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

public class ChatClientService extends ChatService {
    
    /** Внешний целевой BluetoothDevice-сервер (null для самого сервера). */
    private BluetoothDevice mMasterDevice;
    
    private final IBinder mBinder = new LocalBinder();
    
    public class LocalBinder extends Binder {
	public ChatClientService getService() {
	    return ChatClientService.this;
	}
    };

    @Override
    public IBinder onBind(Intent intent) {
	return mBinder;
    };

    @Override
    public void onCreate() {
	super.onCreate();
	Toast.makeText(getBaseContext(), "ChatClientService onCreate()", Toast.LENGTH_SHORT).show();
    };

    @Override
    public void onDestroy() {
	super.onDestroy();
	Toast.makeText(getBaseContext(), "ChatClientService onDestroy()", Toast.LENGTH_SHORT).show();
    };

    public void setMasterDevice(BluetoothDevice mDevice) {
	mMasterDevice = mDevice;
    };

    // Client-реализация IChatClient для ChatActivity
    /** Обновляет Messanger связи с UI. Возвращает false, если аргумент null. */
    @Override
    public boolean updateMessenger(Messenger selectedMessenger) {
	return super.updateMessanger(selectedMessenger);
    };

    /**
     * Client-реализация одного из методов интерфейса IChatClient, отвечающего
     * за создание потока связи с приложением, запущенном в режиме Server.
     * Возврат false закрывает ChatActivity при попытке подключения к
     * ChatClientService.
     */
    @Override
    public boolean startConnection(Messenger selectedMessenger) {
	if (super.startConnection(selectedMessenger)) {
	    try {
		UUID myid = UUID.fromString(getResources().getString(R.string.service_uuid));

		final BluetoothSocket serverSocket = mMasterDevice.createRfcommSocketToServiceRecord(myid);
		serverSocket.connect();
		syncAddConnectionSocket(serverSocket);
		return true;
	    } catch (IOException e) {
		// Основная ошибка здесь: попытка подключения к устройству, на
		// котором не поднят нужный сервис Service Discovery Protocol
		// (SDP)
		Toast.makeText(getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		return false;
	    }
	} else {
	    return false;
	}
    };

    @Override
    public void sendResponse(String msg) {
	// Формирование сообщения согласно выбранному протоколу
	broadcasting(null, new MessagePacket(MessageCode.__TEXT, ++aLastOutputMsgNumber, msg));
    };

    @Override
    public void stopConnection() {
	mMasterDevice = null;
	super.stopConnection();
    };
}