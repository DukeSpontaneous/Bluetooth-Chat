package by.spontaneous.bluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.bluetooth.BluetoothSocket;

abstract class Communication extends Thread
{
	private final BluetoothSocket tSocket;
	protected final InputStream tInStream;
	protected final OutputStream tOutStream; 
	
	protected int tLastInputMsgNumber = 0;
	
	public Communication(BluetoothSocket socket)
	{
		tSocket = socket;
		
		InputStream tmpIn = null;
		OutputStream tmpOut = null;
		try
		{
			tmpIn = socket.getInputStream();
			tmpOut = socket.getOutputStream();
		}
		catch (IOException e)
		{
			this.cancel();
		}
		tInStream = tmpIn;
		tOutStream = tmpOut;		
	}
	
	public void cancel()
	{
		try
		{
			tSocket.close();
		}
		catch (IOException e)
		{
		}
	}
	
	
	
}
