package by.spontaneous.bluetoothchat;

enum ApplicationMode
{
	UNKNOWN(0),
	
	SERVER(1),
	CLIENT(2);
	
	private final int id;
	
	ApplicationMode(int code)
	{
		id = code;
	};

	public final int getId()
	{
		return id;
	};

	public final static ApplicationMode fromId(int code)
	{
		ApplicationMode[] list = ApplicationMode.values();

		if (code >= 0 && code < list.length)
			return list[code];
		else
			return ApplicationMode.UNKNOWN;
	};
}
