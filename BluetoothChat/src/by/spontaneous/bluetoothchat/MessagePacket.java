package by.spontaneous.bluetoothchat;

import java.nio.ByteBuffer;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;

/**
 * Класс пакета сообщения, реализующий его формирование из отдельных компонентов
 * и проверку сформированного пакета на валидность.
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

	/** Конструктор пакета сообщения из массива байтов на фазе прибытия. */
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
	 * Конструктор пакета сообщения из сокращённого набора составных компонентов
	 * (без блока строковых данных) на фазе исхода . НЕ ТЕСТИРОВАЛСЯ.
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

		// Вычисление hash code для всех данных пакета, следующих за 4-ым байтом
		// (за четырьмя байтами hash code)
		System.arraycopy(bAddr, 0, bBuf, 0, bAddr.length);
		System.arraycopy(bId, 0, bBuf, bAddr.length, bId.length);
		bBuf[bAddr.length + bId.length] = inCode.getId();
		System.arraycopy(bMsg, 0, bBuf, bAddr.length + bId.length + 1, bMsg.length);

		hash = Arrays.hashCode(bBuf);

		// Формирование байтового представление пакета
		final byte[] bHash = ByteBuffer.allocate(4).putInt(hash).array();

		bytes = new byte[bHash.length + bBuf.length];

		System.arraycopy(bHash, 0, bytes, 0, bHash.length);
		System.arraycopy(bBuf, 0, bytes, bHash.length, bBuf.length);
	}

	/**
	 * Конструктор пакета сообщения из полного набора составных компонентов на
	 * фазе исхода.
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

		// Вычисление hash code для всех данных пакета, следующих за 4-ым байтом
		// (за четырьмя байтами hash code)
		System.arraycopy(bAddr, 0, bBuf, 0, bAddr.length);
		System.arraycopy(bId, 0, bBuf, bAddr.length, bId.length);
		bBuf[bAddr.length + bId.length] = inCode.getId();
		System.arraycopy(bMsg, 0, bBuf, bAddr.length + bId.length + 1, bMsg.length);

		hash = Arrays.hashCode(bBuf);

		// Формирование байтового представление пакета
		final byte[] bHash = ByteBuffer.allocate(4).putInt(hash).array();

		bytes = new byte[bHash.length + bBuf.length];

		System.arraycopy(bHash, 0, bytes, 0, bHash.length);
		System.arraycopy(bBuf, 0, bytes, bHash.length, bBuf.length);
	}

	/**
	 * Проверка соответствия значения поля Hash code фактическому Hash code
	 * сообщения, с целью убедить в том, что входящий пакет не был повреждён.
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
