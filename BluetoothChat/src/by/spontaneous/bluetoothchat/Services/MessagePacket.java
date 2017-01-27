package by.spontaneous.bluetoothchat.Services;

import java.nio.ByteBuffer;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;

/**
 * Класс пакета сообщения, реализующий его формирование из отдельных компонентов
 * и проверку сформированного пакета на валидность.
 */
class MessagePacket {
    private final static byte[] MY_BT_MAC_ADDRESS = getMyAddressBytes();

    /** Возвращает mac-адрес Default BluetoothAdapter'а. */
    private static byte[] getMyAddressBytes() {
	final String[] macAddressParts = BluetoothAdapter.getDefaultAdapter().getAddress().split(":");
	final byte[] macAddressBytes = new byte[macAddressParts.length];

	// Парсинг 16-ричного текстового представления
	for (int i = 0; i < macAddressParts.length; ++i) {
	    Integer hex = Integer.parseInt(macAddressParts[i], 16);
	    macAddressBytes[i] = hex.byteValue();
	}

	return macAddressBytes;
    };

    /** Возвращает byte[] представление адреса отправителя. */
    private static byte[] getSenderAddressBytes(String strAddress) {
	final String[] macAddressParts = strAddress.split(":");
	final byte[] macAddressBytes = new byte[macAddressParts.length];

	// Парсинг 16-ричного текстового представления
	for (int i = 0; i < macAddressParts.length; ++i) {
	    Integer hex = Integer.parseInt(macAddressParts[i], 16);
	    macAddressBytes[i] = hex.byteValue();
	}

	return macAddressBytes;
    };

    public final byte[] bytes;

    public final int hash;
    public final byte[] address;
    public final int id;
    public final MessageCode code;
    public final String message;

    /** Конструктор пакета сообщения из массива байтов на фазе прибытия. */
    public MessagePacket(byte[] inBytes) {
	bytes = inBytes;

	if (inBytes.length > 4 + 6 + 4) {
	    hash = ByteBuffer.wrap(inBytes, 0, 4).getInt();
	    address = Arrays.copyOfRange(inBytes, 4, 4 + 6);
	    id = ByteBuffer.wrap(inBytes, 4 + 6, 4).getInt();
	    code = MessageCode.fromId(inBytes[4 + 6 + 4]);

	    if (inBytes.length > 4 + 6 + 4 + 1)
		message = new String(Arrays.copyOfRange(inBytes, 4 + 6 + 4 + 1, inBytes.length));
	    else
		message = null;
	} else {
	    hash = 0;
	    address = null;
	    id = 0;
	    code = MessageCode.__UNKNOWN;
	    message = null;
	}
    };

    /** Супер-конструктор, инициализирующий произвольный набор параметров. */
    private MessagePacket(byte[] inAddress, MessageCode inCode, int inId, String inMessage) {
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
    };

    /** Экспресс-конструктор пакета-запроса. */
    public MessagePacket(MessageCode inCode) {
	this(MY_BT_MAC_ADDRESS, inCode, 0, null);
    };

    /** Экспресс-конструктор нумерованного пакета-запроса. */
    public MessagePacket(MessageCode inCode, int inId) {
	this(MY_BT_MAC_ADDRESS, inCode, inId, null);
    };

    /** Экспресс-конструктор пакета-сообщения. */
    public MessagePacket(MessageCode inCode, int inId, String inMessage) {
	this(MY_BT_MAC_ADDRESS, inCode, inId, inMessage);
    };

    /** Экспресс-конструктор суррогатных HELLO-пакетов сервера. */
    public MessagePacket(String inAddress, int inId) {
	this(getSenderAddressBytes(inAddress), MessageCode.__HELLO, inId, null);
    };

    /** Экспресс-конструктор PING-пакетов. */
    public MessagePacket(long time) {
	this(MY_BT_MAC_ADDRESS, MessageCode.__PING, 0, Long.toString(time, Character.MAX_RADIX));
    };

    /** Экспресс-конструктор PONG-пакетов. */
    public MessagePacket(String time) {
	this(MY_BT_MAC_ADDRESS, MessageCode.__PONG, 0, time);
    };

    /**
     * Проверка соответствия значения поля Hash code фактическому Hash code
     * сообщения, с целью убедить в том, что входящий пакет не был повреждён.
     */
    public boolean checkHash() {
	if (bytes.length > 4 + 6 + 4) {
	    final byte[] bBuf = Arrays.copyOfRange(bytes, 4, bytes.length);
	    return (Arrays.hashCode(bBuf) == hash);
	} else {
	    return false;
	}
    };

    /** Возвращает byte[]-тело пакета, по которому вычисляется Hash. */
    private byte[] getBodyBytes() {
	final byte[] bAddr = address != null ? address : new byte[0];
	final byte[] bId = ByteBuffer.allocate(4).putInt(id).array();
	final byte[] bMsg = message != null ? message.getBytes() : new byte[0];
	final byte[] bBuf = new byte[bAddr.length + bId.length + 1 + bMsg.length];

	// Вычисление hash code для всех данных пакета, следующих за 4-ым байтом
	// (за четырьмя байтами hash code)
	System.arraycopy(bAddr, 0, bBuf, 0, bAddr.length);
	System.arraycopy(bId, 0, bBuf, bAddr.length, bId.length);
	bBuf[bAddr.length + bId.length] = code.getId();
	System.arraycopy(bMsg, 0, bBuf, bAddr.length + bId.length + 1, bMsg.length);

	return bBuf;
    };

    /** Возвращает String представление адреса отправителя. */
    public String getSenderAddressString() {
	StringBuilder hex = new StringBuilder();

	if (address != null) {
	    for (int i = 0;;) {
		hex.append(Integer.toString(address[i] & 0xF0 >> 4, 16));
		hex.append(Integer.toString(address[i] & 0x0F, 16));

		if (++i < address.length)
		    hex.append(':');
		else
		    break;
	    }
	}

	return hex.toString();
    };
}
