package by.spontaneous.bluetoothchat;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import by.spontaneous.bluetoothchat.Services.ChatClientService;

public final class MainActivity extends Activity
{
	/** Вариант допустимого кода для включения Bluetooth устройства. */
	private static final int REQUEST_ENABLE_BT = 1;
	/** Адаптер умолчательного Bluetooth устройсва Android. */
	private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	/** Список обнаруженых потенциальных серверов BluetoothDevice. */
	private ArrayList<BluetoothDevice> mDiscoveredDevices;
	/** Список-адаптор обнаруженных потенциальных серверов. */
	private ArrayAdapter<BluetoothDevice> mListAdapter;

	/** Приёмщик сигналов об обнаружении новых устройств. */
	private BroadcastReceiver mDiscoverDevicesReceiver;
	/** Приёмщик сигналов об окончании процедуры поиска. */
	private BroadcastReceiver mDiscoveryFinishedReceiver;

	/** Точка доступа к ChatClientService. */
	private ChatClientService mChatClientService;

	/** Объект подключения к Service'у ChatClientService */
	private final ServiceConnection mClientConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// Получение ссылки на объект-сервис при успешном подключении к
			// chatClientService
			mChatClientService = ((ChatClientService.LocalBinder) service).getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "Неявное отключение сервиса ServiceConnection clientConnection...",
					Toast.LENGTH_LONG).show();
			mChatClientService = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		if (mBluetoothAdapter == null)
		{
			this.finish();
			Toast.makeText(getBaseContext(), "Ошибка: не удалось получить BluetoothAdapter.", Toast.LENGTH_LONG).show();
		}

		final Switch sw = (Switch) findViewById(R.id.switchDiscovery);
		sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
		{
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				discoverDevices(isChecked);
			}
		});

		final Button btn = (Button) findViewById(R.id.buttonRunServer);
		btn.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				sw.setChecked(false);

				startConnection(null);
			}
		});

		final ListView listView = (ListView) findViewById(R.id.listViewDevices);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> l, View v, int position, long id)
			{
				sw.setChecked(false);

				BluetoothDevice device = mListAdapter.getItem(position);
				startConnection(device);
			}
		});

		mDiscoveredDevices = new ArrayList<BluetoothDevice>();

		mListAdapter = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_list_item_1, mDiscoveredDevices)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				View view = super.getView(position, convertView, parent);
				final BluetoothDevice device = getItem(position);
				((TextView) view.findViewById(android.R.id.text1)).setText(device.getName());
				return view;
			}
		};

		listView.setAdapter(mListAdapter);

		if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled())
		{
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		// Bind to LocalService
		Intent intentC = new Intent(getApplicationContext(), ChatClientService.class);
		bindService(intentC, mClientConnection, Context.BIND_AUTO_CREATE);
	};

	@Override
	protected void onStop()
	{
		if (isFinishing())
		{
			final Switch sw = (Switch) findViewById(R.id.switchDiscovery);
			sw.setChecked(false);

			// Но привязанные службы уничтожаются и при простом закрытии
			// Activity
			// осуществившего привязку.

			// Unbind from the service
			if (mChatClientService != null)
			{
				unbindService(mClientConnection);
			}
		}

		super.onStop();
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
		// Обработка ответов на запрос активации устройства Bluetooth
		case REQUEST_ENABLE_BT:
		{
			switch (resultCode)
			{
			case RESULT_OK:
			{
				Toast.makeText(getBaseContext(), "Bluetooth успешно активирован!", Toast.LENGTH_LONG).show();
				break;
			}
			case RESULT_CANCELED:
			{
				Toast.makeText(getBaseContext(), "Bluetooth не был активирован...", Toast.LENGTH_LONG).show();
				break;
			}
			default:
			{
				Toast.makeText(getBaseContext(), "Неопределённый результат обработки запроса REQUEST_ENABLE_BT",
						Toast.LENGTH_LONG).show();
				break;
			}
			}

			break;
		}
		default:
		{
			break;
		}
		}
	};

	/**
	 * Метод, инициализирующий поиск Bluetooth-устройств посредством системного
	 * сервиса, вызываемого методом BluetoothAdapter.startDiscovery().
	 */
	private void discoverDevices(boolean isChecked)
	{
		if (isChecked)
		{
			mDiscoveredDevices.clear();
			mListAdapter.notifyDataSetChanged();

			if (mDiscoverDevicesReceiver == null)
			{
				mDiscoverDevicesReceiver = new BroadcastReceiver()
				{
					@Override
					public void onReceive(Context context, Intent intent)
					{
						String action = intent.getAction();

						if (BluetoothDevice.ACTION_FOUND.equals(action))
						{
							BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

							if (!mDiscoveredDevices.contains(device))
							{
								mDiscoveredDevices.add(device);
								mListAdapter.notifyDataSetChanged();
							}
						}
					}
				};
			}

			if (mDiscoveryFinishedReceiver == null)
			{
				mDiscoveryFinishedReceiver = new BroadcastReceiver()
				{
					@Override
					public void onReceive(Context context, Intent intent)
					{
						// if (progressDialog != null)
						// progressDialog.dismiss();

						Toast.makeText(getBaseContext(), "Сканирование завершено. Повторное сканирование...",
								Toast.LENGTH_LONG).show();

						// unregisterReceiver(discoveryFinishedReceiver);

						mBluetoothAdapter.startDiscovery();
					}
				};
			}

			registerReceiver(mDiscoverDevicesReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
			registerReceiver(mDiscoveryFinishedReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

			// progressDialog = ProgressDialog.show(this, "Поиск устройств",
			// "Подождите...");

			mBluetoothAdapter.startDiscovery();
		}
		else
		{
			mBluetoothAdapter.cancelDiscovery();
			unregisterReceiver(mDiscoveryFinishedReceiver);
			unregisterReceiver(mDiscoverDevicesReceiver);
		}
	};

	/**
	 * Метод, принимающий устройство-сервер BluetoothDevice device, или null,
	 * если устройство само является сервером, и передающий управление
	 * ChatActivity.
	 */
	private void startConnection(BluetoothDevice device)
	{
		Intent chatActivityIntent = new Intent(this, ChatActivity.class);
		// Выбранное внешнее серверное устройство равно null?
		int mode = device == null ? ApplicationMode.SERVER.getId() : ApplicationMode.CLIENT.getId();
		chatActivityIntent.putExtra(getResources().getString(R.string.request_code_mode), mode);

		// Если было выбрано серверное устройство, то следует передать его
		// СhatClientService
		if (device != null)
			mChatClientService.setMasterDevice(device);

		startActivity(chatActivityIntent);
	};
}
