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

public class ConnectionFragment extends Fragment
{
	/** ����� ������� � ChatService. */
	private IChatClient fChatClient = null;

	/** ������ ���������. */
	private final ArrayList<String> fMessages = new ArrayList<String>();
	/** ������-������� ���������. */
	private ArrayAdapter<String> fMessagesAdapter;

	/**
	 * Messenger, ����������� �������� ������ � Thread'� ChatActivity � �
	 * ��������� ����������. � ����� � ���, ��� ��� �������� ������ ���
	 * �������������, �� ���� ������ ������ ��� �����������.
	 */
	private Messenger fChatUIThreadMessenger;

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// ��� ��������� �������� ������� ������ ���� Activity, �������� ��
		// �����������. ��� false �������� ������������ ��� ������ ��������.
		setRetainInstance(true);

		// ��������� �����-�� ������������� ��������, ���� ���������������� ���
		// �����
		fMessagesAdapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, fMessages)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				final View view = super.getView(position, convertView, parent);
				final String str = getItem(position);
				((TextView) view.findViewById(android.R.id.text1)).setText(str);
				return view;
			};
		};
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		final View viewFragment = inflater.inflate(R.layout.f_communication, container);

		final Button btn = (Button) viewFragment.findViewById(R.id.buttonSendMessage);
		btn.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				final EditText text = (EditText) viewFragment.findViewById(R.id.et_input_message);

				if (!text.getText().toString().equals(""))
				{
					// �������� ��������� ������ ����
					try
					{
						final Message msg = Message.obtain(null, GUIRequestCode._MESSAGE.getId(), 0, 0);
						msg.obj = text.getText().toString();
						fChatUIThreadMessenger.send(msg);
					}
					catch (RemoteException e)
					{
					}

					// �������� ��������� � �������
					fChatClient.sendResponse(text.getText().toString());
					text.getText().clear();
				}
			};
		});

		final ChatActivity activity = (ChatActivity) getActivity();

		final ListView listView = (ListView) viewFragment.findViewById(R.id.listViewChat);
		listView.setAdapter(fMessagesAdapter);

		fChatUIThreadMessenger = new Messenger(new Handler()
		{
			@Override
			public void handleMessage(Message msg)
			{
				final Button buttonSend = (Button) viewFragment.findViewById(R.id.buttonSendMessage);

				String str;
				switch (GUIRequestCode.fromId((byte) msg.what))
				{
				// ������ � ������ Toast'�� ��� Thread'�� ��������������
				// Socket'�
				case _TOAST:
					str = (String) msg.obj;
					Toast.makeText(activity.getBaseContext(), str, Toast.LENGTH_SHORT).show();
					break;
				// ����� ��������� � ��� ����
				case _MESSAGE:
					str = (String) msg.obj;
					fMessages.add(str);
					fMessagesAdapter.notifyDataSetChanged();

					// TODO: ����� ����� �����-�� ������� ����������
					// ��������
					// (������� ���������� ListView �� ����������, ����
					// ����������� ��������� ������ ��� ���� ���������)?
					// TODO: �����-�� ������ ������� � ����� �����������
					// (������ �������� ������������ ����� �������� ������)
					listView.smoothScrollToPosition(fMessagesAdapter.getCount() - 1);
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
				case _LONELINESS:
					if (activity.mApplicationMode == ApplicationMode.CLIENT)
					{
						Toast.makeText(activity.getBaseContext(), "������ ����������!", Toast.LENGTH_SHORT).show();
						activity.finish();
					}
					break;
				case _QUIT:
					activity.finish();
					break;
				// �������������� ��������
				case _UNKNOWN:
					Toast.makeText(activity.getBaseContext(), "Messenger: ������������� ��� ���������!",
							Toast.LENGTH_LONG).show();
					break;
				default:
					super.handleMessage(msg);
				}
			};
		});

		// Toast.makeText(activity.getBaseContext(), "ConnectionFragment �����������()!", Toast.LENGTH_SHORT).show();

		return viewFragment;
	};

	@Override
	public void onDestroy()
	{
		if (getActivity().isFinishing())
			if (fChatClient != null)
				fChatClient.stopConnection();

		super.onDestroy();
	};
	
	/**
	 * ������� �������� Messenger ��� Thread'�� ���������� Service. ����������
	 * Messenger, � �������������� ����� ���� ����������� ������ �����
	 * ������������� Messenger'�.
	 */
	protected void updateChatClient(IChatClient client)
	{
		final ChatActivity activity = (ChatActivity) getActivity();

		if (client != null)
			if (client != fChatClient)
			{
				if (fChatClient == null)
				{
					// �������������� ������
					fChatClient = client;

					if (!fChatClient.startConnection(fChatUIThreadMessenger))
						activity.finish();
				}
				else
				{
					// TODO: �������� ���� ������ ������, � ����� ��� �� �����
				}
			}
			else
			{
				// TODO: �������� ��� �� ������
			}
	};
}