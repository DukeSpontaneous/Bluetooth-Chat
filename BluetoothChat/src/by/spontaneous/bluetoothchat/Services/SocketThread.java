package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.bluetooth.BluetoothSocket;

/** Инкапсуляция BluetoothSocket в Thread. */
abstract class SocketThread extends Thread
{
	private final BluetoothSocket tSocket;
	protected final InputStream tInStream;
	private final OutputStream tOutStream;

	/** Конструктор Thread, инкапсулирующий BluetoothSocket. */
	public SocketThread(BluetoothSocket socket)
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

	/** Обработчик остановки Thread, закрывающий свой BluetoothSocket. */
	protected void cancel()
	{
		try
		{
			tSocket.close();
		}
		catch (IOException e)
		{
		}
	}
	
	public void syncWrite(byte[] bytes)
	{
		synchronized (tInStream)
		{
			try
			{
				tOutStream.write(bytes);
			}
			catch (IOException e)
			{
				this.cancel();
				
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}
	
	public void syncWriteSeries(ArrayList<byte[]> bytesList)
	{
		synchronized (tInStream)
		{
			try
			{
				for(byte[] bytes : bytesList)
					tOutStream.write(bytes);
			}
			catch (IOException e)
			{
				this.cancel();
				
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}
}
