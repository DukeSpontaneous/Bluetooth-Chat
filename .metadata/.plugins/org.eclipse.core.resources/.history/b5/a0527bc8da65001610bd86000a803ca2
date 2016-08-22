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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
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

public class MainActivity extends Activity
{
	/** Вариант допустимого кода для включения Bluetooth устройства. */
	private static final int REQUEST_ENABLE_BT = 1;
	/** Адаптер умолчательного Bluetooth устройсва Android. */
	private BluetoothAdapter bluetoothAdapter;

	/** Список обнаруженых потенциальных серверов BluetoothDevice. */
	private ArrayList<BluetoothDevice> discoveredDevices;
	/** Список-адаптор обнаруженных потенциальных серверов. */
	private ArrayAdapter<BluetoothDevice> listAdapter;
	
	/** Приёмщик сигналов об обнаружении новых устройств. */
	private BroadcastReceiver discoverDevicesReceiver;
	/** Приёмщик сигналов об окончании процедуры поиска. */
	private BroadcastReceiver discoveryFinishedReceiver;

	/** Messenger, позволяющий получить доступ к Thread'у MainActivity. */
	private Messenger msgHandler = new Messenger(new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case 0:
				String str = (String) msg.obj;
				Toast.makeText(getBaseContext(), str, Toast.LENGTH_LONG).show();
				break;

			default:
				super.handleMessage(msg);
			}
		}
	});

	private ChatServerService chatServerService;
	private ChatClientService chatClientService;

	/** Defines callbacks for service binding, passed to bindService() */
	private final ServiceConnection serverConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// Получение ссылки на объект-сервис при успешном подключении к
			// chatServerService
			chatServerService = ((ChatServerService.LocalBinder) service).getService();
			chatServerService.setMessenger(msgHandler);
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "Неявное отключение сервиса ServiceConnection serverConnection...",
					Toast.LENGTH_LONG).show();
			chatServerService = null;
		}
	};

	private final ServiceConnection clientConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// Получение ссылки на объект-сервис при успешном подключении к
			// chatClientService
			chatClientService = ((ChatClientService.LocalBinder) service).getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "Неявное отключение сервиса ServiceConnection clientConnection...",
					Toast.LENGTH_LONG).show();
			chatClientService = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(bluetoothAdapter == null)
		{
			this.finish();
			Toast.makeText(getBaseContext(), "Ошибка: не удалось получить BluetoothAdapter.",
					Toast.LENGTH_LONG).show();
		}
		
		final Switch sw = (Switch) findViewById(R.id.switch1);
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

				if (!chatServerService.createServer(bluetoothAdapter))
				{
					Toast.makeText(getBaseContext(), "Ошибка ChatServerService: не удалось создать сервер.",
							Toast.LENGTH_LONG).show();
					return;
				}

				chatClientService.setChatClient(chatServerService);
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

				BluetoothDevice device = listAdapter.getItem(position);
				startConnection(device);
			}
		});

		discoveredDevices = new ArrayList<BluetoothDevice>();

		listAdapter = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_list_item_1, discoveredDevices)
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

		listView.setAdapter(listAdapter);

		if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled())
		{
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		// Bind to LocalService
		Intent intentS = new Intent(getApplicationContext(), ChatServerService.class);
		bindService(intentS, serverConnection, Context.BIND_AUTO_CREATE);

		Intent intentC = new Intent(getApplicationContext(), ChatClientService.class);
		bindService(intentC, clientConnection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	protected void onStop()
	{
		if (isFinishing())
		{
			final Switch sw = (Switch) findViewById(R.id.switch1);
			sw.setChecked(false);
			
			// Но привязанные службы уничтожаются и при простом закрытии Activity
			// осуществившего привязку.

			// Unbind from the service
			if (chatServerService != null)
			{
				unbindService(serverConnection);
			}
			if (chatClientService != null)
			{
				unbindService(clientConnection);
			}	
		}

		super.onStop();
	}

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
	}

	/**
	 * Метод, инициализирующий поиск Bluetooth-устройств посредством системного
	 * сервиса, вызываемого методом BluetoothAdapter.startDiscovery().
	 */
	private void discoverDevices(boolean isChecked)
	{
		if (isChecked)
		{
			discoveredDevices.clear();
			listAdapter.notifyDataSetChanged();

			if (discoverDevicesReceiver == null)
			{
				discoverDevicesReceiver = new BroadcastReceiver()
				{
					@Override
					public void onReceive(Context context, Intent intent)
					{
						String action = intent.getAction();

						if (BluetoothDevice.ACTION_FOUND.equals(action))
						{
							BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

							if (!discoveredDevices.contains(device))
							{
								discoveredDevices.add(device);

								listAdapter.notifyDataSetChanged();
							}
						}
					}
				};
			}

			if (discoveryFinishedReceiver == null)
			{
				discoveryFinishedReceiver = new BroadcastReceiver()
				{
					@Override
					public void onReceive(Context context, Intent intent)
					{
						// if (progressDialog != null)
						// progressDialog.dismiss();

						Toast.makeText(getBaseContext(), "Сканирование завершено. Повторное сканирование...",
								Toast.LENGTH_LONG).show();

						// unregisterReceiver(discoveryFinishedReceiver);

						bluetoothAdapter.startDiscovery();
					}
				};
			}

			registerReceiver(discoverDevicesReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
			registerReceiver(discoveryFinishedReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

			// progressDialog = ProgressDialog.show(this, "Поиск устройств",
			// "Подождите...");

			bluetoothAdapter.startDiscovery();
		}
		else
		{
			bluetoothAdapter.cancelDiscovery();
			unregisterReceiver(discoveryFinishedReceiver);
			unregisterReceiver(discoverDevicesReceiver);
		}
	}

	/**
	 * Метод, принимающий устройство-сервер BluetoothDevice device, или null,
	 * если устройство само является сервером, и передающий управление
	 * ChatActivity.
	 */
	private void startConnection(BluetoothDevice device)
	{
		chatClientService.init(device);

		Intent chatActivityIntent = new Intent(this, ChatActivity.class);
		startActivity(chatActivityIntent);
	}
}

	//TODO: подумать на счёт реализации независимого ChatServerService,
	// способного существовать без связи (этот Activity будет большую часть
	// времени находиться в режиме onStop, где он может быть временно (?)
	// уничтожен из-за нехватке памяти).
