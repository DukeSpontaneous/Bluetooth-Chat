package by.spontaneous.bluetoothchat.Services;

import java.nio.ByteBuffer;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;

/**
 * ����� ������ ���������, ����������� ��� ������������ �� ��������� �����������
 * � �������� ��������������� ������ �� ����������.
 */
final class MessagePacket
{
	private final static byte[] MY_BT_MAC_ADDRESS = getMyAddressBytes();

	/** ���������� mac-����� Default BluetoothAdapter'�. */
	private final static byte[] getMyAddressBytes()
	{
		final String[] macAddressParts = BluetoothAdapter.getDefaultAdapter().getAddress().split(":");
		final byte[] macAddressBytes = new byte[macAddressParts.length];

		// ������� 16-������� ���������� �������������
		for (int i = 0; i < macAddressParts.length; ++i)
		{
			Integer hex = Integer.parseInt(macAddressParts[i], 16);
			macAddressBytes[i] = hex.byteValue();
		}

		return macAddressBytes;
	}

	/** ���������� byte[] ������������� ������ �����������. */
	private final static byte[] getSenderAddressBytes(String strAddress)
	{
		final String[] macAddressParts = strAddress.split(":");
		final byte[] macAddressBytes = new byte[macAddressParts.length];

		// ������� 16-������� ���������� �������������
		for (int i = 0; i < macAddressParts.length; ++i)
		{
			Integer hex = Integer.parseInt(macAddressParts[i], 16);
			macAddressBytes[i] = hex.byteValue();
		}

		return macAddressBytes;
	}

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
			code = MessageCode.__UNKNOWN;
			message = null;
		}
	}

	/** �����-�����������, ���������������� ������������ ����� ����������. */
	private MessagePacket(byte[] inAddress, MessageCode inCode, int inId, String inMessage)
	{
		address = inAddress;
		id = inId;
		code = inCode;
		message = inMessage;

		byte[] bBuf = getBodyBytes();

		hash = Arrays.hashCode(bBuf);

		final byte[] bHash = ByteBuffer.allocate(4).putInt(hash).array();

		bytes = new byte[bHash.length + bBuf.length];

		System.arraycopy(bHash, 0, bytes, 0, bHash.length);
		System.arraycopy(bBuf, 0, bytes, bHash.length, bBuf.length);
	}

	/** ��������-����������� ������-�������. */
	public MessagePacket(MessageCode inCode)
	{
		this(MY_BT_MAC_ADDRESS, inCode, 0, null);
	}
	
	/** ��������-����������� ������������� ������-�������. */
	public MessagePacket(MessageCode inCode, int inId)
	{
		this(MY_BT_MAC_ADDRESS, inCode, inId, null);
	}
	
	/** ��������-����������� ������-���������. */
	public MessagePacket(MessageCode inCode, int inId, String inMessage)
	{
		this(MY_BT_MAC_ADDRESS, inCode, inId, inMessage);
	}	

	/** ��������-����������� ����������� HELLO-������� �������. */
	public MessagePacket(String inAddress, int inId)
	{
		this(getSenderAddressBytes(inAddress), MessageCode.__HELLO, inId, null);
	}

	/**
	 * �������� ������������ �������� ���� Hash code ������������ Hash code
	 * ���������, � ����� ������� � ���, ��� �������� ����� �� ��� ��������.
	 */
	public final boolean checkHash()
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

	/** ���������� byte[]-���� ������, �� �������� ����������� Hash. */
	private final byte[] getBodyBytes()
	{
		final byte[] bAddr = address != null ? address : new byte[0];
		final byte[] bId = ByteBuffer.allocate(4).putInt(id).array();
		final byte[] bMsg = message != null ? message.getBytes() : new byte[0];
		final byte[] bBuf = new byte[bAddr.length + bId.length + 1 + bMsg.length];

		// ���������� hash code ��� ���� ������ ������, ��������� �� 4-�� ������
		// (�� �������� ������� hash code)
		System.arraycopy(bAddr, 0, bBuf, 0, bAddr.length);
		System.arraycopy(bId, 0, bBuf, bAddr.length, bId.length);
		bBuf[bAddr.length + bId.length] = code.getId();
		System.arraycopy(bMsg, 0, bBuf, bAddr.length + bId.length + 1, bMsg.length);

		return bBuf;
	}

	/** ���������� String ������������� ������ �����������. */
	public final String getSenderAddressString()
	{
		final String digits = "0123456789ABCDEF";
		String hex = "";
		if (address != null)
		{
			for (int i = 0;;)
			{
				// "& 0xFF" ����� ��� ���������� � ������������ ����
				hex += digits.charAt((address[i] & 0xFF) / 16);
				hex += digits.charAt((address[i] & 0xFF) % 16);
				if (++i < address.length)
					hex += ':';
				else
					break;
			}
		}
		return hex;
	}
}
