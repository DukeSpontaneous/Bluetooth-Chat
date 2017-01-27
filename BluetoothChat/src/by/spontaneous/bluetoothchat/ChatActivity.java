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

public class ChatActivity extends Activity {
    /** Объект подключения к Service'у ChatServerService */
    private final ServiceConnection mServerConnection = new ServiceConnection() {
	@Override
	public void onServiceConnected(ComponentName className, IBinder service) {
	    // Получение объекта сервиса при успешном подключении
	    final ChatServerService mChatServerService = ((ChatServerService.LocalBinder) service).getService();
	    mConnectionFragment.updateChatClient(mChatServerService);
	}

	@Override
	public void onServiceDisconnected(ComponentName arg0) {
	    Toast.makeText(getBaseContext(), "Неявное отключение сервиса Server...", Toast.LENGTH_LONG).show();
	}
    };
    /** Объект подключения к Service'у ChatClientService */
    private final ServiceConnection mClientConnection = new ServiceConnection() {
	@Override
	public void onServiceConnected(ComponentName className, IBinder service) {
	    // Получение объекта сервиса при успешном подключении
	    final ChatClientService mChatClientService = ((ChatClientService.LocalBinder) service).getService();
	    mConnectionFragment.updateChatClient(mChatClientService);
	}

	@Override
	public void onServiceDisconnected(ComponentName arg0) {
	    Toast.makeText(getBaseContext(), "Неявное отключение сервиса Client...", Toast.LENGTH_LONG).show();
	}
    };

    /** Вариант допустимого кода для включения Bluetooth устройства. */
    private static final int REQUEST_DISCOVERABLE_BT = 2;
    /** Адаптер умолчательного Bluetooth устройсва Android. */
    private static final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();

    
    private ConnectionFragment mConnectionFragment;

    protected ApplicationMode mApplicationMode;
    // private ProgressDialog mProgressDialog;

    // TODO: не очень понятно, когда происходит это событие, и не факт, что
    // раньше привязки сервиса. Хотя сейчас работает так, как будто срабатывает
    // раньше ответа на привязку.
    @Override
    public void onAttachFragment(Fragment fragment) {
	super.onAttachFragment(fragment);
	mConnectionFragment = (ConnectionFragment) fragment;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.chat);

	if (BLUETOOTH_ADAPTER == null) {
	    finish();
	    Toast.makeText(getBaseContext(), "Ошибка: не удалось получить BluetoothAdapter.", Toast.LENGTH_LONG).show();
	}

	int modeCode = getIntent().getIntExtra(getResources().getString(R.string.request_code_mode),
		ApplicationMode.UNKNOWN.getId());
	mApplicationMode = ApplicationMode.fromId(modeCode);

	// First time init, create the UI.
	if (savedInstanceState == null) {
	    switch (mApplicationMode) {
	    case SERVER: {
		// Если режим обнаружаемости для неспаренных устройств не
		// активирован, то...
		if (BLUETOOTH_ADAPTER.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
		    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
		    startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
		}
		break;
	    }
	    case UNKNOWN: {
		this.finish();
		break;
	    }
	    default: {
		break;
	    }
	    }
	}

	// mProgressDialog = new ProgressDialog(this);
	// mProgressDialog.setTitle("Отправка сообщения");
	// mProgressDialog.setMessage("Ожидание подтверждения...");

	// Привязка целевого Service к актуальному Activity
	Intent intent;
	switch (mApplicationMode) {
	case SERVER:
	    intent = new Intent(getApplicationContext(), ChatServerService.class);
	    bindService(intent, mServerConnection, Context.BIND_AUTO_CREATE);
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
    protected void onDestroy() {
	switch (mApplicationMode) {
	case SERVER:
	    unbindService(mServerConnection);
	    break;
	case CLIENT:
	    unbindService(mClientConnection);
	    break;
	default:
	    Toast.makeText(getBaseContext(), "Ошибка ChatActivity: неопределённый режим запуска!", Toast.LENGTH_LONG)
		    .show();
	    break;
	}

	super.onDestroy();
    };

    /**
     * Обработчик кодов, возвращённых после обработки запроса
     * REQUEST_DISCOVERABLE_BT.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	switch (requestCode) {
	case REQUEST_DISCOVERABLE_BT: {
	    switch (resultCode) {
	    case RESULT_CANCELED:
		Toast.makeText(getBaseContext(), "Не удалось сделать устройство видимым для Bluetooth-клиентов.",
			Toast.LENGTH_SHORT).show();
		break;
	    case 1:
		Toast.makeText(getBaseContext(), "Устройство будет видимо для Bluetooth-клиентов неограниченное время.",
			Toast.LENGTH_SHORT).show();
		break;
	    default:
		Toast.makeText(getBaseContext(),
			"Устройство будет видимо для Bluetooth-клиентов " + resultCode + " секунд.", Toast.LENGTH_SHORT)
			.show();
		break;
	    }
	}
	default: {
	    break;
	}
	}
    };
}