package by.spontaneous.bluetoothchat.Services;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import by.spontaneous.bluetoothchat.ConnectionFragment;

public class ServiceBinding<T extends ChatService<T>> implements ServiceConnection {

    private final ConnectionFragment mConnectionFragment;

    /** Точка доступа к <T extends ChatService<T>>. */
    private T mChatService;

    public ServiceBinding() {
	mConnectionFragment = null;
    }

    public ServiceBinding(ConnectionFragment f) {
	mConnectionFragment = f;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
	// Получение объекта сервиса при успешном подключении	
	mChatService = ((T.LocalBinder) service).getService();
	if(mConnectionFragment != null)
	    mConnectionFragment.updateChatClient(mChatService);
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
    }
    
    public T getService() {
	return mChatService;
    }
}
