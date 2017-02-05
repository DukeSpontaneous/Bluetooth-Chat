package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

public abstract class ChatService<T extends ChatService<T>> extends Service implements IChatClient {
        
    private final IBinder mBinder = new LocalBinder();
    
    public class LocalBinder extends Binder {
	@SuppressWarnings("unchecked")
	public T getService() {
	    return (T) T.this;
	}
    };
    
    public IBinder onBind(Intent intent) {
	return mBinder;
    };
    
    
    /** Список Thread'ов поднятых подключений. */
    protected final ArrayList<ConnectionThread> aConnectionThread = new ArrayList<ConnectionThread>();

    /**
     * Хэш-карта ожидаемых подтверждений доставки сообщений (поток, список
     * пакетов). Опустошение этого списка может служить признаком разрешения
     * отправки новых исходящих сообщений (в этой реализации программы дело
     * обстоит именно таким образом).
     */
    protected final HashMap<Thread, ArrayList<MessagePacket>> aConfirmationMap = new HashMap<Thread, ArrayList<MessagePacket>>();
    protected final HashMap<String, Integer> aLastInputMsgNumberMap = new HashMap<String, Integer>();

    /** Messenger взаимодействия с GUI. */
    protected volatile Messenger aMessenger;
    
    /** ID последнего исходящего сообщения. */
    protected volatile int aLastOutputMsgNumber = 0;    
    
    // This class is thread-safe: multiple threads can share a single Timer
    // object without the need for external synchronization.

    private Timer aConfirmationTimer;      

    // Блок взаимодействия с Thread'ами подключений.
    /**
     * Метод отправки сообщения сервером, рассылающий его всем клиентам, кроме
     * отправителя.
     */
    protected void broadcasting(final Thread sender, final MessagePacket packet) {
	synchronized (aConnectionThread) {
	    for (final ConnectionThread thread : aConnectionThread) {
		if (thread != sender) {
		    synchronized (aConfirmationMap) {
			aConfirmationMap.get(thread).add(packet);
		    }
		    transferResponseToUIThread(GUIRequestCode._BLOCK, null);

		    // Добавление заданий проверки доставки
		    aConfirmationTimer.schedule(new TimerTask() {
			private int retrys = 3;

			@Override
			public void run() {
			    // Обработка истечения времени ожидания
			    // подтверждения доставки.
			    boolean isNotConfirmed;
			    synchronized (aConfirmationMap) {
				isNotConfirmed = aConfirmationMap.get(thread).contains(packet);
			    }

			    if (isNotConfirmed == true) {
				if (retrys > 0) {
				    transferToast("Переотправка... =[");
				    try {
					thread.syncWrite(packet.bytes);
				    } catch (IOException e) {
					transferToast("Ошибка переотправки: " + e.getMessage());
				    }
				    --retrys;
				} else {
				    // Число попыток повторной отправки
				    // превышает устоновленный лимит
				    transferToast("Превышено число попыток переотправки!");

				    // Отмена задания
				    this.cancel();
				}
			    } else {
				this.cancel();
			    }
			};

		    }, 2000, 3000);

		    try {
			thread.syncWrite(packet.bytes);
		    } catch (IOException e) {
			transferToast("Ошибка итерации broadcasting(): " + e.getMessage());
		    }
		}
	    }
	}
    };

    /** Класс потоков сервера, принимающих сообщения подключенных клиентов. */
    private class ConnectionThread extends SocketThread {
	private long tLastTimePING;

	public ConnectionThread(BluetoothSocket socket) throws IOException {
	    super(socket);

	    final ArrayList<byte[]> bPackets = new ArrayList<byte[]>();
	    // Формирование своего HELLO-пакета
	    bPackets.add(new MessagePacket(MessageCode.__HELLO, aLastOutputMsgNumber).bytes);

	    // Формирование суррогатных HELLO-пакетов
	    synchronized (aLastInputMsgNumberMap) {
		Collection<String> keys = aLastInputMsgNumberMap.keySet();
		for (String key : keys)
		    bPackets.add(new MessagePacket(key, aLastInputMsgNumberMap.get(key)).bytes);
	    }
	    this.syncWriteSeries(bPackets);

	    // Задание PING'ования
	    aConfirmationTimer.schedule(new TimerTask() {
		@Override
		public void run() {
		    tLastTimePING = new Date().getTime();
		    try {
			syncWrite(new MessagePacket(tLastTimePING).bytes);
		    } catch (IOException e) {
			this.cancel();
		    }
		}

	    }, 5000, 5000);
	};

