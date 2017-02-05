package by.spontaneous.bluetoothchat;

import java.util.ArrayList;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import by.spontaneous.bluetoothchat.Services.GUIRequestCode;
import by.spontaneous.bluetoothchat.Services.IChatClient;

public class ConnectionFragment extends Fragment {
    /** Точка доступа к ChatService. */
    private IChatClient fChatClient = null;

    /** Список сообщений. */
    private final ArrayList<String> fMessages = new ArrayList<String>();
    /** Список-адаптер сообщений. */
    private ArrayAdapter<String> fMessagesAdapter;

    /**
     * Messenger, позволяющий получить доступ к Thread'у ChatActivity и её
     * элементам интерфейса. В связи с тем, что при повороте экрана они
     * пересоздаются, он тоже должен каждый раз обновляться.
     */
    private Messenger fChatUIThreadMessenger;

    @Override
    public void onAttach(Activity activity) {
	super.onAttach(activity);

	// Даёт фрагменту пережить поворот экрана того Activity, которому он
	// принадлежит. При false фрагмент уничтожается при каждом повороте.
	setRetainInstance(true);

	// Возникают какие-то неопределённые проблемы, если инициализировать его
	// сразу
	fMessagesAdapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, fMessages) {
	    @Override
	    public View getView(int position, View convertView, ViewGroup parent) {
		final View view = super.getView(position, convertView, parent);
		final String str = getItem(position);
		((TextView) view.findViewById(android.R.id.text1)).setText(str);
		return view;
	    };
	};
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	final View viewFragment = inflater.inflate(R.layout.f_communication, container);

	final Button btn = (Button) viewFragment.findViewById(R.id.buttonSendMessage);
	btn.setOnClickListener(new View.OnClickListener() {
	    @Override
	    public void onClick(View v) {
		final EditText text = (EditText) viewFragment.findViewById(R.id.et_input_message);

		if (!text.getText().toString().equals("")) {
		    // Отправка сообщения самому себе
		    try {
			final Message msg = Message.obtain(null, GUIRequestCode._MESSAGE.getId(), 0, 0);
			msg.obj = text.getText().toString();
			fChatUIThreadMessenger.send(msg);
		    } catch (RemoteException e) {
		    }

		    // Отправка сообщения в соккеты
		    fChatClient.sendResponse(text.getText().toString());
		    text.getText().clear();
		}
	    };
	});

	final ChatActivity activity = (ChatActivity) getActivity();

	final ListView listView = (ListView) viewFragment.findViewById(R.id.listViewChat);
	listView.setAdapter(fMessagesAdapter);

	fChatUIThreadMessenger = new Messenger(new Handler() {
	    @Override
	    public void handleMessage(Message msg) {
		final Button buttonSend = (Button) viewFragment.findViewById(R.id.buttonSendMessage);

		String str;
		switch (GUIRequestCode.fromId((byte) msg.what)) {
		// Доступ к выводу Toast'ов для Thread'ов прослушивающих
		// Socket'ы
		case _TOAST:
		    str = (String) msg.obj;
		    Toast.makeText(activity.getBaseContext(), str, Toast.LENGTH_SHORT).show();
		    break;
		// Вывод сообщений в лог чата
		case _MESSAGE:
		    str = (String) msg.obj;
		    fMessages.add(str);
		    fMessagesAdapter.notifyDataSetChanged();

		    // TODO: может найти какое-то событие обновление
		    // адаптора
		    // (событие обновления ListView не происходит, если
		    // добавленное сообщение сейчас вне зоны видимости)?
		    // TODO: какой-то подвох остаётся с этими прокрутками
		    // (иногда перестаёт прокручивать после поворота экрана)
		    final ListView listView = (ListView) viewFragment.findViewById(R.id.listViewChat);
		    listView.smoothScrollToPosition(fMessagesAdapter.getCount() - 1);
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
		case _LONELINESS:
		    if (activity.mApplicationMode == ApplicationMode.CLIENT) {
			Toast.makeText(activity.getBaseContext(), "Сервер отключился!", Toast.LENGTH_SHORT).show();
			activity.finish();
		    }
		    break;
		case _CUT:
		    try {
			((Thread) msg.obj).join();
		    } catch (InterruptedException e) {
			Toast.makeText(activity.getBaseContext(), "Ошибка Thread.join(): " + e.getMessage(),
				Toast.LENGTH_SHORT).show();
		    }
		    break;
		case _QUIT:
		    activity.finish();
		    break;
		// Исключительная ситуация
		case _UNKNOWN:
		    Toast.makeText(activity.getBaseContext(), "Messenger: неопределённый тип сообщения!",
			    Toast.LENGTH_LONG).show();
		    break;
		default:
		    super.handleMessage(msg);
		}
	    };
	});

	// Toast.makeText(activity.getBaseContext(), "ConnectionFragment
	// перерисован()!", Toast.LENGTH_SHORT).show();

	return viewFragment;
    };

    @Override
    public void onDestroy() {
	if (getActivity().isFinishing())
	    if (fChatClient != null)
		fChatClient.stopConnection();

	super.onDestroy();
    };

    /**
     * Попытка передать Messenger для Thread'ов выбранного Service. Использует
     * Messenger, и соответственно может быть использован только после
     * инициализации Messenger'а.
     */
    public void updateChatClient(IChatClient client) {
	final ChatActivity activity = (ChatActivity) getActivity();

	if (client != null)
	    if (client != fChatClient) {
		if (fChatClient == null) {
		    // Инициализируем клиент
		    fChatClient = client;

		    if (!fChatClient.startConnection(fChatUIThreadMessenger))
			activity.finish();
		} else {
		    // TODO: Заменяем один клиент другим, а здесь это не норма
		}
	    } else {
		// TODO: Получили тот же клиент
	    }
    };
}