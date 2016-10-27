package org.briarproject.android.threaded;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.view.MenuItem;
import android.view.View;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.android.threaded.ThreadListController.ThreadListListener;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.android.view.TextInputView;
import org.briarproject.android.view.TextInputView.TextInputListener;
import org.briarproject.api.clients.NamedGroup;
import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.util.Collection;
import java.util.logging.Logger;

import static android.support.design.widget.Snackbar.make;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public abstract class ThreadListActivity<G extends NamedGroup, I extends ThreadItem, H extends PostHeader>
		extends BriarActivity
		implements ThreadListListener<H>, TextInputListener,
		ThreadItemListener<I> {

	protected static final String KEY_INPUT_VISIBILITY = "inputVisibility";
	protected static final String KEY_REPLY_ID = "replyId";

	private static final Logger LOG =
			Logger.getLogger(ThreadListActivity.class.getName());

	protected ThreadItemAdapter<I> adapter;
	protected BriarRecyclerView list;
	protected TextInputView textInput;
	protected GroupId groupId;
	private MessageId replyId;

	protected abstract ThreadListController<G, I, H> getController();

	@CallSuper
	@Override
	@SuppressWarnings("ConstantConditions")
	public void onCreate(final Bundle state) {
		super.onCreate(state);

		setContentView(getLayout());

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId in intent.");
		groupId = new GroupId(b);
		getController().setGroupId(groupId);

		textInput = (TextInputView) findViewById(R.id.text_input_container);
		textInput.setVisibility(GONE);
		textInput.setListener(this);
		list = (BriarRecyclerView) findViewById(R.id.list);
		LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
		list.setLayoutManager(linearLayoutManager);
		adapter = createAdapter(linearLayoutManager);
		list.setAdapter(adapter);

		if (state != null) {
			byte[] replyIdBytes = state.getByteArray(KEY_REPLY_ID);
			if (replyIdBytes != null) replyId = new MessageId(replyIdBytes);
		}

		loadItems();
	}

	@LayoutRes
	protected abstract int getLayout();

	protected abstract ThreadItemAdapter<I> createAdapter(
			LinearLayoutManager layoutManager);

	protected void loadNamedGroup() {
		getController().loadNamedGroup(
				new UiResultExceptionHandler<G, DbException>(this) {
					@Override
					public void onResultUi(G groupItem) {
						onNamedGroupLoaded(groupItem);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO Proper error handling
						finish();
					}
				});
	}

	@UiThread
	protected abstract void onNamedGroupLoaded(G groupItem);

	private void loadItems() {
		final int revision = adapter.getRevision();
		getController().loadItems(
				new UiResultExceptionHandler<Collection<I>, DbException>(this) {
					@Override
					public void onResultUi(Collection<I> items) {
						if (revision == adapter.getRevision()) {
							adapter.incrementRevision();
							if (items.isEmpty()) {
								list.showData();
							} else {
								adapter.setItems(items);
								list.showData();
								if (replyId != null)
									adapter.setReplyItemById(replyId);
							}
						} else {
							LOG.info("Concurrent update, reloading");
							loadItems();
						}
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO Proper error handling
						finish();
					}
				});
	}

	@CallSuper
	@Override
	public void onStart() {
		super.onStart();
		list.startPeriodicUpdate();
	}

	@CallSuper
	@Override
	public void onStop() {
		super.onStop();
		list.stopPeriodicUpdate();
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		boolean visible = savedInstanceState.getBoolean(KEY_INPUT_VISIBILITY);
		textInput.setVisibility(visible ? VISIBLE : GONE);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		boolean visible = textInput.getVisibility() == VISIBLE;
		outState.putBoolean(KEY_INPUT_VISIBILITY, visible);
		ThreadItem replyItem = adapter.getReplyItem();
		if (replyItem != null) {
			outState.putByteArray(KEY_REPLY_ID, replyItem.getId().getBytes());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				if (textInput.isKeyboardOpen()) textInput.hideSoftKeyboard();
				supportFinishAfterTransition();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		if (textInput.getVisibility() == VISIBLE) {
			textInput.setVisibility(GONE);
			adapter.setReplyItem(null);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void onItemVisible(I item) {
		if (!item.isRead()) {
			item.setRead(true);
			getController().markItemRead(item);
		}
	}

	@Override
	public void onReplyClick(I item) {
		showTextInput(item);
	}

	protected void displaySnackbarShort(@StringRes int stringId) {
		Snackbar snackbar = make(list, stringId, Snackbar.LENGTH_SHORT);
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
		snackbar.show();
	}

	protected void showTextInput(@Nullable I replyItem) {
		// An animation here would be an overkill because of the keyboard
		// popping up.
		// only clear the text when the input container was not visible
		if (textInput.getVisibility() != VISIBLE) {
			textInput.setVisibility(VISIBLE);
			textInput.setText("");
		}
		textInput.requestFocus();
		textInput.showSoftKeyboard();
		textInput.setHint(replyItem == null ? R.string.forum_new_message_hint :
				R.string.forum_message_reply_hint);
		adapter.setReplyItem(replyItem);
	}

	@Override
	public void onSendClick(String text) {
		if (text.trim().length() == 0)
			return;
		if (StringUtils.utf8IsTooLong(text, getMaxBodyLength())) {
			displaySnackbarShort(R.string.text_too_long);
			return;
		}
		I replyItem = adapter.getReplyItem();
		UiResultExceptionHandler<I, DbException> handler =
				new UiResultExceptionHandler<I, DbException>(this) {
					@Override
					public void onResultUi(I result) {
						addItem(result, true);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO add proper exception handling
						finish();
					}
				};
		getController().createAndStoreMessage(text, replyItem, handler);
		textInput.hideSoftKeyboard();
		textInput.setVisibility(GONE);
		textInput.setText("");
		adapter.setReplyItem(null);
	}

	protected abstract int getMaxBodyLength();

	@Override
	public void onHeaderReceived(H header) {
		getController().loadItem(header,
				new UiResultExceptionHandler<I, DbException>(this) {
					@Override
					public void onResultUi(final I result) {
						addItem(result, false);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO add proper exception handling
						finish();
					}
				});
	}

	@Override
	public void onGroupRemoved() {
		supportFinishAfterTransition();
	}

	protected void addItem(final I item, boolean isLocal) {
		adapter.incrementRevision();
		adapter.add(item);
		if (isLocal && adapter.isVisible(item)) {
			displaySnackbarShort(getItemPostedString());
		} else {
			Snackbar snackbar = Snackbar.make(list,
					isLocal ? getItemPostedString() : getItemReceivedString(),
					Snackbar.LENGTH_LONG);
			snackbar.getView().setBackgroundResource(R.color.briar_primary);
			snackbar.setActionTextColor(ContextCompat
					.getColor(ThreadListActivity.this,
							R.color.briar_button_positive));
			snackbar.setAction(R.string.show, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					adapter.scrollTo(item);
				}
			});
			snackbar.getView().setBackgroundResource(R.color.briar_primary);
			snackbar.show();
		}
	}

	@StringRes
	protected abstract int getItemPostedString();

	@StringRes
	protected abstract int getItemReceivedString();

}
