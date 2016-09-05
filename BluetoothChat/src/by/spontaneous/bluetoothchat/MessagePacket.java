package by.spontaneous.bluetoothchat;

import java.nio.ByteBuffer;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;

/**
 * ����� ������ ���������, ����������� ��� ������������ �� ��������� �����������
 * � �������� ��������������� ������ �� ����������.
 */
public class MessagePacket
{
	public static final byte[] MY_BT_MAC_ADDRESS = getMyBluetoothMACAddress();
	
	public final byte[] bytes;

	public final int hash;
	public final byte[] address;
	public final int id;
	public final MessageCode code;
	public final String message;

	/** ����������� ������ ��������� �� ������� ������ �� ���� ��������. */
	public MessagePacket(byte[] inBytes)
	{
		bytes = inBytes;

		if (inBytes.length > 4 + 6 + 4)
		{
			hash = ByteBuffer.wrap(inBytes, 0, 4).getInt();
			address = Arrays.copyOfRange(inBytes, 4, 4 + 6);
			id = ByteBuffer.wrap(inBytes, 4 + 6, 4).getInt();
			code = MessageCode.fromId(inBytes[4 + 6 + 4]);

			if (inBytes.length > 4 + 6 + 4 + 1)
				message = new String(Arrays.copyOfRange(inBytes, 4 + 6 + 4 + 1, inBytes.length));
			else
				message = null;
		}
		else
		{
			hash = 0;
			address = null;
			id = 0;
			code = MessageCode.UNKNOWN;
			message = null;
		}
	}

	/**
	 * ����������� ������ ��������� �� ������������ ������ ��������� �����������
	 * (��� ����� ��������� ������) �� ���� ������ . �� ������������.
	 */
	public MessagePacket(MessageCode inCode, int inId)
	{
		address = MY_BT_MAC_ADDRESS;
		id = inId;
		code = inCode;
		message = null;

		final byte[] bAddr = MY_BT_MAC_ADDRESS != null ? MY_BT_MAC_ADDRESS : new byte[0];
		final byte[] bId = ByteBuffer.allocate(4).putInt(inId).array();
		final byte[] bMsg = new byte[0];
		final byte[] bBuf = new byte[bAddr.length + bId.length + 1 + bMsg.length];

		// ���������� hash code ��� ���� ������ ������, ��������� �� 4-�� ������
		// (�� �������� ������� hash code)
		System.arraycopy(bAddr, 0, bBuf, 0, bAddr.length);
		System.arraycopy(bId, 0, bBuf, bAddr.length, bId.length);
		bBuf[bAddr.length + bId.length] = inCode.getId();
		System.arraycopy(bMsg, 0, bBuf, bAddr.length + bId.length + 1, bMsg.length);

		hash = Arrays.hashCode(bBuf);

		// ������������ ��������� ������������� ������
		final byte[] bHash = ByteBuffer.allocate(4).putInt(hash).array();

		bytes = new byte[bHash.length + bBuf.length];

		System.arraycopy(bHash, 0, bytes, 0, bHash.length);
		System.arraycopy(bBuf, 0, bytes, bHash.length, bBuf.length);
	}

	/**
	 * ����������� ������ ��������� �� ������� ������ ��������� ����������� ��
	 * ���� ������.
	 */
	public MessagePacket(MessageCode inCode, int inId, String inMessage)
	{
		address = MY_BT_MAC_ADDRESS;
		id = inId;
		code = inCode;
		message = inMessage;

		final byte[] bAddr = MY_BT_MAC_ADDRESS != null ? MY_BT_MAC_ADDRESS : new byte[0];
		final byte[] bId = ByteBuffer.allocate(4).putInt(inId).array();
		final byte[] bMsg = inMessage != null ? inMessage.getBytes() : new byte[0];
		final byte[] bBuf = new byte[bAddr.length + bId.length + 1 + bMsg.length];

		// ���������� hash code ��� ���� ������ ������, ��������� �� 4-�� ������
		// (�� �������� ������� hash code)
		System.arraycopy(bAddr, 0, bBuf, 0, bAddr.length);
		System.arraycopy(bId, 0, bBuf, bAddr.length, bId.length);
		bBuf[bAddr.length + bId.length] = inCode.getId();
		System.arraycopy(bMsg, 0, bBuf, bAddr.length + bId.length + 1, bMsg.length);

		hash = Arrays.hashCode(bBuf);

		// ������������ ��������� ������������� ������
		final byte[] bHash = ByteBuffer.allocate(4).putInt(hash).array();

		bytes = new byte[bHash.length + bBuf.length];

		System.arraycopy(bHash, 0, bytes, 0, bHash.length);
		System.arraycopy(bBuf, 0, bytes, bHash.length, bBuf.length);
	}

	/**
	 * �������� ������������ �������� ���� Hash code ������������ Hash code
	 * ���������, � ����� ������� � ���, ��� �������� ����� �� ��� ��������.
	 */
	public boolean checkHash()
	{
		if (bytes.length > 4 + 6 + 4)
		{
			final byte[] bBuf = Arrays.copyOfRange(bytes, 4, bytes.length);
			return Arrays.hashCode(bBuf) == hash ? true : false;
		}
		else
		{
			return false;
		}
	}

	private static byte[] getMyBluetoothMACAddress()
	{
		final String[] macAddressParts = BluetoothAdapter.getDefaultAdapter().getAddress().split(":");

		// convert hex string to byte values
		final byte[] macAddressBytes = new byte[macAddressParts.length];

		for (int i = 0; i < macAddressParts.length; ++i)
		{
			Integer hex = Integer.parseInt(macAddressParts[i], 16);
			macAddressBytes[i] = hex.byteValue();
		}

		return macAddressBytes;
	}
}
