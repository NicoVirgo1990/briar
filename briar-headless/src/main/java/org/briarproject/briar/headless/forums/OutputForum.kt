package org.briarproject.briar.headless.forums

import org.briarproject.briar.api.forum.Forum
import javax.annotation.concurrent.Immutable

@Immutable
internal data class OutputForum(
    val name: String,
    val id: ByteArray
) {
    constructor(forum: Forum) : this(
        name = forum.name,
        id = forum.id.bytes
    )
}

internal fun Forum.output() = OutputForum(this)

internal fun Collection<Forum>.output() = map { it.output() }
