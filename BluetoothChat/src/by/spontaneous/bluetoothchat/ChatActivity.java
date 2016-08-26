package by.spontaneous.bluetoothchat;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ChatActivity extends Activity
{
	/** ������� ����������� ���� ��� ��������� Bluetooth ����������. */
	private static final int REQUEST_DISCOVERABLE_BT = 2;
	/** ������� �������������� Bluetooth ��������� Android. */
	private BluetoothAdapter bluetoothAdapter;

	/** ������ ���������. */
	private ArrayList<String> messages;
	/** ������-������� ���������. */
	private ArrayAdapter<String> messagesAdapter;

	/** ����� ������� � ChatClientService. */
	private IChatClient chatClient;

	/** ����� ������� � ChatServerService. */
	private ChatServerService chatServerService;
	/** ����� ������� � ChatClientService. */
	private ChatClientService chatClientService;

	private ApplicationMode applicationMode;
	ProgressDialog pDialog;

	/** Messenger, ����������� �������� ������ � Thread'� ChatActivity. */
	private Messenger chatUIThreadMessenger = new Messenger(new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			String str;
			switch (MessageCode.fromId(msg.what))
			{
			// ������ � ������ Toast'�� ��� Thread'�� �������������� Socket'�
			case TOAST:
				str = (String) msg.obj;
				Toast.makeText(getBaseContext(), str, Toast.LENGTH_LONG).show();
				break;
			// ����� ��������� � ��� ����
			case MESSAGE:
				str = (String) msg.obj;
				messages.add(str);
				messagesAdapter.notifyDataSetChanged();

				// TODO: ����� ����� �����-�� ������� ���������� ��������
				// (������� ���������� ListView �� ����������, ���� �����������
				// ��������� ������ ��� ���� ���������)?
				final ListView listView = (ListView) findViewById(R.id.listViewChat);
				listView.smoothScrollToPosition(messagesAdapter.getCount() - 1);
				break;
			case WAITING:
				pDialog.show();
				break;
			case CONFIRMATION:
				pDialog.dismiss();
				break;
			case QUIT:
				quitChatActivity();
				break;
			// �������������� ��������
			case UNKNOWN:
				Toast.makeText(getBaseContext(), "Messenger: ������������� ��� ���������!", Toast.LENGTH_LONG).show();
				break;
			default:
				super.handleMessage(msg);
			}
		}
	});

	/** ������ ����������� � Service'� ChatServerService */
	private final ServiceConnection serverConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// ��������� ������ �� ������-������ ��� �������� ����������� �
			// chatServerService
			chatServerService = ((ChatServerService.LocalBinder) service).getService();
			chatClient = chatServerService;

			tryConnectToService();

			// �hatServerService.createServer() ����� Messenger!!!
			if (!chatServerService.createServer(bluetoothAdapter))
			{
				Toast.makeText(getBaseContext(), "������ ChatServerService: �� ������� ������� ������.",
						Toast.LENGTH_LONG).show();
				return;
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "������� ���������� ������� ServiceConnection serverConnection...",
					Toast.LENGTH_LONG).show();
			chatClient = null;
			chatServerService = null;
		}
	};
	/** ������ ����������� � Service'� ChatClientService */
	private final ServiceConnection clientConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// ��������� ������ �� ������-������ ��� �������� ����������� �
			// chatClientService
			chatClientService = ((ChatClientService.LocalBinder) service).getService();
			chatClient = chatClientService;

			tryConnectToService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "������� ���������� ������� ServiceConnection clientConnection...",
					Toast.LENGTH_LONG).show();
			chatClient = null;
			chatClientService = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat);

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
		{
			this.finish();
			Toast.makeText(getBaseContext(), "������: �� ������� �������� BluetoothAdapter.", Toast.LENGTH_LONG).show();
		}

		int modeCode = getIntent().getIntExtra(getResources().getString(R.string.request_code_mode),
				ApplicationMode.UNKNOWN.getId());
		applicationMode = ApplicationMode.fromId(modeCode);
		if (applicationMode == ApplicationMode.UNKNOWN)
		{
			this.finish();
		}

		messages = new ArrayList<String>();
		messagesAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, messages)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				View view = super.getView(position, convertView, parent);
				final String str = getItem(position);
				((TextView) view.findViewById(android.R.id.text1)).setText(str);
				return view;
			}
		};

		final ListView listView = (ListView) findViewById(R.id.listViewChat);
		listView.setAdapter(messagesAdapter);

		final Button btn = (Button) findViewById(R.id.buttonSendMessage);
		btn.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				EditText text = (EditText) findViewById(R.id.et_input_message);

				if (!text.getText().toString().equals(""))
				{
					// �������� ��������� � ������������ Messenger Thread'� UI
					// TODO: ���� ��� �������� � ���������� � Thread'� UI...
					try
					{
						Message msg = Message.obtain(null, MessageCode.MESSAGE.getId(), 0, 0);
						msg.obj = text.getText().toString();
						chatUIThreadMessenger.send(msg);
					}
					catch (RemoteException e)
					{
					}

					// ������������ ��������� �������� ���������� ���������, �
					// ������������ �� ����� ��������� ��������� � 255 ����.
					byte[] buffer = new byte[256];
					buffer[0] = (byte) MessageCode.MESSAGE.getId();
					// ��������� �� ��������� ������������ � ������������
					// ��������... o_0
					System.arraycopy(text.getText().toString().getBytes(), 0, buffer, 1,
							text.getText().toString().getBytes().length < 255
									? text.getText().toString().getBytes().length : 255);

					// �������� ��������� � �������
					chatClient.sendResponse(buffer);
					text.getText().clear();
				}
			}
		});
		
		pDialog = new ProgressDialog(this);
		pDialog.setTitle("�������� ���������");
		pDialog.setMessage("�������� �������������...");
	}

	@Override
	protected void onDestroy()
	{
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		// Bind to LocalService

		// �������� ������� ������
		Intent intent;

		switch (applicationMode)
		{
		case SERVER:
			intent = new Intent(getApplicationContext(), ChatServerService.class);
			bindService(intent, serverConnection, Context.BIND_AUTO_CREATE);

			// ���� ����� �������������� ��� ����������� ��������� ��
			// �����������, ��...
			if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
			{
				Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
				startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
			}
			break;
		case CLIENT:
			intent = new Intent(getApplicationContext(), ChatClientService.class);
			bindService(intent, clientConnection, Context.BIND_AUTO_CREATE);
			break;
		default:
			Toast.makeText(getBaseContext(), "������ ChatActivity: ������������� ����� �������!", Toast.LENGTH_LONG)
					.show();
			break;
		}

	}

	@Override
	protected void onStop()
	{
		if (isFinishing())
		{
			chatClient.close();
		}

		if (chatServerService != null)
		{
			unbindService(serverConnection);
			chatServerService = null;
		}

		if (chatClientService != null)
		{
			unbindService(clientConnection);
			chatClientService = null;
		}

		chatClient = null;

		super.onStop();
	}

	/**
	 * ���������� �����, ������������ ����� ��������� �������
	 * REQUEST_DISCOVERABLE_BT.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
		case REQUEST_DISCOVERABLE_BT:
		{
			switch (resultCode)
			{
			case RESULT_CANCELED:
			{
				Toast.makeText(getBaseContext(), "�� ������� ������� ���������� ������� ��� Bluetooth-��������.",
						Toast.LENGTH_LONG).show();
				break;
			}
			case 1:
			{
				Toast.makeText(getBaseContext(), "���������� ����� ������ ��� Bluetooth-�������� �������������� �����.",
						Toast.LENGTH_LONG).show();
				break;
			}
			default:
			{
				Toast.makeText(getBaseContext(),
						"���������� ����� ������ ��� Bluetooth-�������� " + resultCode + " ������.", Toast.LENGTH_LONG)
						.show();
				break;
			}
			}
		}
		default:
		{
			break;
		}
		}
	};

	/** ������� �������� Messenger ��� Thread'�� ���������� Service. */
	private void tryConnectToService()
	{
		if (chatClient.connectToServer(chatUIThreadMessenger) == false)
			quitChatActivity();
	};

	private void quitChatActivity()
	{
		this.finish();
	}
}