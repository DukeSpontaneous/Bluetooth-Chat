﻿package by.spontaneous.bluetoothchat.Services;

public enum GUIRequestCode {
    _UNKNOWN((byte) 0),

    _TOAST((byte) 1),
    _MESSAGE((byte) 2),
    _BLOCK((byte) 3),
    _UNBLOCK((byte) 4),
    _CUT((byte) 5),
    _LONELINESS((byte) 6),
    _QUIT((byte) 7);

    private final byte id;

    GUIRequestCode(byte code) {
	id = code;
    };

    public byte getId() {
	return id;
    };

    public static GUIRequestCode fromId(byte code) {
	final GUIRequestCode[] list = GUIRequestCode.values();

	if (code >= 0 && code < list.length)
	    return list[code];
	else
	    return GUIRequestCode._UNKNOWN;
    };
}
