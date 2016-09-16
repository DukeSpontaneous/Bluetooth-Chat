package by.spontaneous.bluetoothchat.Services;

import java.io.IOException;
import java.util.Date;
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
import android.widget.Toast;

abstract class ChatService extends Service implements IChatClient
{
	/** ������ Thread'�� �������� �����������. */
	protected final ArrayList<ConnectionThread> aConnectionThread = new ArrayList<ConnectionThread>();

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

	// This class is thread-safe: multiple threads can share a single Timer
	// object without the need for external synchronization.

	private Timer aConfirmationTimer;

	// ���� �������������� � Thread'��� �����������.
	/**
	 * ����� �������� ��������� ��������, ����������� ��� ���� ��������, �����
	 * �����������.
	 */
	protected void broadcasting(final Thread sender, final MessagePacket packet)
	{
		synchronized (aConnectionThread)
		{
			for (final ConnectionThread thread : aConnectionThread)
			{
				if (thread != sender)
				{
					synchronized (aConfirmationMap)
					{
						aConfirmationMap.get(thread).add(packet);
					}
					transferResponseToUIThread(GUIRequestCode._BLOCK, null);

					// ���������� ������� �������� ��������
					aConfirmationTimer.schedule(new TimerTask()
					{
						private int retrys = 3;

						@Override
						public void run()
						{
							// ��������� ��������� ������� ��������
							// ������������� ��������.
							boolean isNotConfirmed;
							synchronized (aConfirmationMap)
							{
								isNotConfirmed = aConfirmationMap.get(thread).contains(packet);
							}

							if (isNotConfirmed == true)
							{
								if (retrys > 0)
								{
									transferToast("������������... =[");
									try
									{
										thread.syncWrite(packet.bytes);
									}
									catch (IOException e)
									{
										transferToast("������ ������������: " + e.getMessage());
									}
									--retrys;
								}
								else
								{
									// ����� ������� ��������� ��������
									// ��������� ������������� �����
									transferToast("��������� ����� ������� ������������!");
									thread.goodbyeSocketClose();

									// ���������� �������
									this.cancel();
								}
							}
							else
							{
								this.cancel();
							}
						};

					}, 2000, 3000);

					try
					{
						thread.syncWrite(packet.bytes);
					}
					catch (IOException e)
					{
						transferToast("������ �������� broadcasting(): " + e.getMessage());
					}
				}
			}
		}
	};

	/** ����� ������� �������, ����������� ��������� ������������ ��������. */
	private final class ConnectionThread extends SocketThread
	{
		private long tLastTimePING;

