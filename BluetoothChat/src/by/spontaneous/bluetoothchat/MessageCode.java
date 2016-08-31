package by.spontaneous.bluetoothchat;

enum MessageCode
{	
	UNKNOWN((byte)0),
	
	TOAST((byte)1),
	MESSAGE((byte)2),	
	WAITING((byte)3),
	CONFIRMATION((byte)4),
	QUIT((byte)5);

	private final byte id;

	MessageCode(byte code)
	{
		id = code;
	}

	public byte getId()
	{
		return id;
	}

	public static MessageCode fromId(byte code)
	{
		MessageCode[] list = MessageCode.values();
		
		if (code >= 0 && code < list.length)
			return list[code];
		else
			return MessageCode.UNKNOWN;
	}
}
