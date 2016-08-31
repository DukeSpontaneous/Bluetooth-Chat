package by.spontaneous.bluetoothchat;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 *  ласс пакета сообщени€, реализующий его формирование из отдельных компонентов
 * и проверку сформированного пакета на валидность.
 */
public class MessagePacket
{
	public final byte[] bytes;

	public final int hash;
	public final int id;
	public final MessageCode code;
	public final String message;

	public MessagePacket(byte[] inBytes)
	{
		bytes = inBytes;

		if (inBytes.length > 8)
		{
			hash = ByteBuffer.wrap(inBytes, 0, 4).getInt();
			id = ByteBuffer.wrap(inBytes, 4, 4).getInt();
			code = MessageCode.fromId(inBytes[8]);

			if (inBytes.length > 9)
				message = new String(Arrays.copyOfRange(inBytes, 9, inBytes.length));
			else
				message = null;
		}
		else
		{
			hash = 0;
			id = 0;
			code = MessageCode.UNKNOWN;
			message = null;
		}
	}

	public MessagePacket(MessageCode inCode, int inId)
	{
		code = inCode;
		id = inId;
		message = null;

		final byte[] bId = ByteBuffer.allocate(4).putInt(id).array();
		final byte[] bBuf = new byte[bId.length + 1];

		// ¬ычисление hash code дл€ всех данных пакета, следующих за 4-ым байтом
		// (за четырьм€ байтами hash code)
		System.arraycopy(bId, 0, bBuf, 0, bId.length);
		bBuf[bId.length] = (byte) inCode.getId();

		hash = Arrays.hashCode(bBuf);

		// ‘ормирование байтового представление пакета
		final byte[] bHash = ByteBuffer.allocate(4).putInt(hash).array();

		bytes = new byte[bHash.length + bBuf.length];

		System.arraycopy(bHash, 0, bytes, 0, bHash.length);
		System.arraycopy(bBuf, 0, bytes, bHash.length, bBuf.length);
	}
	
	public MessagePacket(MessageCode inCode, int inId, String inMessage)
	{
		code = inCode;
		id = inId;
		message = inMessage;

		final byte[] bId = ByteBuffer.allocate(4).putInt(id).array();
		final byte[] bMsg = inMessage != null ? inMessage.getBytes() : new byte[0];
		final byte[] bBuf = new byte[bId.length + 1 + bMsg.length];

		// ¬ычисление hash code дл€ всех данных пакета, следующих за 4-ым байтом
		// (за четырьм€ байтами hash code)
		System.arraycopy(bId, 0, bBuf, 0, bId.length);
		bBuf[bId.length] = (byte) inCode.getId();
		System.arraycopy(bMsg, 0, bBuf, bId.length + 1, bMsg.length);

		hash = Arrays.hashCode(bBuf);

		// ‘ормирование байтового представление пакета
		final byte[] bHash = ByteBuffer.allocate(4).putInt(hash).array();

		bytes = new byte[bHash.length + bBuf.length];

		System.arraycopy(bHash, 0, bytes, 0, bHash.length);
		System.arraycopy(bBuf, 0, bytes, bHash.length, bBuf.length);
	}
	
	public boolean checkHash()
	{
		if (bytes.length > 8)
		{
			final byte[] bBuf = Arrays.copyOfRange(bytes, 4, bytes.length);
			return Arrays.hashCode(bBuf) == hash ? true : false;
		}
		else
		{
			return false;
		}
	}
}
