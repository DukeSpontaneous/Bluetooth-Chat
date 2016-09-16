package by.spontaneous.bluetoothchat.Services;

import android.os.Messenger;

/**
 * ��������� �������������� � ��������� ��� ChatActivity, ���������������
 * ����������� ��� ���������, ��� ���������� ����������.
 */
public interface IChatClient
{
	/**
	 * ��������� ���������� Messenger'� ����� � UI. ���������� false, ����
	 * selectedMessenger == null.
	 */
	public boolean updateMessenger(Messenger selectedMessenger);

	/**
	 * ��������� �������� ������ ����� � �����������, ���������� � ������
	 * Server. ������� false ���������������� ��� ������������� �������
	 * ChatActivity ��� ������� ����������� � ChatClientService.
	 */
	public boolean startConnection(Messenger handler);

	/** ��������� �������� ��������� �� EditText (���� ����� � ChatActivity). */
	public void sendResponse(String msg);

	/** ��������� ���������� ���������� ������ ChatActivity. */
	public void stopConnection();
}
