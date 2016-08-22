package by.spontaneous.bluetoothchat;

import java.util.ArrayList;

import android.app.Activity;
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
	private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

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

	/** Messenger, ����������� �������� ������ � Thread'� ChatActivity. */
	private Messenger chatUIThreadMessenger = new Messenger(new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			// ������ � ������ Toast'�� ��� Thread'�� �������������� Socket'�
			case 0:
				String str = (String) msg.obj;
				Toast.makeText(getBaseContext(), str, Toast.LENGTH_LONG).show();
				break;
			// ����� ��������� � ��� ����
			case 1:
				byte[] buffer = (byte[]) msg.obj;
				messages.add(new String(buffer));
				messagesAdapter.notifyDataSetChanged();

				// TODO: ����� ����� �����-�� ������� ���������� ��������
				// (������� ���������� ListView �� ����������, ���� �����������
				// ��������� ������ ��� ���� ���������)?
				final ListView listView = (ListView) findViewById(R.id.listViewChat);
				listView.smoothScrollToPosition(messagesAdapter.getCount() - 1);

				break;

			// �������������� ��������
			default:
				super.handleMessage(msg);
			}
		}
	});

	/** Defines callbacks for service binding, passed to bindService() */
	private final ServiceConnection serverConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// ��������� ������ �� ������-������ ��� �������� ����������� �
			// chatServerService
			chatServerService = ((ChatServerService.LocalBinder) service).getService();

			// TODO: ��� ��������� Messenger'� ����� ������������� ChatActivity
			// ����������� �� �������� ����� ���� ������� �� Toast'�
			// AcceptTread'��.
			chatServerService.connectToServer(chatUIThreadMessenger);

			chatClient = chatServerService;

			if (!chatServerService.createServer(bluetoothAdapter))
			{
				Toast.makeText(getBaseContext(), "������ ChatServerService: �� ������� ������� ������.",
						Toast.LENGTH_LONG).show();
				return;
			}

			// ���� ����� ��������� ��� ���������� ����������� �������, ��...
			if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
			{
				Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
				startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
			}

			tryConnectToServer();
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

	private final ServiceConnection clientConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// ��������� ������ �� ������-������ ��� �������� ����������� �
			// chatClientService
			chatClientService = ((ChatClientService.LocalBinder) service).getService();
			chatClient = chatClientService;

			tryConnectToServer();
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

		int modeCode = getIntent().getIntExtra(getResources().getString(R.string.request_code_mode),
				ApplicationMode.UNKNOWN.getId());

		// TODO: ������� ��� �������, � ������� ������� ���������...
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

				// �������� ��������� � ����� UI
				try
				{
					Message msg = Message.obtain(null, 1, 0, 0);
					msg.obj = text.getText().toString().getBytes();
					chatUIThreadMessenger.send(msg);
				}
				catch (RemoteException e)
				{
				}

				// �������� ��������� � �������
				chatClient.sendMessage(text.getText().toString());
				text.getText().clear();
			}
		});
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
			chatClient.close();

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

	/**
	 * ������� �������������� ����������� � Server'� � ������ ����������
	 * Service.
	 */
	private void tryConnectToServer()
	{
		// TODO: ��� ��� ����� ������ ����������� ���� Messenger ��� Server'�?
		// ��� ��� ������ �� ��������� ����� � Service?!
		if (chatClient.connectToServer(chatUIThreadMessenger) == false)
			this.finish();
	};
}