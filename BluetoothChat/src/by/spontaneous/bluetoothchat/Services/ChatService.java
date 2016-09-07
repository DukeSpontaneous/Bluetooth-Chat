package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.bluetooth.BluetoothSocket;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

abstract class ChatService extends Service implements IChatClient
{
	//protected final static String SERVICE_UUID = "565b2b42-b215-4717-a10b-f61bd445001f";
	//protected final static String SERVICE_NAME = "Testing Server";
	
	
	/** ������ Thread'�� �������� �����������. */
	protected final ArrayList<SocketThread> aConnectionThread = new ArrayList<SocketThread>();

	/** ID ���������� ���������� ���������. */
	protected volatile int aLastOutputMsgNumber = 0;

	/**
	 * ���-����� ��������� ������������� �������� ��������� (�����, ������
	 * �������). ����������� ����� ������ ����� ������� ��������� ����������
	 * �������� ����� ��������� ��������� (� ���� ���������� ��������� ����
	 * ������� ������ ����� �������).
	 */
	protected final HashMap<Thread, ArrayList<MessagePacket>> aConfirmationMap = new HashMap<Thread, ArrayList<MessagePacket>>();
	protected final HashMap<String, Integer> aLastInputMsgNumberMap = new HashMap<String, Integer>();

	private final Timer aConfirmationTimer = new Timer();

	// ���� �������������� � Thread'��� �����������.
	/**
	 * ����� �������� ��������� ��������, ����������� ��� ���� ��������, �����
	 * �����������.
	 */
	protected final void broadcasting(Thread sender, MessagePacket packet)
	{
		synchronized (aConnectionThread)
		{
			for (SocketThread thread : aConnectionThread)
			{
				if (thread != sender)
				{
					synchronized (aConfirmationMap)
					{
						aConfirmationMap.get(thread).add(packet);
					}
					transferResponseToUIThread(null, GUIRequestCode._BLOCK);

					/*
					// ���������� ������� �������� ��������
					aConfirmationTimer.schedule(new TimerTask()
					{						
						public void run()
						{
							// TODO: ��������� ��������� ������� ��������
							// ������������� ��������.
							synchronized (aConfirmationMap)
							{
								// ��� ������� � ��������� ����������!
								if(aConfirmationMap.get(thread).contains(packet))
								{									
								}								
							}
						}
					}, 1000);
					*/

					thread.syncWrite(packet.bytes);
				}
			}
		}
	}

	/** ����� ������� �������, ����������� ��������� ������������ ��������. */
	private final class ConnectionThread extends SocketThread
	{
		public ConnectionThread(BluetoothSocket socket)
		{
			super(socket);

			final ArrayList<byte[]> bPackets = new ArrayList<byte[]>();
			// ���������� ������ ������� � ��������
			bPackets.add(new MessagePacket(MessageCode.__HELLO, aLastOutputMsgNumber, null).bytes);

			// ���������� ������ ������� � ���������, ��������������� �����
			synchronized (aLastInputMsgNumberMap)
			{
				Collection<String> keys = aLastInputMsgNumberMap.keySet();
				for (String key : keys)
					bPackets.add(new MessagePacket(key, aLastInputMsgNumberMap.get(key)).bytes);
			}
			this.syncWriteSeries(bPackets);
		};

		/** ���� ConnectionThread (��������� BluetoothSocket). */
		@Override
		public void run()
		{
			// TODO: ������, �������, ��� ��������...
			final byte[] buffer = new byte[256];
			int bCount;

			// ���� ���������
			while (true)
			{
				try
				{
					bCount = tInStream.read(buffer);
				}
				catch (IOException e)
				{
					transferToast("������ ���������:" + e.getMessage());
					break;
				}

				byte[] bPacket = Arrays.copyOfRange(buffer, 0, bCount);

				// ����� ������������ ����������� ���������
				final MessagePacket packet = new MessagePacket(bPacket);

				if (!packet.checkHash())
				{
					transferToast("������: ���������� ��������� ����������!");
					continue;
				}

				switch (packet.code)
				{
				case __TEXT:
				{
					// ����� ����� ��������� ������������� � ����� ������
					syncWrite(new MessagePacket(MessageCode.__CONFIRMATION, packet.id, null).bytes);

					// TODO: ������ ������������ ������������������ ������
					// ���� ����������, �� ����������� ������, ����� ������
					// ������������ � �������, �� ������� �� �����
					// ���������� ����� �����������

					final int lastInputMsgID = aLastInputMsgNumberMap.get(packet.getSenderAddressString());
					if (packet.id <= lastInputMsgID)
					{
						transferToast("������: ������� �������� ���������!");
						continue;
					}
					else if (packet.id > lastInputMsgID + 1)
					{
						transferToast("��������������: ������� � ������������������ ���������!");
					}
					transferResponseToUIThread(packet.message, GUIRequestCode._MESSAGE);

					// ��������� ���� ������������, ����� �����������
					broadcasting(this, packet);
					aLastInputMsgNumberMap.put(packet.getSenderAddressString(), packet.id);
					break;
				}
				case __CONFIRMATION:
				{
					syncCheckIncomingConfirmation(packet);
					break;
				}
				case __HELLO:
				{
					syncHello(packet);
					broadcasting(this, packet);
					break;
				}
				case __GOODBYE:
				{
					syncGoodbye(packet);
					broadcasting(this, packet);
					break;
				}
				default:
					break;
				}
			}
			// ����� ������������ ����� ���������

			// TODO: ...����� �� ����� ���� ������������� Thread?
			this.cancel();
		};

