package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;
import by.spontaneous.bluetoothchat.R;

public class ChatServerService extends ChatService {
 
    private final IBinder mBinder = new LocalBinder();
    
    /** Thread серверного ожидания новых клиентов. */
    private AcceptThread mAcceptThread;
    
    public class LocalBinder extends Binder {
	public ChatServerService getService() {
	    return ChatServerService.this;
	}
    };
    
    @Override
    public IBinder onBind(Intent intent) {
	return mBinder;
    };

    @Override
    public void onCreate() {
	super.onCreate();
	Toast.makeText(getBaseContext(), "ChatServerService onCreate()", Toast.LENGTH_SHORT).show();
    };

    @Override
    public void onDestroy() {
	super.onDestroy();
	destroyAcceptThread();
	Toast.makeText(getBaseContext(), "ChatServerService onDestroy()", Toast.LENGTH_SHORT).show();
    };

    /** Класс потока сервера, составляющего список входящих подключений. */
    private class AcceptThread extends Thread {
	private final BluetoothServerSocket tServerSocket;

	public AcceptThread(BluetoothServerSocket socket) {
	    tServerSocket = socket;
	};

	public void run() {
	    while (true) {
		transferToast("Ожидание нового клиента...");
		try {
		    final BluetoothSocket clientSocket = tServerSocket.accept();
		    syncAddConnectionSocket(clientSocket);
		} catch (IOException e) {
		    // Основной выход из цикла ожидания (ServerSocket был
		    // закрыт?)
		    if (e.getMessage() == "[JSR82] accept: Connection is not created (failed or aborted).")
			return;

		    // Непредвиденный выход из цикла ожидания...
		    transferToast(e.getMessage());
		    break;
		}
		transferToast("Подключен новый клиент!");
	    }
	    transferToast("Ошибка: ожидание новых клиентов аварийно прекращено!");
	    transferResponseToUIThread(GUIRequestCode._QUIT, null);
	    this.cancel();
	};

	/** Закрывает ServerSocket бесконечной прослушки. */
	private void cancel() {
	    try {
		tServerSocket.close();
	    } catch (IOException e) {
		transferToast("Ошибка ServerSocket.cancel(): " + e.getMessage());
	    }
	    transferResponseToUIThread(GUIRequestCode._QUIT, null);
	    transferCut(this);
	};
    };

    /** Метод инициализации пары сокет-нить прослушки подключений сервера. */
    private boolean createAcceptThread() {
	this.destroyAcceptThread();

	BluetoothServerSocket acceptServerSocket;
	try {
	    final BluetoothAdapter bAdapter = BluetoothAdapter.getDefaultAdapter();

	    acceptServerSocket = bAdapter.listenUsingRfcommWithServiceRecord(
		    getResources().getString(R.string.service_name),
		    UUID.fromString(getResources().getString(R.string.service_uuid)));
	} catch (IOException e) {
	    Toast.makeText(getBaseContext(), "Ошибка создания AcceptThread: " + e.getMessage(), Toast.LENGTH_LONG)
		    .show();
	    transferResponseToUIThread(GUIRequestCode._QUIT, null);
	    return false;
	}

	mAcceptThread = new AcceptThread(acceptServerSocket);
	mAcceptThread.setDaemon(true);
	mAcceptThread.start();

	return true;
    };

    /**
     * Метод уничтожения Accept-пары Сокет-Нить прослушки подключений сервера.
     */
    private void destroyAcceptThread() {
	// Остановить нить приёма новых клиентов
	if (mAcceptThread != null) {
	    mAcceptThread.cancel();
	    mAcceptThread = null;

	    Toast.makeText(getBaseContext(), "AcceptThread успешно завершён!", Toast.LENGTH_SHORT).show();
	}
    };

    // Server-реализация IChatClient для ChatActivity
    /** Обновляет Messanger связи с UI. Возвращает false, если аргумент null. */
    @Override
    public boolean updateMessenger(Messenger selectedMessenger) {
	return super.updateMessanger(selectedMessenger);
    };

    /**
     * Server-реализация одного из методов интерфейса IChatClient, отвечающего
     * за создание потока связи с приложением, запущенном в режиме Server.
     * Возврат false закрывает ChatActivity при попытке подключения к
     * ChatClientService.
     */
    @Override
    public boolean startConnection(Messenger selectedMessenger) {
	if (super.startConnection(selectedMessenger)) {
	    // Попытка запустить сервер
	    return createAcceptThread();
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
	destroyAcceptThread();
	super.stopConnection();
    };
}