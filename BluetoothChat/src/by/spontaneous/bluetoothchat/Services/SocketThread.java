package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.bluetooth.BluetoothSocket;

/** ������������ BluetoothSocket � Thread. */
abstract class SocketThread extends Thread
{
	private final BluetoothSocket tSocket;
	protected final InputStream tInStream;
	private final OutputStream tOutStream;

	/** ����������� Thread, ��������������� BluetoothSocket. */
	public SocketThread(BluetoothSocket socket) throws IOException
	{
		tSocket = socket;

		tInStream = socket.getInputStream();
		tOutStream = socket.getOutputStream();
	}

	/** ���������� ��������� Thread, ����������� ���� BluetoothSocket. */
	protected void cancel() throws IOException
	{
		tSocket.close();
	}
	
	public void syncWrite(byte[] bytes) throws IOException
	{
		synchronized (tInStream)
		{
			tOutStream.write(bytes);
		}		
	}
	
	public void syncWriteSeries(ArrayList<byte[]> bytesList) throws IOException
	{
		synchronized (tInStream)
		{
			for(byte[] bytes : bytesList)
				tOutStream.write(bytes);
		}		
	}
}
