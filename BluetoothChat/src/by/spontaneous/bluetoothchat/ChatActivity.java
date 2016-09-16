package by.spontaneous.bluetoothchat;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;
import by.spontaneous.bluetoothchat.Services.ChatClientService;
import by.spontaneous.bluetoothchat.Services.ChatServerService;

public final class ChatActivity extends Activity
{
	/** ������ ����������� � Service'� ChatServerService */
	private final ServiceConnection mServerConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// ��������� ������� ������� ��� �������� �����������
			final ChatServerService mChatServerService = ((ChatServerService.LocalBinder) service).getService();
			mConnectionFragment.updateChatClient(mChatServerService);
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "������� ���������� ������� Server...", Toast.LENGTH_LONG).show();
		}
	};
	/** ������ ����������� � Service'� ChatClientService */
	private final ServiceConnection mClientConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// ��������� ������� ������� ��� �������� �����������
			final ChatClientService mChatClientService = ((ChatClientService.LocalBinder) service).getService();
			mConnectionFragment.updateChatClient(mChatClientService);
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "������� ���������� ������� Client...", Toast.LENGTH_LONG).show();
		}
	};

	/** ������� ����������� ���� ��� ��������� Bluetooth ����������. */
	private static final int REQUEST_DISCOVERABLE_BT = 2;
	/** ������� �������������� Bluetooth ��������� Android. */
	private static final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();

	private ConnectionFragment mConnectionFragment;

	protected ApplicationMode mApplicationMode;
	// private ProgressDialog mProgressDialog;

	// TODO: �� ����� �������, ����� ���������� ��� �������, � �� ����, ���
	// ������ �������� �������. ���� ������ �������� ���, ��� ����� �����������
	// ������ ������ �� ��������.
	@Override
	public void onAttachFragment(Fragment fragment)
	{
		super.onAttachFragment(fragment);
		mConnectionFragment = (ConnectionFragment) fragment;
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat);

		if (BLUETOOTH_ADAPTER == null)
		{
			finish();
			Toast.makeText(getBaseContext(), "������: �� ������� �������� BluetoothAdapter.", Toast.LENGTH_LONG).show();
		}

		int modeCode = getIntent().getIntExtra(getResources().getString(R.string.request_code_mode),
				ApplicationMode.UNKNOWN.getId());
		mApplicationMode = ApplicationMode.fromId(modeCode);

		// First time init, create the UI.
		if (savedInstanceState == null)
		{
			switch (mApplicationMode)
			{
			case SERVER:
			{
				// ���� ����� �������������� ��� ����������� ��������� ��
				// �����������, ��...
				if (BLUETOOTH_ADAPTER.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
				{
					Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
					discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
					startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
				}
				break;
			}
			case UNKNOWN:
			{
				this.finish();
				break;
			}
			default:
			{
				break;
			}
			}
		}

		// mProgressDialog = new ProgressDialog(this);
		// mProgressDialog.setTitle("�������� ���������");
		// mProgressDialog.setMessage("�������� �������������...");

		// �������� �������� Service � ����������� Activity
		Intent intent;
		switch (mApplicationMode)
		{
		case SERVER:
			intent = new Intent(getApplicationContext(), ChatServerService.class);
			bindService(intent, mServerConnection, Context.BIND_AUTO_CREATE);
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
	protected void onDestroy()
	{
		switch (mApplicationMode)
		{
		case SERVER:
			unbindService(mServerConnection);
			break;
		case CLIENT:
			unbindService(mClientConnection);
			break;
		default:
			Toast.makeText(getBaseContext(), "������ ChatActivity: ������������� ����� �������!", Toast.LENGTH_LONG)
					.show();
			break;
		}

		super.onDestroy();
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
				Toast.makeText(getBaseContext(), "�� ������� ������� ���������� ������� ��� Bluetooth-��������.",
						Toast.LENGTH_SHORT).show();
				break;
			case 1:
				Toast.makeText(getBaseContext(), "���������� ����� ������ ��� Bluetooth-�������� �������������� �����.",
						Toast.LENGTH_SHORT).show();
				break;
			default:
				Toast.makeText(getBaseContext(),
						"���������� ����� ������ ��� Bluetooth-�������� " + resultCode + " ������.", Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
		default:
		{
			break;
		}
		}
	};
}