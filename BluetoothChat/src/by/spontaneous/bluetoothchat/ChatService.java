package by.spontaneous.bluetoothchat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;

import android.app.Service;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

abstract class ChatService extends Service implements IChatClient
{
	/** Список Thread'ов поднятых подключений. */
	protected final ArrayList<Communication> aConnectionThread = new ArrayList<Communication>();

	/** ID последнего исходящего сообщения. */
	protected volatile int aLastOutputMsgNumber = 0;

	/**
	 * Хэш-карта ожидаемых подтверждений доставки сообщений (поток, список
	 * пакетов). Опустошение этого списка может служить признаком разрешения
	 * отправки новых исходящих сообщений (в этой реализации программы дело
	 * обстоит именно таким образом).
	 */
	protected final HashMap<Communication, ArrayList<MessagePacket>> aConfirmationHashMap = new HashMap<Communication, ArrayList<MessagePacket>>();

	protected final Timer aConfirmationTimer = new Timer();
	
	/** Messenger взаимодействия с GUI. */
	protected Messenger aMessenger;

	/** Отправка запроса обработчику Messenger'а в Thread'е UI. */
	protected synchronized void transferResponseToUIThread(String str, MessageCode code)
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
	protected void transferToast(String str)
	{
		transferResponseToUIThread(str, MessageCode.TOAST);
	}
}
