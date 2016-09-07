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
import by.spontaneous.bluetoothchat.Services.ChatClientService;
import by.spontaneous.bluetoothchat.Services.ChatServerService;
import by.spontaneous.bluetoothchat.Services.GUIRequestCode;
import by.spontaneous.bluetoothchat.Services.IChatClient;

public final class ChatActivity extends Activity
{
	/** ������� ����������� ���� ��� ��������� Bluetooth ����������. */
	private static final int REQUEST_DISCOVERABLE_BT = 2;
	/** ������� �������������� Bluetooth ��������� Android. */
	private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	/** ������ ���������. */
	private ArrayList<String> mMessages;
	/** ������-������� ���������. */
	private ArrayAdapter<String> mMessagesAdapter;

	/** ����� ������� � ChatClientService. */
	private IChatClient mChatClient = null;

	/** ����� ������� � ChatServerService. */
	private ChatServerService mChatServerService;
	/** ����� ������� � ChatClientService. */
	private ChatClientService mChatClientService;

	private ApplicationMode mApplicationMode;
	// private ProgressDialog mProgressDialog;

	/** Messenger, ����������� �������� ������ � Thread'� ChatActivity. */
	private Messenger mChatUIThreadMessenger = new Messenger(new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			final ListView listView = (ListView) findViewById(R.id.listViewChat);
			final Button buttonSend = (Button) findViewById(R.id.buttonSendMessage);

			String str;
			switch (GUIRequestCode.fromId((byte) msg.what))
			{
			// ������ � ������ Toast'�� ��� Thread'�� �������������� Socket'�
			case _TOAST:
				str = (String) msg.obj;
				Toast.makeText(getBaseContext(), str, Toast.LENGTH_LONG).show();
				break;
			// ����� ��������� � ��� ����
			case _MESSAGE:
				str = (String) msg.obj;
				mMessages.add(str);
				mMessagesAdapter.notifyDataSetChanged();

				// TODO: ����� ����� �����-�� ������� ���������� ��������
				// (������� ���������� ListView �� ����������, ���� �����������
				// ��������� ������ ��� ���� ���������)?
				listView.smoothScrollToPosition(mMessagesAdapter.getCount() - 1);
				break;
			case _BLOCK:
				// mProgressDialog.show();
				buttonSend.setText("�������� ��������...");
				buttonSend.setEnabled(false);
				break;
			case _UNBLOCK:
				// mProgressDialog.dismiss();
				buttonSend.setText("���������");
				buttonSend.setEnabled(true);
				break;
			case _QUIT:
				quitChatActivity();
				break;
			// �������������� ��������
			case _UNKNOWN:
				Toast.makeText(getBaseContext(), "Messenger: ������������� ��� ���������!", Toast.LENGTH_LONG).show();
				break;
			default:
				super.handleMessage(msg);
			}
		}
	});

	/** ������ ����������� � Service'� ChatServerService */
	private final ServiceConnection mServerConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// ��������� ������ �� ������-������ ��� �������� ����������� �
			// chatServerService
			mChatServerService = ((ChatServerService.LocalBinder) service).getService();
			mChatClient = mChatServerService;

			tryConnectToService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "������� ���������� ������� ServiceConnection serverConnection...",
					Toast.LENGTH_LONG).show();
			mChatClient = null;
			mChatServerService = null;
		}
	};
	/** ������ ����������� � Service'� ChatClientService */
	private final ServiceConnection mClientConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// ��������� ������ �� ������-������ ��� �������� ����������� �
			// chatClientService
			mChatClientService = ((ChatClientService.LocalBinder) service).getService();
			mChatClient = mChatClientService;

			tryConnectToService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "������� ���������� ������� ServiceConnection clientConnection...",
					Toast.LENGTH_LONG).show();
			mChatClient = null;
			mChatClientService = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat);

		if (mBluetoothAdapter == null)
		{
			this.finish();
			Toast.makeText(getBaseContext(), "������: �� ������� �������� BluetoothAdapter.", Toast.LENGTH_LONG).show();
		}

		int modeCode = getIntent().getIntExtra(getResources().getString(R.string.request_code_mode),
				ApplicationMode.UNKNOWN.getId());
		mApplicationMode = ApplicationMode.fromId(modeCode);
		if (mApplicationMode == ApplicationMode.UNKNOWN)
		{
			this.finish();
		}

		mMessages = new ArrayList<String>();
		mMessagesAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mMessages)
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
		listView.setAdapter(mMessagesAdapter);

		final Button btn = (Button) findViewById(R.id.buttonSendMessage);
		btn.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				final EditText text = (EditText) findViewById(R.id.et_input_message);

				if (!text.getText().toString().equals(""))
				{
					// �������� ��������� ������ ����
					try
					{
						final Message msg = Message.obtain(null, GUIRequestCode._MESSAGE.getId(), 0, 0);
						msg.obj = text.getText().toString();
						mChatUIThreadMessenger.send(msg);
					}
					catch (RemoteException e)
					{
					}

					// �������� ��������� � �������
					mChatClient.sendResponse(text.getText().toString());
					text.getText().clear();
				}
			}
		});

		// mProgressDialog = new ProgressDialog(this);
		// mProgressDialog.setTitle("�������� ���������");
		// mProgressDialog.setMessage("�������� �������������...");
	};

	@Override
	protected void onStart()
	{
		super.onStart();

		// Bind to LocalService

		// �������� ������� ������
		Intent intent;

		switch (mApplicationMode)
		{
		case SERVER:
			intent = new Intent(getApplicationContext(), ChatServerService.class);
			bindService(intent, mServerConnection, Context.BIND_AUTO_CREATE);

			// ���� ����� �������������� ��� ����������� ��������� ��
			// �����������, ��...
			if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
			{
				Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
				startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
			}
			break;
		case CLIENT:
			intent = new Intent(getApplicationContext(), ChatClientService.class);
			bindService(intent, mClientConnection, Context.BIND_AUTO_CREATE);
			break;
		default:
			Toast.makeText(getBaseContext(), "������ ChatActivity: ������������� ����� �������!", Toast.LENGTH_LONG)
					.show();
			break;
		}
	};

	@Override
	protected void onStop()
	{
		if (isFinishing())
		{
			mChatClient.closeChatClient();
		}

		if (mChatServerService != null)
		{
			unbindService(mServerConnection);
			mChatServerService = null;
		}

		if (mChatClientService != null)
		{
			unbindService(mClientConnection);
			mChatClientService = null;
		}

		mChatClient = null;

		super.onStop();
	};

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
		if (!mChatClient.connectToServer(mChatUIThreadMessenger))
			quitChatActivity();
	};

	private void quitChatActivity()
	{
		this.finish();
	};
}