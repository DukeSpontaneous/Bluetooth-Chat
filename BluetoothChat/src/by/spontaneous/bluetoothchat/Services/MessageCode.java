package by.spontaneous.bluetoothchat.Services;

enum MessageCode
{	
	__UNKNOWN((byte)0),
	
	__TEXT((byte)1),
	__HELLO((byte)2),
	__GOODBYE((byte)3),
	__PING((byte)4),
	__PONG((byte)5),
	__CONFIRMATION((byte)6),
	__QUIT((byte)7);

	private final byte id;

	MessageCode(byte code)
	{
		id = code;
	};

	public final byte getId()
	{
		return id;
	};

	public final static MessageCode fromId(byte code)
	{
		final MessageCode[] list = MessageCode.values();
		
		if (code >= 0 && code < list.length)
			return list[code];
		else
			return MessageCode.__UNKNOWN;
	};
}