	/** Тело ConnectionThread (прослушка BluetoothSocket). */
	@Override
	public void run() {
	    // TODO: Размер, конечно, под вопросом...
	    final byte[] buffer = new byte[256];
	    int bCount;

	    // Цикл прослушки
	    while (true) {
		try {
		    bCount = tInStream.read(buffer);
		} catch (IOException e) {
		    transferToast("Ошибка прослушки:" + e.getMessage());

		    this.goodbyeSocketClose();
		    return;
		}

		byte[] bPacket = Arrays.copyOfRange(buffer, 0, bCount);

		// Пакет придуманного прикладного протокола
		final MessagePacket packet = new MessagePacket(bPacket);

		if (!packet.checkHash()) {
		    transferToast("Ошибка: полученное сообщение повреждено!");
		    continue;
		}

		switch (packet.code) {
		case __PING: {
		    try {
			syncWrite(new MessagePacket(packet.message).bytes);
		    } catch (IOException e) {
			transferToast("Ошибка отправки PONG: " + e.getMessage());
		    }
		    break;
		}
		case __PONG: {
		    long time = Long.parseLong(packet.message, Character.MAX_RADIX);
		    if (time == tLastTimePING) {
			transferToast("PING: " + (new Date().getTime() - tLastTimePING) + " ms");
		    } else {
			transferToast("Неожиданный PONG!");
		    }
		    break;
		}
		case __TEXT: {
		    // Сразу нужно отправить подтверждение в любом случае
		    try {
			syncWrite(new MessagePacket(MessageCode.__CONFIRMATION, packet.id).bytes);
		    } catch (IOException e) {
			transferToast("Ошибка отправки подтверждения: " + e.getMessage());
		    }

		    // TODO: вообще теоретически последовательность должна
		    // быть непрерывна, за исключением случая, когда клиент
		    // подключается к серверу, на котором до этого
		    // происходил обмен сообщениями

		    final int lastInputMsgID = aLastInputMsgNumberMap.get(packet.getSenderAddressString());
		    if (packet.id <= lastInputMsgID) {
			transferToast("Ошибка: получен дубликат сообщения!");
			continue;
		    } else if (packet.id > lastInputMsgID + 1) {
			transferToast("Предупреждение: пропуск в последовательности сообщений!");
		    }
		    transferResponseToUIThread(GUIRequestCode._MESSAGE, packet.message);

		    // Отправить всем подключениям, кроме отправителя
		    broadcasting(this, packet);
		    aLastInputMsgNumberMap.put(packet.getSenderAddressString(), packet.id);
		    break;
		}
		case __CONFIRMATION: {
		    syncCheckIncomingConfirmation(packet);
		    break;
		}
		case __HELLO: {
		    transferToast("Пришёл HELLO!");
		    syncIncomingHello(packet);
		    broadcasting(this, packet);

		    break;
		}
		case __GOODBYE: {
		    transferToast("Пришёл GOODBYE!");
		    syncIncomingGoodbye(packet);
		    broadcasting(this, packet);

		    break;
		}
		default:
		    transferToast("Ошибка: неопределённый тип сообщения!");
		    break;
		}
	    }
	    // Конец бесконечного цикла прослушки
	};

	private void syncCheckIncomingConfirmation(MessagePacket packet) {
	    synchronized (aConfirmationMap) {
		if (aConfirmationMap.containsKey(this)) {
		    boolean continueWaiting = false;
		    boolean packetFound = false;
		    for (MessagePacket mp : aConfirmationMap.get(this)) {
			if (packet.id == mp.id) {
			    aConfirmationMap.get(this).remove(mp);
			    packetFound = true;
			} else {
			    continueWaiting = true;
			}
		    }

		    if (!continueWaiting) {
			transferResponseToUIThread(GUIRequestCode._UNBLOCK, null);
		    }

		    if (!packetFound) {
			transferToast("Ошибка: ложный идентификатор подтверждения доставки!");
		    }
		} else {
		    transferToast("Ошибка: неожиданное соединение ложно подтвердило доставку!");
		}
	    }
	};

	private void syncIncomingHello(MessagePacket pack) {
	    final String senderAddress = pack.getSenderAddressString();
	    synchronized (aLastInputMsgNumberMap) {
		if (aLastInputMsgNumberMap.containsKey(senderAddress))
		    transferToast("Предупреждение: получен повторный HELLO!");
		aLastInputMsgNumberMap.put(pack.getSenderAddressString(), pack.id);
	    }
	};

	private void syncIncomingGoodbye(MessagePacket pack) {
	    final String senderAddress = pack.getSenderAddressString();
	    synchronized (aLastInputMsgNumberMap) {
		if (aLastInputMsgNumberMap.containsKey(senderAddress))
		    aLastInputMsgNumberMap.remove(senderAddress);
		else
		    transferToast("Предупреждение: GOODBYE прислал незнакомец!");
	    }
	};

