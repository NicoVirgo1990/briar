package org.briarproject.api.messaging;

import org.briarproject.api.clients.BaseMessageHeader;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

public class PrivateMessageHeader extends BaseMessageHeader {

	public PrivateMessageHeader(MessageId id, GroupId groupId, long timestamp,
			boolean local, boolean read, boolean sent, boolean seen) {

		super(id, groupId, timestamp, local, read, sent, seen);
	}

}
