package by.spontaneous.bluetoothchat.Services;

import android.os.Messenger;

/**
 * ��������� �������������� � ��������� ��� ChatActivity, ���������������
 * ����������� ��� ���������, ��� ���������� ����������.
 */
public interface IChatClient
{
	/**
	 * ��������� �������� ������ ����� � �����������, ���������� � ������
	 * Server. ������� false ���������������� ��� ������������� �������
	 * ChatActivity ��� ������� ����������� � ChatClientService.
	 */
	public boolean connectToServer(Messenger handler);

	/** ��������� �������� ��������� �� EditText (���� ����� � ChatActivity). */
	public void sendResponse(String msg);

	/** ��������� ���������� ���������� ������ ChatActivity. */
	public void closeChatClient();
}
