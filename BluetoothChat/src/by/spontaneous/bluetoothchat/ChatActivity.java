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
	/** Вариант допустимого кода для включения Bluetooth устройства. */
	private static final int REQUEST_DISCOVERABLE_BT = 2;
	/** Адаптер умолчательного Bluetooth устройсва Android. */
	private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	/** Список сообщений. */
	private ArrayList<String> mMessages;
	/** Список-адаптер сообщений. */
	private ArrayAdapter<String> mMessagesAdapter;

	/** Точка доступа к ChatClientService. */
	private IChatClient mChatClient = null;

	/** Точка доступа к ChatServerService. */
	private ChatServerService mChatServerService;
	/** Точка доступа к ChatClientService. */
	private ChatClientService mChatClientService;

	private ApplicationMode mApplicationMode;
	// private ProgressDialog mProgressDialog;

	/** Messenger, позволяющий получить доступ к Thread'у ChatActivity. */
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
			// Доступ к выводу Toast'ов для Thread'ов прослушивающих Socket'ы
			case _TOAST:
				str = (String) msg.obj;
				Toast.makeText(getBaseContext(), str, Toast.LENGTH_LONG).show();
				break;
			// Вывод сообщений в лог чата
			case _MESSAGE:
				str = (String) msg.obj;
				mMessages.add(str);
				mMessagesAdapter.notifyDataSetChanged();

				// TODO: может найти какое-то событие обновление адаптора
				// (событие обновления ListView не происходит, если добавленное
				// сообщение сейчас вне зоны видимости)?
				listView.smoothScrollToPosition(mMessagesAdapter.getCount() - 1);
				break;
			case _BLOCK:
				// mProgressDialog.show();
				buttonSend.setText("Ожидание доставки...");
				buttonSend.setEnabled(false);
				break;
			case _UNBLOCK:
				// mProgressDialog.dismiss();
				buttonSend.setText("Отправить");
				buttonSend.setEnabled(true);
				break;
			case _QUIT:
				quitChatActivity();
				break;
			// Исключительная ситуация
			case _UNKNOWN:
				Toast.makeText(getBaseContext(), "Messenger: неопределённый тип сообщения!", Toast.LENGTH_LONG).show();
				break;
			default:
				super.handleMessage(msg);
			}
		}
	});

	/** Объект подключения к Service'у ChatServerService */
	private final ServiceConnection mServerConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// Получение ссылки на объект-сервис при успешном подключении к
			// chatServerService
			mChatServerService = ((ChatServerService.LocalBinder) service).getService();
			mChatClient = mChatServerService;

			tryConnectToService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "Неявное отключение сервиса ServiceConnection serverConnection...",
					Toast.LENGTH_LONG).show();
			mChatClient = null;
			mChatServerService = null;
		}
	};
	/** Объект подключения к Service'у ChatClientService */
	private final ServiceConnection mClientConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// Получение ссылки на объект-сервис при успешном подключении к
			// chatClientService
			mChatClientService = ((ChatClientService.LocalBinder) service).getService();
			mChatClient = mChatClientService;

			tryConnectToService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Toast.makeText(getBaseContext(), "Неявное отключение сервиса ServiceConnection clientConnection...",
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
			Toast.makeText(getBaseContext(), "Ошибка: не удалось получить BluetoothAdapter.", Toast.LENGTH_LONG).show();
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
					// Отправка сообщения самому себе
					try
					{
						final Message msg = Message.obtain(null, GUIRequestCode._MESSAGE.getId(), 0, 0);
						msg.obj = text.getText().toString();
						mChatUIThreadMessenger.send(msg);
					}
					catch (RemoteException e)
					{
					}

					// Отправка сообщения в соккеты
					mChatClient.sendResponse(text.getText().toString());
					text.getText().clear();
				}
			}
		});

		// mProgressDialog = new ProgressDialog(this);
		// mProgressDialog.setTitle("Отправка сообщения");
		// mProgressDialog.setMessage("Ожидание подтверждения...");
	};

	@Override
	protected void onStart()
	{
		super.onStart();

		// Bind to LocalService

		// Выбираем целевой сервис
		Intent intent;

		switch (mApplicationMode)
		{
		case SERVER:
			intent = new Intent(getApplicationContext(), ChatServerService.class);
			bindService(intent, mServerConnection, Context.BIND_AUTO_CREATE);

			// Если режим обнаружаемости для неспаренных устройств не
			// активирован, то...
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
			Toast.makeText(getBaseContext(), "Ошибка ChatActivity: неопределённый режим запуска!", Toast.LENGTH_LONG)
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
	 * Обработчик кодов, возвращённых после обработки запроса
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
				Toast.makeText(getBaseContext(), "Не удалось сделать устройство видимым для Bluetooth-клиентов.",
						Toast.LENGTH_LONG).show();
				break;
			}
			case 1:
			{
				Toast.makeText(getBaseContext(), "Устройство будет видимо для Bluetooth-клиентов неограниченное время.",
						Toast.LENGTH_LONG).show();
				break;
			}
			default:
			{
				Toast.makeText(getBaseContext(),
						"Устройство будет видимо для Bluetooth-клиентов " + resultCode + " секунд.", Toast.LENGTH_LONG)
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

	/** Попытка передать Messenger для Thread'ов выбранного Service. */
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