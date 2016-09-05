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
	/** ������ Thread'�� �������� �����������. */
	protected final ArrayList<Communication> aConnectionThread = new ArrayList<Communication>();

	/** ID ���������� ���������� ���������. */
	protected volatile int aLastOutputMsgNumber = 0;

	/**
	 * ���-����� ��������� ������������� �������� ��������� (�����, ������
	 * �������). ����������� ����� ������ ����� ������� ��������� ����������
	 * �������� ����� ��������� ��������� (� ���� ���������� ��������� ����
	 * ������� ������ ����� �������).
	 */
	protected final HashMap<Communication, ArrayList<MessagePacket>> aConfirmationHashMap = new HashMap<Communication, ArrayList<MessagePacket>>();

	protected final Timer aConfirmationTimer = new Timer();
	
	/** Messenger �������������� � GUI. */
	protected Messenger aMessenger;

	/** �������� ������� ����������� Messenger'� � Thread'� UI. */
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

	/** ������������ ������� �� ����� Toast � Thread'� UI. */
	protected void transferToast(String str)
	{
		transferResponseToUIThread(str, MessageCode.TOAST);
	}
}
