package org.briarproject.briar.android.keyagreement;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactExchangeListener;
import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R.string;
import org.briarproject.briar.android.activity.ActivityComponent;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactExchangeActivity extends KeyAgreementActivity implements
		ContactExchangeListener {

	private static final Logger LOG =
			Logger.getLogger(ContactExchangeActivity.class.getName());

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ContactExchangeTask contactExchangeTask;
	@Inject
	volatile IdentityManager identityManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		getSupportActionBar().setTitle(string.add_contact_title);
	}

	@Override
	void keyAgreementFinished(KeyAgreementResult result) {
		runOnUiThreadUnlessDestroyed(() -> startContactExchange(result));
	}

	private void startContactExchange(KeyAgreementResult result) {
		runOnDbThread(() -> {
			LocalAuthor localAuthor;
			// Load the local pseudonym
			try {
				localAuthor = identityManager.getLocalAuthor();
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				contactExchangeFailed();
				return;
			}

			// Exchange contact details
			contactExchangeTask.startExchange(ContactExchangeActivity.this,
					localAuthor, result.getMasterKey(),
					result.getConnection(), result.getTransportId(),
					result.wasAlice());
		});
	}

	@Override
	public void contactExchangeSucceeded(Author remoteAuthor) {
		runOnUiThreadUnlessDestroyed(() -> {
			String contactName = remoteAuthor.getName();
			String format = getString(string.contact_added_toast);
			String text = String.format(format, contactName);
			Toast.makeText(ContactExchangeActivity.this, text, LENGTH_LONG)
					.show();
			supportFinishAfterTransition();
		});
	}

	@Override
	public void duplicateContact(Author remoteAuthor) {
		runOnUiThreadUnlessDestroyed(() -> {
			String contactName = remoteAuthor.getName();
			String format = getString(string.contact_already_exists);
			String text = String.format(format, contactName);
			Toast.makeText(ContactExchangeActivity.this, text, LENGTH_LONG)
					.show();
			finish();
		});
	}

	@Override
	public void contactExchangeFailed() {
		runOnUiThreadUnlessDestroyed(() -> {
			Toast.makeText(ContactExchangeActivity.this,
					string.contact_exchange_failed, LENGTH_LONG).show();
			finish();
		});
	}
}
