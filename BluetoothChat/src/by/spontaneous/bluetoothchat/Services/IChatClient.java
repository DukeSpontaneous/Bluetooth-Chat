package by.spontaneous.bluetoothchat.Services;

import android.os.Messenger;

/**
 * Интерфейс взаимодействия с сервисами для ChatActivity, подразумевающий
 * возможность его серверной, или клиентской реализации.
 */
public interface IChatClient
{
	/**
	 * Интерфейс создания потока связи с приложением, запущенном в режиме
	 * Server. Возврат false интерпретируется как необходимость закрыть
	 * ChatActivity при попытке подключения к ChatClientService.
	 */
	public boolean connectToServer(Messenger handler);

	/** Интерфейс отправки сообщения из EditText (поле ввода в ChatActivity). */
	public void sendResponse(String msg);

	/** Интерфейс реализации завершения работы ChatActivity. */
	public void closeChatClient();
}