	/** Расширение функции, учитывающее реализацию протокола обмена. */
	protected void goodbyeSocketClose() {
	    try {
		syncWrite(new MessagePacket(MessageCode.__GOODBYE, ++aLastOutputMsgNumber).bytes);
	    } catch (IOException e) {
		transferToast("Ошибка отправки GOODBYE при завершении SocketThread: " + e.getMessage());
	    }

	    try {
		super.close();
		transferToast("Thread передачи данных успешно остановлен!");
	    } catch (IOException e) {
		transferToast("Ошибка SocketThread.close(): " + e.getMessage());
	    }

	    // Прослушка остановлена: удалить это подключение из списков
	    syncRemoveConnectedClient(this);

	    transferCut(this);
	};

    }

    /** Метод синхронизированного добавления элемента в спискм подключений. */
    protected void syncAddConnectionSocket(BluetoothSocket socket) throws IOException {
	// TODO: возможно стоит подписывать потоки методом setName()? Например в
	// нём можно хранить имя пользователя-отправителя.
	final ConnectionThread thread = new ConnectionThread(socket);

	synchronized (aConnectionThread) {
	    aConnectionThread.add(thread);
	}

	synchronized (aConfirmationMap) {
	    aConfirmationMap.put(thread, new ArrayList<MessagePacket>());
	}

	thread.setDaemon(true);
	thread.start();
    };

    /** Метод синхронизированного удаления клиентов из списков сервера. */
    private void syncRemoveConnectedClient(SocketThread targetThread) {
	synchronized (aConnectionThread) {
	    aConnectionThread.remove(targetThread);
	}

	synchronized (aConfirmationMap) {
	    if (aConfirmationMap.containsKey(targetThread)) {
		aConfirmationMap.remove(targetThread);
		if (aConfirmationMap.isEmpty())
		    transferResponseToUIThread(GUIRequestCode._LONELINESS, null);

		boolean confirmationEmpty = true;
		for (ArrayList<MessagePacket> waitedPacketsConfirm : aConfirmationMap.values()) {
		    // Если есть другие ожидаемые подтверждения доставки
		    if (!waitedPacketsConfirm.isEmpty()) {
			confirmationEmpty = false;
			break;
		    }
		}
		// Если нет ожидаемых подтверждений доставки
		if (confirmationEmpty == true)
		    transferResponseToUIThread(GUIRequestCode._UNBLOCK, null);
	    }
	}
    };

    // Блок взаимодействия с GUI.    

    // TODO: Нужно ли его синхронизировать по aMessenger?
    /** Отправка запроса обработчику Messenger'а в Thread'е UI. */
    protected void transferResponseToUIThread(GUIRequestCode code, String str) {
	if (aMessenger != null) {
	    synchronized (aMessenger) {
		try {
		    Message msg = Message.obtain(null, code.getId(), 0, 0);
		    msg.obj = str;
		    aMessenger.send(msg);
		} catch (RemoteException e) {
		    // TODO: пока нет извещений об исключениях Messenger'а
		}
	    }
	} else {
	    // TODO: пока нет извещений об исключениях Messenger'а
	}
    };

    protected void transferCut(Thread thread) {
	if (aMessenger != null) {
	    synchronized (aMessenger) {
		try {
		    Message msg = Message.obtain(null, GUIRequestCode._CUT.getId(), 0, 0);
		    msg.obj = thread;
		    aMessenger.send(msg);
		} catch (RemoteException e) {
		    // TODO: пока нет извещений об исключениях Messenger'а
		}
	    }
	} else {
	    // TODO: пока нет извещений об исключениях Messenger'а
	}
    }

    /** Формирование запроса на вывод Toast в Thread'е UI. */
    protected void transferToast(String str) {
	transferResponseToUIThread(GUIRequestCode._TOAST, "st: " + str);
    };

    /** Обновляет Messanger связи с UI. Возвращает false, если аргумент null. */
    public boolean updateMessanger(Messenger selectedMessenger) {
	if (selectedMessenger != null) {
	    aMessenger = selectedMessenger;
	    return true;
	} else {
	    return false;
	}
    };

    /** Инициализирует новый Timer. */
    public boolean startConnection(Messenger selectedMessenger) {
	if (updateMessanger(selectedMessenger)) {
	    aConfirmationTimer = new Timer();
	    return true;
	} else {
	    return false;
	}
    }

    // TODO: сейчас вызывается финальным уничтожением Fragment'a подключения
    public void stopConnection() {
	Toast.makeText(getBaseContext(), "closeChatClient()!", Toast.LENGTH_SHORT).show();

	for (ConnectionThread thread : aConnectionThread)
	    thread.goodbyeSocketClose();

	// После этого Timer не пригоден для планировки!
	aConfirmationTimer.cancel();

	final int tasksCount = aConfirmationTimer.purge();
	if (tasksCount > 0)
	    Toast.makeText(getBaseContext(), "Заданий таймера подтверждения удалено: " + tasksCount, Toast.LENGTH_LONG)
		    .show();
	aConfirmationTimer = null;
    };
}