		private void syncCheckIncomingConfirmation(MessagePacket packet)
		{
			synchronized (aConfirmationMap)
			{
				if (aConfirmationMap.containsKey(this))
				{
					boolean continueWaiting = false;
					boolean packetFound = false;
					for (MessagePacket mp : aConfirmationMap.get(this))
					{
						if (packet.id == mp.id)
						{
							aConfirmationMap.get(this).remove(mp);
							packetFound = true;
						}
						else
						{
							continueWaiting = true;
						}
					}

					if (!continueWaiting)
					{
						transferResponseToUIThread(null, GUIRequestCode._UNBLOCK);
					}

					if (!packetFound)
					{
						transferToast("������: ������ ������������� ������������� ��������!");
					}
				}
				else
				{
					transferToast("������: ����������� ���������� ����� ����������� ��������!");
				}
			}
		};

		private final void syncHello(MessagePacket pack)
		{
			final String senderAddress = pack.getSenderAddressString();
			synchronized (aLastInputMsgNumberMap)
			{
				if (aLastInputMsgNumberMap.containsKey(senderAddress))
					transferToast("��������������: ������� ��������� Hello!");
				aLastInputMsgNumberMap.put(pack.getSenderAddressString(), pack.id);
			}
		}

		private final void syncGoodbye(MessagePacket pack)
		{
			final String senderAddress = pack.getSenderAddressString();
			synchronized (aLastInputMsgNumberMap)
			{
				if (aLastInputMsgNumberMap.containsKey(senderAddress))
					aLastInputMsgNumberMap.remove(senderAddress);
			}
		}

		@Override
		protected void cancel()
		{
			// ��������� �����������: ������� ��� ����������� �� �������
			broadcasting(this, new MessagePacket(MessageCode.__GOODBYE, ++aLastOutputMsgNumber, null));
			syncRemoveConnectedClient(this);

			super.cancel();
			transferToast("Thread �������� ������ ������� ����������!");
		}
	};

	/** ����� ������������������� ���������� �������� � ������ �������. */
	protected final void syncAddConnectedClient(BluetoothSocket socket)
	{
		// TODO: �������� ����� ����������� ������ ������� setName()
		final SocketThread thread = new ConnectionThread(socket);

		synchronized (aConnectionThread)
		{
			aConnectionThread.add(thread);
		}

		synchronized (aConfirmationMap)
		{
			aConfirmationMap.put(thread, new ArrayList<MessagePacket>());
		}

		thread.setDaemon(true);
		thread.start();
	}

	/** ����� ������������������� �������� �������� �� ������� �������. */
	private final void syncRemoveConnectedClient(SocketThread targetThread)
	{
		synchronized (aConnectionThread)
		{
			aConnectionThread.remove(targetThread);
		}

		synchronized (aConfirmationMap)
		{
			if (aConfirmationMap.containsKey(targetThread))
			{
				aConfirmationMap.remove(targetThread);

				boolean confirmationEmpty = true;
				for (ArrayList<MessagePacket> waitedPacketsConfirm : aConfirmationMap.values())
				{
					// ���� ���� ������ ��������� ������������� ��������
					if (!waitedPacketsConfirm.isEmpty())
					{
						confirmationEmpty = false;
						break;
					}
				}

				if (confirmationEmpty == true)
				{
					// ���� ��� ��������� ������������� ��������
					transferResponseToUIThread(null, GUIRequestCode._UNBLOCK);
				}
			}
		}
	}

	// ���� �������������� � GUI.
	/** Messenger �������������� � GUI. */
	protected Messenger aMessenger;

	/** �������� ������� ����������� Messenger'� � Thread'� UI. */
	protected final synchronized void transferResponseToUIThread(String str, GUIRequestCode code)
	{
		try
		{
			Message msg = Message.obtain(null, code.getId(), 0, 0);
			msg.obj = str;
			aMessenger.send(msg);
		}
		catch (RemoteException e)
		{
		}
	}

	/** ������������ ������� �� ����� Toast � Thread'� UI. */
	protected final void transferToast(String str)
	{
		transferResponseToUIThread(str, GUIRequestCode._TOAST);
	}
}
