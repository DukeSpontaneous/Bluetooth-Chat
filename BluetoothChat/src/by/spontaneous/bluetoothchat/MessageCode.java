package by.spontaneous.bluetoothchat;

enum MessageCode
{	
	UNKNOWN(0),
	
	TOAST(1),
	MESSAGE(2),
	CONFIRMATION(3);

	private final int id;

	MessageCode(int code)
	{
		id = code;
	}

	public int getId()
	{
		return id;
	}

	public static MessageCode fromId(int code)
	{
		MessageCode[] list = MessageCode.values();
		
		if (code >= 0 && code < list.length)
			return list[code];
		else
			return MessageCode.UNKNOWN;
	}
}