		public ConnectionThread(BluetoothSocket socket) throws IOException
		{
			super(socket);

			final ArrayList<byte[]> bPackets = new ArrayList<byte[]>();
			// ������������ ������ HELLO-������
			bPackets.add(new MessagePacket(MessageCode.__HELLO, aLastOutputMsgNumber).bytes);

			// ������������ ����������� HELLO-�������
			synchronized (aLastInputMsgNumberMap)
			{
				Collection<String> keys = aLastInputMsgNumberMap.keySet();
				for (String key : keys)
					bPackets.add(new MessagePacket(key, aLastInputMsgNumberMap.get(key)).bytes);
			}
			this.syncWriteSeries(bPackets);

			// ������� PING'������
			aConfirmationTimer.schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					tLastTimePING = new Date().getTime();
					try
					{
						syncWrite(new MessagePacket(tLastTimePING).bytes);
					}
					catch (IOException e)
					{
						this.cancel();
					}
				}

			}, 5000, 5000);
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

					this.goodbyeSocketClose();
					return;
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
				case __PING:
				{
					try
					{
						syncWrite(new MessagePacket(packet.message).bytes);
					}
					catch (IOException e)
					{
						transferToast("������ �������� PONG: " + e.getMessage());
					}
					break;
				}
				case __PONG:
				{
					long time = Long.parseLong(packet.message, Character.MAX_RADIX);
					if (time == tLastTimePING)
					{
						transferToast("PING: " + (new Date().getTime() - tLastTimePING) + " ms");
					}
					else
					{
						transferToast("����������� PONG!");
					}
					break;
				}
				case __TEXT:
				{
					// ����� ����� ��������� ������������� � ����� ������
					try
					{
						syncWrite(new MessagePacket(MessageCode.__CONFIRMATION, packet.id).bytes);
					}
					catch (IOException e)
					{
						transferToast("������ �������� �������������: " + e.getMessage());
					}

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
					transferResponseToUIThread(GUIRequestCode._MESSAGE, packet.message);

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
					transferToast("������ HELLO!");
					syncIncomingHello(packet);
					broadcasting(this, packet);

					break;
				}
				case __GOODBYE:
				{
					transferToast("������ GOODBYE!");
					syncIncomingGoodbye(packet);
					broadcasting(this, packet);

					this.goodbyeSocketClose();
					return;
				}
				default:
					transferToast("������: ������������� ��� ���������!");
					break;
				}
			}
			// ����� ������������ ����� ���������
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
						transferResponseToUIThread(GUIRequestCode._UNBLOCK, null);
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

		private final void syncIncomingHello(MessagePacket pack)
		{
			final String senderAddress = pack.getSenderAddressString();
			synchronized (aLastInputMsgNumberMap)
			{
				if (aLastInputMsgNumberMap.containsKey(senderAddress))
					transferToast("��������������: ������� ��������� HELLO!");
				aLastInputMsgNumberMap.put(pack.getSenderAddressString(), pack.id);
			}
		};

		private final void syncIncomingGoodbye(MessagePacket pack)
		{
			final String senderAddress = pack.getSenderAddressString();
			synchronized (aLastInputMsgNumberMap)
			{
				if (aLastInputMsgNumberMap.containsKey(senderAddress))
					aLastInputMsgNumberMap.remove(senderAddress);
				else
					transferToast("��������������: GOODBYE ������� ����������!");
			}
		};

		/** ���������� �������, ����������� ���������� ��������� ������. */
		protected void goodbyeSocketClose()
		{
			try
			{
				syncWrite(new MessagePacket(MessageCode.__GOODBYE, ++aLastOutputMsgNumber).bytes);
			}
			catch (IOException e)
			{
				transferToast("������ �������� GOODBYE ��� ���������� SocketThread: " + e.getMessage());
			}

			// ��������� �����������: ������� ��� ����������� �� �������
			syncRemoveConnectedClient(this);

			try
			{
				super.close();
				transferToast("Thread �������� ������ ������� ����������!");
			}
			catch (IOException e)
			{
				transferToast("������ SocketThread.close(): " + e.getMessage());
			}
		};

	}

	/** ����� ������������������� ���������� �������� � ������ �����������. */
	protected final void syncAddConnectionSocket(BluetoothSocket socket) throws IOException
	{
		// TODO: �������� ����� ����������� ������ ������� setName()? �������� �
		// �� ����� ������� ��� ������������-�����������.
		final ConnectionThread thread = new ConnectionThread(socket);

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
	};

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
				if (aConfirmationMap.isEmpty())
					transferResponseToUIThread(GUIRequestCode._LONELINESS, null);

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
				// ���� ��� ��������� ������������� ��������
				if (confirmationEmpty == true)
					transferResponseToUIThread(GUIRequestCode._UNBLOCK, null);
			}
		}
	};

	// ���� �������������� � GUI.
	/** Messenger �������������� � GUI. */
	protected volatile Messenger aMessenger;

	// TODO: ����� �� ��� ���������������� �� aMessenger?
	/** �������� ������� ����������� Messenger'� � Thread'� UI. */
	protected final void transferResponseToUIThread(GUIRequestCode code, String str)
	{
		if (aMessenger != null)
		{
			synchronized (aMessenger)
			{
				try
				{
					Message msg = Message.obtain(null, code.getId(), 0, 0);
					msg.obj = str;
					aMessenger.send(msg);
				}
				catch (RemoteException e)
				{
					// TODO: ���� ��� ��������� �� ����������� Messenger'�
				}
			}
		}
		else
		{
			// TODO: ���� ��� ��������� �� ����������� Messenger'�
		}
	};

	/** ������������ ������� �� ����� Toast � Thread'� UI. */
	protected final void transferToast(String str)
	{
		transferResponseToUIThread(GUIRequestCode._TOAST, "st: " + str);
	};

	/** ��������� Messanger ����� � UI. ���������� false, ���� �������� null. */
	public boolean updateMessanger(Messenger selectedMessenger)
	{
		if (selectedMessenger != null)
		{
			aMessenger = selectedMessenger;
			return true;
		}
		else
		{
			return false;
		}
	};

	/** �������������� ����� Timer. */
	public boolean startConnection(Messenger selectedMessenger)
	{
		if(updateMessanger(selectedMessenger))
		{
			aConfirmationTimer = new Timer();
			return true;			
		}
		else
		{
			return false;
		}		
	}
	
	// TODO: ������ ���������� ��������� ������������ Fragment'a �����������
	public void stopConnection()
	{
		Toast.makeText(getBaseContext(), "closeChatClient()!", Toast.LENGTH_SHORT).show();

		for (ConnectionThread thread : aConnectionThread)
			thread.goodbyeSocketClose();

		// ����� ����� Timer �� �������� ��� ����������!
		aConfirmationTimer.cancel();

		final int tasksCount = aConfirmationTimer.purge();
		if (tasksCount > 0)
			Toast.makeText(getBaseContext(), "������� ������� ������������� �������: " + tasksCount, Toast.LENGTH_LONG)
					.show();
		aConfirmationTimer = null;
	};
}